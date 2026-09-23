package demo.socialanalytics.soap;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// Địa chỉ dịch vụ tỷ giá SOAP. Tiền tố: social.exchange-rate.*
//
// Mặc định trỏ vào chính endpoint của ứng dụng này để demo chạy được offline.
// Ở thật thì đổi sang địa chỉ của nhà cung cấp — không phải sửa code.
@ConfigurationProperties(prefix = "social.exchange-rate")
public record ExchangeRateProperties(
    @DefaultValue("http://localhost:8080/api/v1/soap") String endpoint
) {
}
