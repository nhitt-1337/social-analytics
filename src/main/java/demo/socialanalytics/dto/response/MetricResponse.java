package demo.socialanalytics.dto.response;

import java.time.LocalDateTime;

public record MetricResponse(
    Long id,
    Long postId,
    Integer likes,
    Integer shares,
    Integer comments,
    Integer followers,
    LocalDateTime collectedAt
) {
}
