package demo.socialanalytics.dto.response;

import demo.socialanalytics.entity.PlatformSummary;

import java.time.LocalDateTime;

public record PlatformSummaryResponse(
    String platform,
    long postCount,
    long accountCount,
    LocalDateTime updatedAt
) {
    public static PlatformSummaryResponse of(PlatformSummary summary) {
        return new PlatformSummaryResponse(
            summary.getPlatform().getSlug(),
            summary.getPostCount(),
            summary.getAccountCount(),
            summary.getUpdatedAt());
    }
}
