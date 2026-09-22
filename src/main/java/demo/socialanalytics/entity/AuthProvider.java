package demo.socialanalytics.entity;

import java.util.Locale;

// Nguồn xác thực của tài khoản. LOCAL dành cho admin tạo sẵn; FACEBOOK/TWITTER là Social Login.
public enum AuthProvider {
    LOCAL,
    FACEBOOK,
    TWITTER;

    public String getSlug() {
        return name().toLowerCase(Locale.ROOT);
    }

    // Đổi registrationId trong cấu hình OAuth2 sang enum.
    //
    // Twitter đổi tên thành X nên Spring Security đặt hằng provider dựng sẵn là `x`
    // (https://api.x.com/...). Ở phía mình vẫn giữ tên TWITTER cho khớp đề bài và dữ liệu cũ,
    // nên chấp nhận cả hai cách gọi.
    public static AuthProvider fromRegistrationId(String registrationId) {
        if (registrationId == null) {
            throw new IllegalArgumentException("registrationId không được null");
        }
        return switch (registrationId.toLowerCase(Locale.ROOT)) {
            case "facebook" -> FACEBOOK;
            case "x", "twitter" -> TWITTER;
            default -> throw new IllegalArgumentException(
                "Chưa hỗ trợ đăng nhập bằng '" + registrationId + "'");
        };
    }
}
