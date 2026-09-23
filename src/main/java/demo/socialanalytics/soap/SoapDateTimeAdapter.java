package demo.socialanalytics.soap;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

// Chuyển đổi xs:dateTime <-> LocalDateTime cho các lớp JAXB sinh từ XSD.
// Được khai trong src/main/resources/xjb/bindings.xjb và gọi từ code sinh tự động.
public final class SoapDateTimeAdapter {

    private SoapDateTimeAdapter() {
    }

    public static LocalDateTime parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            // Client khác có thể gửi kèm múi giờ ("2026-09-23T10:00:00+07:00").
            // Thử kiểu có offset trước rồi mới tới kiểu không offset.
            return OffsetDateTime.parse(value).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            return LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }
    }

    public static String print(LocalDateTime value) {
        return value == null ? null : value.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }
}
