package demo.socialanalytics.security;

import demo.socialanalytics.entity.AuthProvider;

import java.util.Map;

// Thông tin người dùng đã được chuẩn hoá về một khuôn chung cho mọi nhà cung cấp.
//
// Mỗi nơi trả một kiểu JSON khác nhau nên phần "đọc hiểu" gom hết vào đây; tầng trên chỉ làm
// việc với record này, không phải if/else theo từng nhà cung cấp.
public record SocialUserAttributes(
    AuthProvider provider,
    String providerId,
    String fullName,
    String email,
    String avatarUrl
) {

    // Facebook: /me?fields=id,name,email trả phẳng { "id", "name", "email" }.
    static SocialUserAttributes fromFacebook(Map<String, Object> attributes) {
        String id = text(attributes.get("id"));
        return new SocialUserAttributes(
            AuthProvider.FACEBOOK,
            id,
            text(attributes.get("name")),
            text(attributes.get("email")),
            // Ảnh đại diện Facebook lấy theo id, không nằm trong phần trả về.
            id == null ? null : "https://graph.facebook.com/" + id + "/picture?type=large");
    }

    // X (Twitter): /2/users/me trả LỒNG một lớp { "data": { "id", "name", "username" } }.
    // Đây là lý do không dùng thẳng DefaultOAuth2UserService cho X — nó tìm thuộc tính ở
    // tầng ngoài cùng nên không thấy "username" và ném lỗi.
    //
    // X cũng KHÔNG trả email (phải xin quyền riêng), nên email để null.
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
    // Ghép cả nhà cung cấp vào để id trùng nhau giữa hai nền tảng không lẫn thành một người.
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
