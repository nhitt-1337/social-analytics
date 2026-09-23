package demo.socialanalytics.messaging;

import java.time.LocalDateTime;

public record ImportCompletedMessage(
    Long userId,
    int totalRows,
    int imported,
    int skipped,
    LocalDateTime completedAt
) {
}
