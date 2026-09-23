package demo.socialanalytics.service;

import demo.socialanalytics.config.CrawlProperties;
import demo.socialanalytics.entity.CrawlRun;
import demo.socialanalytics.entity.CrawlStatus;
import demo.socialanalytics.repository.CrawlRunRepository;
import demo.socialanalytics.dto.response.ChartDataResponse;
import demo.socialanalytics.messaging.DashboardBroadcaster;
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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SocialMetricsUpdateJobTest {
    @Mock PostRepository postRepository;
    @Mock CrawlRunRepository crawlRunRepository;
    @Mock SocialMetricsCollector collector;
    @Mock DashboardBroadcaster broadcaster;
    @Mock ChartDataService chartDataService;

    SocialMetricsUpdateJob job;

    private CrawlProperties properties(int timeoutSeconds) {
        return new CrawlProperties(true, 8, 100, timeoutSeconds, new CrawlProperties.Mock(0, 0));
    }

    private SocialMetricsUpdateJob newJob(int timeoutSeconds) {
        return new SocialMetricsUpdateJob(postRepository, crawlRunRepository, collector,
            properties(timeoutSeconds), broadcaster, chartDataService);
    }

    @BeforeEach
    void setUp() {
        job = newJob(30);
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
    void phatTinRealtimeSauKhiCrawlXong() {
        when(postRepository.findDistinctUserIds()).thenReturn(List.of(1L));
        accountReturns(1L, 2, 2, 0);
        when(chartDataService.chartData(null, null)).thenReturn(mock(ChartDataResponse.class));

        job.runOnce();

        verify(broadcaster).crawlFinished(any());
        verify(broadcaster).chartUpdated(any());
    }

    @Test
    void khongCoBaiNaoThanhCongThiKhongPhat() {
        when(postRepository.findDistinctUserIds()).thenReturn(List.of(1L));
        accountReturns(1L, 2, 0, 2);

        job.runOnce();

        verify(broadcaster).crawlFinished(any());
        verify(broadcaster, never()).chartUpdated(any());
        verifyNoInteractions(chartDataService);
    }

    @Test
    void loiPhatTinKhongLamHongCrawl() {
        when(postRepository.findDistinctUserIds()).thenReturn(List.of(1L));
        accountReturns(1L, 2, 2, 0);
        when(chartDataService.chartData(null, null))
            .thenThrow(new IllegalStateException("không tính được dữ liệu biểu đồ"));

        assertThatCode(() -> job.runOnce()).doesNotThrowAnyException();
    }

    @Test
    void khongCoBaiVietVanGhiLanChay() {
        when(postRepository.findDistinctUserIds()).thenReturn(List.of());

        CrawlRun run = job.runOnce();

        assertThat(run.getStatus()).isEqualTo(CrawlStatus.SUCCESS);
        assertThat(run.getTotalAccounts()).isZero();
        assertThat(run.getMessage()).contains("Không có bài viết nào");
        verifyNoInteractions(collector);
    }

    @Test
    void ghiTrangThaiRunningTruocRoiCapNhatSau() {
        when(postRepository.findDistinctUserIds()).thenReturn(List.of(1L));
        accountReturns(1L, 1, 1, 0);

        job.runOnce();

        verify(crawlRunRepository, times(2)).save(any(CrawlRun.class));
    }

    @Test
    void giaoHetViecRoiMoiCho() {
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

    @Test
    void khongChayChongLenNhau() throws Exception {
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

    @Test
    void quaThoiGianThiBoDo() {
        job = newJob(1);
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
    void chayDuocSauKhiQuaHan() {
        job = newJob(1);
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
