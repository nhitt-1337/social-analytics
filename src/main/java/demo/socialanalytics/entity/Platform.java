package demo.socialanalytics.entity;

import java.util.Locale;

public enum Platform {
    FACEBOOK,
    TWITTER;

    public String getSlug() {
        return name().toLowerCase(Locale.ROOT);
    }
}
