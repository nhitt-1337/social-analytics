package demo.socialanalytics.dto.response;

import java.time.LocalDate;
import java.util.List;

// Dữ liệu cho Chart.js, đã sắp sẵn thành labels + các series để phía trình duyệt khỏi phải
public record ChartDataResponse(
    List<LocalDate> labels,
    List<Long> likes,
    List<Long> shares,
    List<Long> comments,
    List<Long> followers,
    // Số liệu hiện tại theo nền tảng, cho biểu đồ tròn bên cạnh.
    List<PlatformSummaryResponse> platforms,
    // Mốc thời gian dữ liệu được dựng, để trình duyệt biết bản nào mới hơn.
    java.time.LocalDateTime generatedAt
) {
    public boolean isEmpty() {
        return labels.isEmpty();
    }
}
