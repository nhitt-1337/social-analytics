package demo.socialanalytics.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "social.crawl")
public record CrawlProperties(

    @DefaultValue("true") boolean enabled,

    @DefaultValue("8") int poolSize,

    @DefaultValue("100") int queueCapacity,

    @DefaultValue("300") int timeoutSeconds,

    @DefaultValue Mock mock
) {
    public record Mock(
        @DefaultValue("40") long latencyMs,

        @DefaultValue("0.1") double failureRate
    ) {
    }
}
