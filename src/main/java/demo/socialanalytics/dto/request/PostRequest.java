package demo.socialanalytics.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

// Body tạo/cập nhật bài viết. userId không nhận từ client ở bước sau (lấy từ phiên đăng nhập),
// tạm thời cho phép truyền để CRUD dùng được khi chưa có auth.
public record PostRequest(
    @NotNull(message = "userId không được để trống")
    Long userId,

    @NotBlank(message = "platform không được để trống")
    String platform,

    @NotBlank(message = "externalId không được để trống")
    @Size(max = 100, message = "externalId không được vượt quá 100 ký tự")
    String externalId,

    @Size(max = 5000, message = "content không được vượt quá 5000 ký tự")
    String content,

    @Size(max = 500, message = "url không được vượt quá 500 ký tự")
    String url,

    @PastOrPresent(message = "postedAt không được ở tương lai")
    LocalDateTime postedAt
) {
}
