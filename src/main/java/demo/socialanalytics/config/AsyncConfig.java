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

// Mặc định @Scheduled chạy một luồng, @Async tạo luồng không giới hạn
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

    // Bể luồng để crawl.
    @Bean(name = CRAWL_EXECUTOR)
    public ThreadPoolTaskExecutor socialCrawlExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.poolSize());
        executor.setMaxPoolSize(properties.poolSize());
        executor.setQueueCapacity(properties.queueCapacity());

        // Tên luồng có tiền tố để đọc log biết ngay việc chạy ở đâu.
        executor.setThreadNamePrefix("crawl-");

        // Hàng đợi đầy thì chạy ngay trên luồng gọi: job chậm lại nhưng không mất bài nào.
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

    // Ngoại lệ từ @Async trả void không quay lại luồng gọi
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
