package demo.socialanalytics.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;

import java.time.LocalDateTime;

// Body ghi nhận một lần đo tương tác. Background job ở bước sau sẽ gọi chính service này.
public record MetricRequest(
    @NotNull(message = "postId không được để trống")
    Long postId,

    @NotNull(message = "likes không được để trống")
    @Min(value = 0, message = "likes không được nhỏ hơn 0")
    Integer likes,

    @NotNull(message = "shares không được để trống")
    @Min(value = 0, message = "shares không được nhỏ hơn 0")
    Integer shares,

    @Min(value = 0, message = "comments không được nhỏ hơn 0")
    Integer comments,

    @NotNull(message = "followers không được để trống")
    @Min(value = 0, message = "followers không được nhỏ hơn 0")
    Integer followers,

    // Bỏ trống thì lấy thời điểm hiện tại.
    @PastOrPresent(message = "collectedAt không được ở tương lai")
    LocalDateTime collectedAt
) {
}
