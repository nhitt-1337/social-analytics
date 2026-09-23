package demo.socialanalytics.soap;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// Địa chỉ dịch vụ tỷ giá SOAP. Tiền tố: social.exchange-rate.*
@ConfigurationProperties(prefix = "social.exchange-rate")
public record ExchangeRateProperties(
    @DefaultValue("http://localhost:8080/api/v1/soap") String endpoint
) {
}
