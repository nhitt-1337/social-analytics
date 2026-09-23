package demo.socialanalytics.dto.response;

import demo.socialanalytics.entity.CrawlRun;

import java.time.LocalDateTime;

public record CrawlRunResponse(
    Long id,
    String status,
    LocalDateTime startedAt,
    LocalDateTime finishedAt,
    long durationMs,
    int totalAccounts,
    int totalPosts,
    int succeededPosts,
    int failedPosts,
    String message
) {
    public static CrawlRunResponse of(CrawlRun run) {
        return new CrawlRunResponse(
            run.getId(),
            run.getStatus().name(),
            run.getStartedAt(),
            run.getFinishedAt(),
            run.duration().toMillis(),
            run.getTotalAccounts(),
            run.getTotalPosts(),
            run.getSucceededPosts(),
            run.getFailedPosts(),
            run.getMessage());
    }
}
