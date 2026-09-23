package demo.socialanalytics.config;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.context.ActiveProfiles;

import java.lang.reflect.Method;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

// Cấu hình bể luồng: để mặc định thì @Scheduled chạy trên MỘT luồng và @Async tạo luồng mới
@SpringBootTest(properties = {
    "social.crawl.pool-size=4",
    "social.crawl.queue-capacity=10"
})
@ActiveProfiles("test")
class AsyncConfigTest {

    @Autowired
    @Qualifier(AsyncConfig.CRAWL_EXECUTOR)
    ThreadPoolTaskExecutor crawlExecutor;

    @Autowired ThreadPoolTaskScheduler taskScheduler;

    @Test
    void beLuongCrawlDungCauHinhTuProperties() {
        assertThat(crawlExecutor.getCorePoolSize()).isEqualTo(4);
        assertThat(crawlExecutor.getMaxPoolSize()).isEqualTo(4);
        assertThat(crawlExecutor.getThreadNamePrefix()).isEqualTo("crawl-");
    }

    // Bể của @Scheduled phải có nhiều hơn một luồng, nếu không một job chậm sẽ chặn mọi job khác.
    @Test
    void beLuongLichChayCoNhieuHonMotLuong() {
        // getPoolSize() trả về số luồng ĐANG tồn tại (0 khi chưa có việc nào), không phải cấu hình ->
        assertThat(taskScheduler.getScheduledThreadPoolExecutor().getCorePoolSize()).isGreaterThan(1);
        assertThat(taskScheduler.getThreadNamePrefix()).isEqualTo("scheduler-");
    }

    @Test
    void chayThatSuSongSongChuKhongTuanTu() throws Exception {
        int soTacVu = 4;
        CountDownLatch tatCaDaVao = new CountDownLatch(soTacVu);
        CountDownLatch xongHet = new CountDownLatch(soTacVu);

        for (int i = 0; i < soTacVu; i++) {
            crawlExecutor.execute(() -> {
                tatCaDaVao.countDown();
                try {
                    // Chỉ thoát ra khi cả 4 cùng vào được: chạy tuần tự thì treo ở đây.
                    tatCaDaVao.await(3, TimeUnit.SECONDS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
                xongHet.countDown();
            });
        }

        assertThat(xongHet.await(5, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void tacVuChayTrenLuongCoTenBatDauBangCrawl() throws Exception {
        CompletableFuture<String> threadName = new CompletableFuture<>();

        crawlExecutor.execute(() -> threadName.complete(Thread.currentThread().getName()));

        assertThat(threadName.get(3, TimeUnit.SECONDS)).startsWith("crawl-");
    }

    // Hàng đợi đầy thì tác vụ chạy ngay trên luồng gọi (CallerRunsPolicy) chứ không bị vứt bỏ
    @Test
    void hangDoiDayThiChayTrenLuongGoiChuKhongVutBo() {
        assertThat(crawlExecutor.getThreadPoolExecutor().getRejectedExecutionHandler())
            .isInstanceOf(ThreadPoolExecutor.CallerRunsPolicy.class);
    }

    // Ngoại lệ từ method @Async trả về void không quay lại được luồng gọi; không có handler thì nó
    @Test
    void coHandlerChoNgoaiLeKhongDuocXuLyTrongLuongNen() throws Exception {
        AsyncConfig config = new AsyncConfig(new CrawlProperties(
            true, 2, 10, 60, new CrawlProperties.Mock(0, 0)));

        var handler = config.getAsyncUncaughtExceptionHandler();

        assertThat(handler).isInstanceOf(AsyncConfig.LoggingAsyncExceptionHandler.class);
        // Gọi thử: handler phải nuốt được lỗi và không tự ném ra tiếp.
        Method method = String.class.getMethod("length");
        handler.handleUncaughtException(new IllegalStateException("thử"), method, "tham-so");
    }
}
