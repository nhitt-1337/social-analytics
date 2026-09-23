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

        // "Sức hút" riêng của từng bài — tổng số like bài đó sẽ đạt được khi đã nguội.
        // Cố định theo externalId nên mỗi lần crawl vẫn ra cùng một con số.
        long potential = Math.abs(post.getExternalId().hashCode() % 260) + 40L;

        LocalDateTime since = post.getPostedAt() != null ? post.getPostedAt() : post.getCreatedAt();
        long hours = since == null ? 0 : Math.max(0, Duration.between(since, LocalDateTime.now()).toHours());

        // Đường cong BÃO HOÀ, không phải tăng tuyến tính.
        //
        // Tương tác thật dồn vào một hai ngày đầu rồi gần như đứng yên. Công thức cũ cộng thêm
        // theo số giờ nên bài càng cũ số càng phình vô hạn — bài 10 ngày tuổi tự có thêm 720 like
        // mà chẳng ai tương tác.
        // Hệ số 36 giờ: sau ~1,5 ngày đạt quá nửa, sau ~4 ngày gần chạm trần.
        double maturity = 1 - Math.exp(-hours / 36.0);

        // Sàn 5% để bài vừa đăng không hiện 0 tuyệt đối.
        int likes = (int) Math.round(potential * Math.max(0.05, maturity));

        // Tỷ lệ theo thực tế mạng xã hội: share vài phần trăm số like, bình luận thấp hơn nữa.
        // Công thức cũ cho share bằng ~31% số like, nhìn là biết không thật.
        int shares = (int) Math.round(likes * 0.06);
        int comments = (int) Math.round(likes * 0.04);

        // Người theo dõi là chỉ số của TÀI KHOẢN, không phải của bài viết, nên gần như không
        // đổi theo tuổi bài. Cho nhích rất nhẹ để biểu đồ không phải một đường thẳng tắp.
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
