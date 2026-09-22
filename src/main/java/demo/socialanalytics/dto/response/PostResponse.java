package demo.socialanalytics.dto.response;

import java.time.LocalDateTime;

// Bài viết kèm số liệu MỚI NHẤT để dashboard hiển thị ngay, không phải gọi thêm /metrics.
// latestMetric null khi bài chưa được crawl lần nào.
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
