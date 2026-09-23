package demo.socialanalytics.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "social.messaging")
public record MessagingProperties(

    @DefaultValue("3") int maxRedeliveries,

    @DefaultValue("500") long initialRedeliveryDelayMs,

    @DefaultValue("2") double backOffMultiplier
) {
}
