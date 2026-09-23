package demo.socialanalytics.client;

import demo.socialanalytics.config.CrawlProperties;
import demo.socialanalytics.entity.Post;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;

// Dữ liệu giả thay cho API Facebook / X.
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

        // "Sức hút" riêng của từng bài — tổng số like bài đó sẽ đạt được khi đã nguội.
        long potential = Math.abs(post.getExternalId().hashCode() % 260) + 40L;

        LocalDateTime since = post.getPostedAt() != null ? post.getPostedAt() : post.getCreatedAt();
        long hours = since == null ? 0 : Math.max(0, Duration.between(since, LocalDateTime.now()).toHours());

        // Đường cong bão hoà: tương tác dồn vào 1-2 ngày đầu rồi chững lại
        double maturity = 1 - Math.exp(-hours / 36.0);

        // Sàn 5% để bài vừa đăng không hiện 0 tuyệt đối.
        int likes = (int) Math.round(potential * Math.max(0.05, maturity));

        // Tỷ lệ theo thực tế mạng xã hội: share vài phần trăm số like, bình luận thấp hơn nữa.
        int shares = (int) Math.round(likes * 0.06);
        int comments = (int) Math.round(likes * 0.04);

        // Người theo dõi là chỉ số của TÀI KHOẢN
        int followers = (int) (potential * 12 + hours / 24);

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
            // Khôi phục cờ ngắt rồi báo lỗi lên: nuốt InterruptedException là cách chắc chắn khiến job
            Thread.currentThread().interrupt();
            throw new SocialApiException("Bị ngắt khi đang gọi API", exception);
        }
    }

    private boolean shouldFail() {
        double rate = properties.mock().failureRate();
        return rate > 0 && ThreadLocalRandom.current().nextDouble() < rate;
    }
}
