package demo.socialanalytics.client;

import demo.socialanalytics.config.CrawlProperties;
import demo.socialanalytics.entity.Post;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;

// Dữ liệu giả thay cho API Facebook / X.
//
// Số liệu sinh ra theo id bài viết (cố định) cộng phần tăng theo thời gian, nên mỗi lần crawl
// lại nhận được con số nhích lên — biểu đồ tăng trưởng ở bước sau mới có hình dạng hợp lý,
// khác với việc trả số ngẫu nhiên nhảy lung tung.
//
// Có cả độ trễ và tỉ lệ lỗi để thấy được tác dụng của chạy song song và để phần xử lý lỗi
// trong luồng thực sự được chạy tới.
@Component
public class MockSocialApiClient implements SocialApiClient {

    private final CrawlProperties properties;

    public MockSocialApiClient(CrawlProperties properties) {
        this.properties = properties;
    }

    @Override
    public SocialMetricsSnapshot fetchMetrics(Post post) {
        sleepLikeNetworkCall();

        if (shouldFail()) {
            throw new SocialApiException(
                "Nhà cung cấp trả lỗi tạm thời cho bài " + post.getExternalId());
        }

        // Mốc riêng của từng bài, cố định qua các lần chạy.
        long seed = Math.abs(post.getExternalId().hashCode() % 500) + 50L;

        LocalDateTime since = post.getPostedAt() != null ? post.getPostedAt() : post.getCreatedAt();
        long hours = since == null ? 0 : Math.max(0, Duration.between(since, LocalDateTime.now()).toHours());

        int likes = (int) (seed * 2 + hours * 3);
        int shares = (int) (seed / 2 + hours);
        int comments = (int) (seed / 4 + hours / 2);
        int followers = (int) (seed * 20 + hours * 5);

        return new SocialMetricsSnapshot(likes, shares, comments, followers);
    }

    private void sleepLikeNetworkCall() {
        long latency = properties.mock().latencyMs();
        if (latency <= 0) {
            return;
        }
        try {
            Thread.sleep(latency);
        } catch (InterruptedException exception) {
            // Khôi phục cờ ngắt rồi báo lỗi lên: nuốt InterruptedException là cách chắc chắn
            // khiến job không bao giờ dừng được khi ứng dụng đang tắt.
            Thread.currentThread().interrupt();
            throw new SocialApiException("Bị ngắt khi đang gọi API", exception);
        }
    }

    private boolean shouldFail() {
        double rate = properties.mock().failureRate();
        return rate > 0 && ThreadLocalRandom.current().nextDouble() < rate;
    }
}
