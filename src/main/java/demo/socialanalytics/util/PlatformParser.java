package demo.socialanalytics.util;

import demo.socialanalytics.entity.Platform;
import demo.socialanalytics.exception.InvalidRequestParameterException;

import java.util.Arrays;
import java.util.Locale;

public final class PlatformParser {
    private PlatformParser() {
    }

    public static Platform parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Platform.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new InvalidRequestParameterException(
                "Nền tảng không hợp lệ: " + value + ". Cho phép: "
                    + Arrays.stream(Platform.values()).map(Platform::getSlug).toList());
        }
    }
}
