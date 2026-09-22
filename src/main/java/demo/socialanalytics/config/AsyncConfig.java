package demo.socialanalytics.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

// Bật @Async và @Scheduled, và khai báo rõ hai bể luồng riêng cho chúng.
//
// Không khai báo thì Spring dùng bể mặc định: @Scheduled chạy trên MỘT luồng duy nhất
// (một job chậm sẽ chặn mọi job khác), còn @Async dùng SimpleAsyncTaskExecutor —
// tạo luồng mới cho TỪNG tác vụ, không hề giới hạn. Cả hai đều không dùng được ở production.
@Configuration
@EnableAsync
@EnableScheduling
@EnableConfigurationProperties(CrawlProperties.class)
public class AsyncConfig implements AsyncConfigurer {

    public static final String CRAWL_EXECUTOR = "socialCrawlExecutor";

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    private final CrawlProperties properties;

    public AsyncConfig(CrawlProperties properties) {
        this.properties = properties;
    }

    // Bể luồng để crawl. Mỗi tác vụ là một TÀI KHOẢN, phần lớn thời gian là ngồi chờ mạng
    // chứ không tính toán, nên số luồng đặt cao hơn số nhân CPU vẫn có lợi.
    @Bean(name = CRAWL_EXECUTOR)
    public ThreadPoolTaskExecutor socialCrawlExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.poolSize());
        executor.setMaxPoolSize(properties.poolSize());
        executor.setQueueCapacity(properties.queueCapacity());

        // Tên luồng có tiền tố để đọc log biết ngay việc chạy ở đâu.
        executor.setThreadNamePrefix("crawl-");

        // Hàng đợi đầy thì chạy ngay trên luồng gọi: job chậm lại nhưng không mất bài nào.
        // Mặc định của JDK là AbortPolicy — ném lỗi và bỏ luôn tác vụ đó.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        // Tắt ứng dụng thì chờ việc đang chạy xong rồi mới dừng, tránh ghi dở dang vào DB.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);

        executor.initialize();
        return executor;
    }

    // Bể riêng cho @Scheduled. Để mặc định thì mọi job dùng chung một luồng.
    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("scheduler-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        return scheduler;
    }

    @Override
    public Executor getAsyncExecutor() {
        return socialCrawlExecutor();
    }

    // Lưới an toàn cho ngoại lệ trong luồng nền.
    //
    // Đây là điểm mấu chốt của xử lý lỗi đa luồng: ngoại lệ ném ra từ một phương thức @Async
    // trả về void KHÔNG quay lại được luồng gọi — không có handler thì nó biến mất không dấu vết.
    // Job đã bắt lỗi theo từng tài khoản rồi; chỗ này bắt những gì lọt qua.
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new LoggingAsyncExceptionHandler();
    }

    static class LoggingAsyncExceptionHandler implements AsyncUncaughtExceptionHandler {
        @Override
        public void handleUncaughtException(Throwable error, Method method, Object... params) {
            log.error("Ngoại lệ không được xử lý trong tác vụ nền {}.{} với tham số {}",
                method.getDeclaringClass().getSimpleName(), method.getName(),
                Arrays.toString(params), error);
        }
    }
}
