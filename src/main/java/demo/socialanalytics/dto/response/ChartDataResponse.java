package demo.socialanalytics.dto.response;

import java.time.LocalDate;
import java.util.List;

public record ChartDataResponse(
    List<LocalDate> labels,
    List<Long> likes,
    List<Long> shares,
    List<Long> comments,
    List<Long> followers,
    List<PlatformSummaryResponse> platforms,
    java.time.LocalDateTime generatedAt
) {
    public boolean isEmpty() {
        return labels.isEmpty();
    }
}
