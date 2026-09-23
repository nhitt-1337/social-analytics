package demo.socialanalytics.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// Tham số thử lại message. Tiền tố: social.messaging.*
@ConfigurationProperties(prefix = "social.messaging")
public record MessagingProperties(

    // Số lần GIAO LẠI trước khi broker đẩy message sang DLQ.
    @DefaultValue("3") int maxRedeliveries,

    // Giãn cách trước lần giao lại đầu tiên, tính bằng mili giây.
    @DefaultValue("500") long initialRedeliveryDelayMs,

    // Mỗi lần thử lại thì giãn cách nhân lên bấy nhiêu: 0,5s -> 1s -> 2s.
    @DefaultValue("2") double backOffMultiplier
) {
}
