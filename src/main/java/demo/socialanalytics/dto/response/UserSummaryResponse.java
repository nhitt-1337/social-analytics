package demo.socialanalytics.dto.response;

// Thông tin người dùng nhúng trong response bài viết; không lộ provider/providerId.
public record UserSummaryResponse(
    Long id,
    String fullName,
    String email
) {
}
