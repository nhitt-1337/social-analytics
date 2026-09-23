package demo.socialanalytics.client;

import demo.socialanalytics.config.CrawlProperties;
import demo.socialanalytics.entity.Post;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;

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

        long potential = Math.abs(post.getExternalId().hashCode() % 260) + 40L;

        LocalDateTime since = post.getPostedAt() != null ? post.getPostedAt() : post.getCreatedAt();
        long hours = since == null ? 0 : Math.max(0, Duration.between(since, LocalDateTime.now()).toHours());

        // Đường cong bão hoà: tương tác dồn vào 1-2 ngày đầu rồi chững lại
        double maturity = 1 - Math.exp(-hours / 36.0);

        int likes = (int) Math.round(potential * Math.max(0.05, maturity));

        int shares = (int) Math.round(likes * 0.06);
        int comments = (int) Math.round(likes * 0.04);

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
            // Khôi phục cờ ngắt, nếu không job sẽ không dừng được khi ứng dụng tắt
            Thread.currentThread().interrupt();
            throw new SocialApiException("Bị ngắt khi đang gọi API", exception);
        }
    }

    private boolean shouldFail() {
        double rate = properties.mock().failureRate();
        return rate > 0 && ThreadLocalRandom.current().nextDouble() < rate;
    }
}
