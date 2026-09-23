package demo.socialanalytics.security;

import demo.socialanalytics.entity.AuthProvider;

import java.util.Map;

// Thông tin người dùng đã được chuẩn hoá về một khuôn chung cho mọi nhà cung cấp.
public record SocialUserAttributes(
    AuthProvider provider,
    String providerId,
    String fullName,
    String email,
    String avatarUrl
) {

    // Facebook: /me?fields=id,name,email,picture.type(large) trả về { "id"
    static SocialUserAttributes fromFacebook(Map<String, Object> attributes) {
        return new SocialUserAttributes(
            AuthProvider.FACEBOOK,
            text(attributes.get("id")),
            text(attributes.get("name")),
            text(attributes.get("email")),
            facebookPicture(attributes));
    }

    // Phải xin picture trong user-info; tự ghép URL sẽ ra ảnh mặc định xám
    @SuppressWarnings("unchecked")
    private static String facebookPicture(Map<String, Object> attributes) {
        if (!(attributes.get("picture") instanceof Map<?, ?> picture)) {
            return null;
        }
        if (!(((Map<String, Object>) picture).get("data") instanceof Map<?, ?> data)) {
            return null;
        }
        Map<String, Object> fields = (Map<String, Object>) data;
        if (Boolean.TRUE.equals(fields.get("is_silhouette"))) {
            return null;
        }
        return text(fields.get("url"));
    }

    // X (Twitter): /2/users/me trả LỒNG một lớp { "data": { "id", "name", "username" } }.
    @SuppressWarnings("unchecked")
    static SocialUserAttributes fromX(Map<String, Object> attributes) {
        Map<String, Object> data = attributes.get("data") instanceof Map<?, ?> nested
            ? (Map<String, Object>) nested
            : attributes;

        String name = text(data.get("name"));
        String username = text(data.get("username"));
        return new SocialUserAttributes(
            AuthProvider.TWITTER,
            text(data.get("id")),
            name != null ? name : username,
            null,
            text(data.get("profile_image_url")));
    }

    public static SocialUserAttributes of(String registrationId, Map<String, Object> attributes) {
        AuthProvider provider = AuthProvider.fromRegistrationId(registrationId);
        return switch (provider) {
            case FACEBOOK -> fromFacebook(attributes);
            case TWITTER -> fromX(attributes);
            case LOCAL -> throw new IllegalArgumentException("LOCAL không phải nhà cung cấp OAuth2");
        };
    }

    // Tên định danh duy nhất của phiên đăng nhập, vd "facebook:123456".
    public String principalName() {
        return provider.getSlug() + ":" + providerId;
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }
}
