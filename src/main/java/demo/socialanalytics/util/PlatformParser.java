package demo.socialanalytics.util;

import demo.socialanalytics.entity.Platform;
import demo.socialanalytics.exception.InvalidRequestParameterException;

import java.util.Arrays;
import java.util.Locale;

// Chuyển chuỗi client gửi ("facebook") thành enum Platform, không phân biệt hoa thường.
// Gom vào util vì cả PostService lẫn phần import Excel ở bước sau đều cần.
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
