package demo.socialanalytics.service;

import demo.socialanalytics.client.SocialApiClient;
import demo.socialanalytics.client.SocialMetricsSnapshot;
import demo.socialanalytics.config.AsyncConfig;
import demo.socialanalytics.entity.Post;
import demo.socialanalytics.repository.PostRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

// Rate limit theo token: song song theo tài khoản, tuần tự trong một tài khoản
@Service
public class SocialMetricsCollector {

    private static final Logger log = LoggerFactory.getLogger(SocialMetricsCollector.class);

    private final PostRepository postRepository;
    private final SocialApiClient socialApiClient;
    private final MetricWriter metricWriter;

    public SocialMetricsCollector(
        PostRepository postRepository,
        SocialApiClient socialApiClient,
        MetricWriter metricWriter
    ) {
        this.postRepository = postRepository;
        this.socialApiClient = socialApiClient;
        this.metricWriter = metricWriter;
    }

    // Trả CompletableFuture chứ không phải void, vì job cần BIẾT khi nào xong và kết quả ra sao.
    @Async(AsyncConfig.CRAWL_EXECUTOR)
    public CompletableFuture<AccountCrawlResult> collectForAccount(Long userId) {
        List<Post> posts = loadPosts(userId);
        if (posts.isEmpty()) {
            return CompletableFuture.completedFuture(AccountCrawlResult.empty(userId));
        }

        log.debug("Bắt đầu crawl tài khoản {} với {} bài viết", userId, posts.size());
        int succeeded = 0;
        int failed = 0;

        for (Post post : posts) {
            if (Thread.currentThread().isInterrupted()) {
                // Ứng dụng đang tắt hoặc lần chạy đã quá hạn: dừng sớm, phần đã ghi vẫn giữ.
                log.warn("Dừng sớm khi crawl tài khoản {}: luồng bị ngắt", userId);
                break;
            }
            if (crawlOnePost(post)) {
                succeeded++;
            } else {
                failed++;
            }
        }

        log.debug("Xong tài khoản {}: {} thành công, {} lỗi", userId, succeeded, failed);
        return CompletableFuture.completedFuture(
            new AccountCrawlResult(userId, posts.size(), succeeded, failed));
    }

    // Lỗi của MỘT bài chỉ dừng lại ở bài đó.
    private boolean crawlOnePost(Post post) {
        try {
            SocialMetricsSnapshot snapshot = socialApiClient.fetchMetrics(post);
            metricWriter.record(post.getId(), snapshot, LocalDateTime.now());
            return true;
        } catch (Exception exception) {
            // Ghi cả stack trace: đây là luồng nền, không có ai nhìn thấy lỗi ngoài log.
            log.warn("Không crawl được bài {} ({}): {}",
                post.getId(), post.getExternalId(), exception.getMessage(), exception);
            return false;
        }
    }

    // @Transactional chỉ có tác dụng khi gọi từ bên ngoài qua proxy
    private List<Post> loadPosts(Long userId) {
        return postRepository.findByUserIdOrderByIdAsc(userId);
    }
}
