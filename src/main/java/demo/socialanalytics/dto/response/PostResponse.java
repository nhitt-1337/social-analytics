package demo.socialanalytics.dto.response;

import java.time.LocalDateTime;

public record PostResponse(
    Long id,
    String platform,
    String externalId,
    String content,
    String url,
    LocalDateTime postedAt,
    UserSummaryResponse user,
    MetricResponse latestMetric,
    LocalDateTime createdAt
) {
}
