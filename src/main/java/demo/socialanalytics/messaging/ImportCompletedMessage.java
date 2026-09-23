package demo.socialanalytics.messaging;

import java.time.LocalDateTime;

// Nội dung message IMPORT_COMPLETED.
public record ImportCompletedMessage(
    Long userId,
    int totalRows,
    int imported,
    int skipped,
    LocalDateTime completedAt
) {
}
