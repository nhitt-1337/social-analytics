package demo.socialanalytics.entity;

import java.util.Locale;

public enum AuthProvider {
    LOCAL,
    FACEBOOK,
    TWITTER;

    public String getSlug() {
        return name().toLowerCase(Locale.ROOT);
    }

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
