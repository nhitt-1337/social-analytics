package demo.socialanalytics.entity;

import java.util.Locale;

// Nền tảng mạng xã hội mà bài viết thuộc về.
public enum Platform {
    FACEBOOK,
    TWITTER;

    public String getSlug() {
        return name().toLowerCase(Locale.ROOT);
    }
}
