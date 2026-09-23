package demo.socialanalytics.security;

import demo.socialanalytics.entity.AuthProvider;

import java.util.Map;

public record SocialUserAttributes(
    AuthProvider provider,
    String providerId,
    String fullName,
    String email,
    String avatarUrl
) {
    static SocialUserAttributes fromFacebook(Map<String, Object> attributes) {
        return new SocialUserAttributes(
            AuthProvider.FACEBOOK,
            text(attributes.get("id")),
            text(attributes.get("name")),
            text(attributes.get("email")),
            facebookPicture(attributes));
    }

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
