package demo.socialanalytics.service;

import demo.socialanalytics.config.CrawlProperties;
import demo.socialanalytics.dto.response.CrawlRunResponse;
import demo.socialanalytics.messaging.DashboardBroadcaster;
import demo.socialanalytics.entity.CrawlRun;
import demo.socialanalytics.entity.CrawlStatus;
import demo.socialanalytics.repository.CrawlRunRepository;
import demo.socialanalytics.repository.PostRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

// Job cập nhật chỉ số mạng xã hội, chạy mỗi 1 giờ.
@Component
@ConditionalOnProperty(name = "social.crawl.enabled", havingValue = "true", matchIfMissing = true)
public class SocialMetricsUpdateJob {

    private static final Logger log = LoggerFactory.getLogger(SocialMetricsUpdateJob.class);

    private final PostRepository postRepository;
    private final CrawlRunRepository crawlRunRepository;
    private final SocialMetricsCollector collector;
    private final CrawlProperties properties;
    private final DashboardBroadcaster broadcaster;
    private final ChartDataService chartDataService;

    // Chặn hai lần chạy chồng lên nhau. fixedDelay đã lo cho lịch tự động, nhưng nút "chạy ngay"
    private final AtomicBoolean running = new AtomicBoolean(false);

    public SocialMetricsUpdateJob(
        PostRepository postRepository,
        CrawlRunRepository crawlRunRepository,
        SocialMetricsCollector collector,
        CrawlProperties properties,
        DashboardBroadcaster broadcaster,
        ChartDataService chartDataService
    ) {
        this.postRepository = postRepository;
        this.crawlRunRepository = crawlRunRepository;
        this.collector = collector;
        this.properties = properties;
        this.broadcaster = broadcaster;
        this.chartDataService = chartDataService;
    }

    // fixedDelay chứ không phải fixedRate: đếm 1 giờ từ lúc lần trước KẾT THÚC.
    @Scheduled(
        fixedDelayString = "${social.crawl.interval:PT1H}",
        initialDelayString = "${social.crawl.initial-delay:PT1M}")
    public void updateSocialMetricsJob() {
        runOnce();
    }

    // Tách khỏi method @Scheduled để dashboard gọi được "chạy ngay" mà không phải đợi tới giờ.
    public CrawlRun runOnce() {
        if (!running.compareAndSet(false, true)) {
            log.info("Bỏ qua: đang có một lần crawl khác chạy dở");
            return null;
        }
        try {
            return execute();
        } finally {
            // finally: sót chỗ này là job sẽ không bao giờ chạy lại sau lần đầu tiên gặp lỗi.
            running.set(false);
        }
    }

    private CrawlRun execute() {
        long startedNanos = System.nanoTime();
        List<Long> userIds = postRepository.findDistinctUserIds();
        CrawlRun run = startRun(userIds.size());

        log.info("Bắt đầu crawl: {} tài khoản, bể {} luồng", userIds.size(), properties.poolSize());

        if (userIds.isEmpty()) {
            // Không có gì để làm KHÔNG phải là thất bại -> truyền vào ô ghi chú, không phải ô sự cố.
            return finish(run, List.of(), startedNanos, null, "Không có bài viết nào để cập nhật");
        }

        // Giao hết việc cho bể luồng rồi mới chờ: gọi .get() ngay trong vòng lặp thì hoá ra chạy tuần tự
        List<CompletableFuture<AccountCrawlResult>> futures = userIds.stream()
            .map(collector::collectForAccount)
            .toList();

        try {
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                .get(properties.timeoutSeconds(), TimeUnit.SECONDS);
        } catch (TimeoutException exception) {
            log.error("Lần crawl này quá {} giây, bỏ dở phần còn lại", properties.timeoutSeconds());
            futures.forEach(future -> future.cancel(true));
            return finish(run, collect(futures), startedNanos,
                "Quá thời gian cho phép (" + properties.timeoutSeconds() + "s)", null);
        } catch (InterruptedException exception) {
            // Khôi phục cờ ngắt rồi thoát: nuốt InterruptedException khiến ứng dụng không tắt được.
            Thread.currentThread().interrupt();
            futures.forEach(future -> future.cancel(true));
            return finish(run, collect(futures), startedNanos, "Bị ngắt khi đang chạy", null);
        } catch (ExecutionException exception) {
            log.error("Có tác vụ nền thất bại bất thường", exception.getCause());
            return finish(run, collect(futures), startedNanos,
                "Lỗi tác vụ nền: " + exception.getCause().getMessage(), null);
        }

        return finish(run, collect(futures), startedNanos, null, null);
    }

    // Gom kết quả, bỏ qua future nào chưa xong hoặc đã hỏng — phần đó đã được ghi log ở chỗ khác.
    private List<AccountCrawlResult> collect(List<CompletableFuture<AccountCrawlResult>> futures) {
        return futures.stream()
            .filter(future -> future.isDone() && !future.isCompletedExceptionally() && !future.isCancelled())
            .map(CompletableFuture::join)
            .toList();
    }

    // Không @Transactional: gọi từ trong cùng class nên không đi qua proxy
    private CrawlRun startRun(int totalAccounts) {
        CrawlRun run = new CrawlRun();
        run.setStartedAt(LocalDateTime.now());
        run.setStatus(CrawlStatus.RUNNING);
        run.setTotalAccounts(totalAccounts);
        return crawlRunRepository.save(run);
    }

    // problem: sự cố, có ảnh hưởng tới trạng thái.
    private CrawlRun finish(
        CrawlRun run, List<AccountCrawlResult> results, long startedNanos,
        String problem, String note) {

        int totalPosts = results.stream().mapToInt(AccountCrawlResult::totalPosts).sum();
        int succeeded = results.stream().mapToInt(AccountCrawlResult::succeeded).sum();
        int failed = results.stream().mapToInt(AccountCrawlResult::failed).sum();

        run.setFinishedAt(LocalDateTime.now());
        run.setTotalPosts(totalPosts);
        run.setSucceededPosts(succeeded);
        run.setFailedPosts(failed);
        run.setStatus(statusOf(problem, succeeded, failed));
        run.setMessage(trim(problem != null ? problem : note));

        CrawlRun saved = crawlRunRepository.save(run);
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startedNanos);
        log.info("Crawl xong sau {} ms: {}/{} bài thành công, {} lỗi, trạng thái {}",
            elapsed.toMillis(), succeeded, totalPosts, failed, saved.getStatus());

        notifyDashboards(saved, succeeded);
        return saved;
    }

    // Đẩy dữ liệu mới xuống các dashboard đang mở.
    private void notifyDashboards(CrawlRun run, int succeeded) {
        try {
            broadcaster.crawlFinished(CrawlRunResponse.of(run));
            // Không bài nào cập nhật được thì biểu đồ chẳng có gì mới để vẽ.
            if (succeeded > 0) {
                broadcaster.chartUpdated(chartDataService.chartData(null, null));
            }
        } catch (Exception exception) {
            log.warn("Không gửi được cập nhật realtime sau lần crawl {}: {}",
                run.getId(), exception.getMessage());
        }
    }

    private CrawlStatus statusOf(String problem, int succeeded, int failed) {
        if (problem != null && succeeded == 0) {
            return CrawlStatus.FAILED;
        }
        if (problem != null || failed > 0) {
            // Vài bài lỗi là chuyện thường của việc gọi API ngoài; không gọi cả lần chạy là hỏng.
            return succeeded == 0 && failed > 0 ? CrawlStatus.FAILED : CrawlStatus.PARTIAL;
        }
        return CrawlStatus.SUCCESS;
    }

    private String trim(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }
}
