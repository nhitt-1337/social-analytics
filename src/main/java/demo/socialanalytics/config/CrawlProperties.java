package demo.socialanalytics.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// Cấu hình cho job cập nhật chỉ số, gom vào một chỗ thay vì rải @Value khắp nơi.
// Tiền tố: social.crawl.* trong application.yaml
@ConfigurationProperties(prefix = "social.crawl")
public record CrawlProperties(

    // Tắt job khi chạy test hoặc khi chạy nhiều instance mà chưa có cơ chế khoá.
    @DefaultValue("true") boolean enabled,

    @DefaultValue("8") int poolSize,

    // Hàng đợi đầy thì tác vụ mới chạy ngay trên luồng gọi (CallerRunsPolicy) — chậm lại
    // chứ không mất việc.
    @DefaultValue("100") int queueCapacity,

    // Chờ tối đa bấy nhiêu giây cho một lần chạy. Quá thì bỏ dở để lần sau còn chạy được,
    // không để job treo vô hạn.
    @DefaultValue("300") int timeoutSeconds,

    @DefaultValue Mock mock
) {

    // Tham số cho client giả lập. Khi nối API thật thì bỏ phần này đi.
    public record Mock(
        // Giả lập độ trễ mạng để thấy rõ tác dụng của chạy song song.
        @DefaultValue("40") long latencyMs,

        // Tỉ lệ gọi hỏng, để kiểm tra phần xử lý lỗi trong luồng. 0 = không bao giờ hỏng.
        @DefaultValue("0.1") double failureRate
    ) {
    }
}
