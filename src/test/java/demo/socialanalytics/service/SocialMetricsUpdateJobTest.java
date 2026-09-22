package demo.socialanalytics.service;

import demo.socialanalytics.config.CrawlProperties;
import demo.socialanalytics.entity.CrawlRun;
import demo.socialanalytics.entity.CrawlStatus;
import demo.socialanalytics.repository.CrawlRunRepository;
import demo.socialanalytics.repository.PostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

// Phần điều phối: chia việc, chờ, tổng hợp, ghi lịch sử.
@ExtendWith(MockitoExtension.class)
class SocialMetricsUpdateJobTest {

    @Mock PostRepository postRepository;
    @Mock CrawlRunRepository crawlRunRepository;
    @Mock SocialMetricsCollector collector;

    SocialMetricsUpdateJob job;

    private CrawlProperties properties(int timeoutSeconds) {
        return new CrawlProperties(true, 8, 100, timeoutSeconds, new CrawlProperties.Mock(0, 0));
    }

    @BeforeEach
    void setUp() {
        job = new SocialMetricsUpdateJob(postRepository, crawlRunRepository, collector, properties(30));
        // save() trả lại chính entity được truyền vào, như JPA thật.
        lenient().when(crawlRunRepository.save(any(CrawlRun.class))).thenAnswer(invocation -> {
            CrawlRun run = invocation.getArgument(0);
            if (run.getId() == null) {
                ReflectionTestUtils.setField(run, "id", 1L);
            }
            return run;
        });
    }

    private void accountReturns(long userId, int total, int succeeded, int failed) {
        when(collector.collectForAccount(userId)).thenReturn(CompletableFuture.completedFuture(
            new AccountCrawlResult(userId, total, succeeded, failed)));
    }

    @Test
    void chiaViecTheoTaiKhoanVaTongHopKetQua() {
        when(postRepository.findDistinctUserIds()).thenReturn(List.of(1L, 2L));
        accountReturns(1L, 3, 3, 0);
        accountReturns(2L, 2, 2, 0);

        CrawlRun run = job.runOnce();

        assertThat(run.getStatus()).isEqualTo(CrawlStatus.SUCCESS);
        assertThat(run.getTotalAccounts()).isEqualTo(2);
        assertThat(run.getTotalPosts()).isEqualTo(5);
        assertThat(run.getSucceededPosts()).isEqualTo(5);
        assertThat(run.getFailedPosts()).isZero();
        assertThat(run.getFinishedAt()).isNotNull();
        verify(collector).collectForAccount(1L);
        verify(collector).collectForAccount(2L);
    }

    // Vài bài lỗi là chuyện thường của việc gọi API ngoài -> PARTIAL, không phải FAILED.
    @Test
    void motSoBaiLoiThiTrangThaiLaPartial() {
        when(postRepository.findDistinctUserIds()).thenReturn(List.of(1L));
        accountReturns(1L, 5, 4, 1);

        CrawlRun run = job.runOnce();

        assertThat(run.getStatus()).isEqualTo(CrawlStatus.PARTIAL);
        assertThat(run.getFailedPosts()).isEqualTo(1);
    }

    @Test
    void hongToanBoThiTrangThaiLaFailed() {
        when(postRepository.findDistinctUserIds()).thenReturn(List.of(1L));
        accountReturns(1L, 3, 0, 3);

        assertThat(job.runOnce().getStatus()).isEqualTo(CrawlStatus.FAILED);
    }

    @Test
    void khongCoBaiVietNaoThiVanGhiMotLanChayThanhCong() {
        when(postRepository.findDistinctUserIds()).thenReturn(List.of());

        CrawlRun run = job.runOnce();

        assertThat(run.getStatus()).isEqualTo(CrawlStatus.SUCCESS);
        assertThat(run.getTotalAccounts()).isZero();
        assertThat(run.getMessage()).contains("Không có bài viết nào");
        verifyNoInteractions(collector);
    }

    // Ghi một dòng RUNNING trước, cập nhật lại khi xong: dashboard nhìn thấy job đang chạy
    // chứ không phải chờ tới lúc kết thúc mới có gì để hiện.
    @Test
    void ghiTrangThaiRunningTruocRoiCapNhatSau() {
        when(postRepository.findDistinctUserIds()).thenReturn(List.of(1L));
        accountReturns(1L, 1, 1, 0);

        job.runOnce();

        // save() gọi hai lần trên cùng một bản ghi: lúc bắt đầu và lúc kết thúc.
        verify(crawlRunRepository, times(2)).save(any(CrawlRun.class));
    }

    // Đây là điểm mấu chốt của đa luồng: phải GIAO HẾT việc rồi mới chờ.
    // Gọi .get() ngay trong vòng lặp thì hoá ra chạy tuần tự.
    @Test
    void giaoHetViecRoiMoiChoChuKhongChoTungCai() {
        when(postRepository.findDistinctUserIds()).thenReturn(List.of(1L, 2L, 3L, 4L));

        AtomicInteger dangChay = new AtomicInteger();
        AtomicInteger dinhCaoDongThoi = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch tatCaDaVao = new CountDownLatch(4);

        when(collector.collectForAccount(anyLong())).thenAnswer(invocation -> {
            long userId = invocation.getArgument(0);
            return CompletableFuture.supplyAsync(() -> {
                int hienTai = dangChay.incrementAndGet();
                dinhCaoDongThoi.accumulateAndGet(hienTai, Math::max);
                tatCaDaVao.countDown();
                try {
                    // Không thoát ra cho tới khi cả 4 tác vụ cùng vào được đây.
                    // Nếu job chờ từng cái một thì đây là chỗ nó treo cứng.
                    tatCaDaVao.await(2, TimeUnit.SECONDS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
                dangChay.decrementAndGet();
                return new AccountCrawlResult(userId, 1, 1, 0);
            }, pool);
        });

        CrawlRun run = job.runOnce();
        pool.shutdown();

        assertThat(dinhCaoDongThoi.get()).isEqualTo(4);
        assertThat(run.getSucceededPosts()).isEqualTo(4);
    }

    // Lần chạy thứ hai bị bỏ qua khi lần đầu chưa xong — nút "chạy ngay" có thể bấm trúng lúc
    // job định kỳ đang chạy.
    @Test
    void khongChoHaiLanChayChongLenNhau() throws Exception {
        when(postRepository.findDistinctUserIds()).thenReturn(List.of(1L));
        CountDownLatch dangChay = new CountDownLatch(1);
        CountDownLatch choThaRa = new CountDownLatch(1);

        when(collector.collectForAccount(1L)).thenAnswer(invocation ->
            CompletableFuture.supplyAsync(() -> {
                dangChay.countDown();
                try {
                    choThaRa.await(2, TimeUnit.SECONDS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
                return new AccountCrawlResult(1L, 1, 1, 0);
            }));

        Thread lanDau = new Thread(job::runOnce);
        lanDau.start();
        assertThat(dangChay.await(2, TimeUnit.SECONDS)).isTrue();

        CrawlRun bikBoQua = job.runOnce();

        choThaRa.countDown();
        lanDau.join(3_000);
        assertThat(bikBoQua).isNull();
    }

    // Quá hạn thì bỏ dở phần còn lại và vẫn ghi lại lần chạy, thay vì treo mãi.
    @Test
    void quaThoiGianChoPhepThiBoDoVaVanGhiLai() {
        job = new SocialMetricsUpdateJob(postRepository, crawlRunRepository, collector, properties(1));
        when(crawlRunRepository.save(any(CrawlRun.class))).thenAnswer(i -> i.getArgument(0));
        when(postRepository.findDistinctUserIds()).thenReturn(List.of(1L));
        when(collector.collectForAccount(1L)).thenReturn(new CompletableFuture<>()); // không bao giờ xong

        CrawlRun run = job.runOnce();

        assertThat(run.getStatus()).isEqualTo(CrawlStatus.FAILED);
        assertThat(run.getMessage()).contains("Quá thời gian");
        assertThat(run.getFinishedAt()).isNotNull();
    }

    // Sau một lần quá hạn, job vẫn phải chạy được lần sau (cờ running được thả trong finally).
    @Test
    void vanChayDuocSauKhiLanTruocQuaHan() {
        job = new SocialMetricsUpdateJob(postRepository, crawlRunRepository, collector, properties(1));
        when(crawlRunRepository.save(any(CrawlRun.class))).thenAnswer(i -> i.getArgument(0));
        when(postRepository.findDistinctUserIds()).thenReturn(List.of(1L));
        when(collector.collectForAccount(1L))
            .thenReturn(new CompletableFuture<>())
            .thenReturn(CompletableFuture.completedFuture(new AccountCrawlResult(1L, 2, 2, 0)));

        job.runOnce();
        CrawlRun lanSau = job.runOnce();

        assertThat(lanSau).isNotNull();
        assertThat(lanSau.getStatus()).isEqualTo(CrawlStatus.SUCCESS);
    }
}
