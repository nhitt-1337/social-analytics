package demo.socialanalytics.excel;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

final class ExcelCellConverter {
    private static final List<DateTimeFormatter> DATE_TIME_FORMATS = List.of(
        DateTimeFormatter.ISO_LOCAL_DATE_TIME,
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"),
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
    );

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
        DateTimeFormatter.ISO_LOCAL_DATE,
        DateTimeFormatter.ofPattern("dd/MM/yyyy")
    );

    private ExcelCellConverter() {
    }

    static Object convert(Cell cell, Class<?> targetType, String header) {
        String raw = asText(cell);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();

        if (targetType == String.class) {
            return value;
        }
        if (targetType == Integer.class || targetType == int.class) {
            return parseInteger(value, header);
        }
        if (targetType == Long.class || targetType == long.class) {
            return parseLong(value, header);
        }
        if (targetType == Boolean.class || targetType == boolean.class) {
            return parseBoolean(value, header);
        }
        if (targetType == LocalDateTime.class) {
            return parseDateTime(cell, value, header);
        }
        if (targetType == LocalDate.class) {
            LocalDateTime dateTime = parseDateTime(cell, value, header);
            return dateTime.toLocalDate();
        }
        if (targetType.isEnum()) {
            return parseEnum(value, targetType, header);
        }
        throw new IllegalStateException(
            "ExcelMapper chưa hỗ trợ kiểu " + targetType.getSimpleName() + " (cột '" + header + "')");
    }

    private static String asText(Cell cell) {
        if (cell == null) {
            return null;
        }
        CellType type = cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
        return switch (type) {
            case STRING -> cell.getStringCellValue();
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getLocalDateTimeCellValue().toString();
                }
                double number = cell.getNumericCellValue();
                yield number == Math.floor(number) && !Double.isInfinite(number)
                    ? String.valueOf((long) number)
                    : String.valueOf(number);
            }
            default -> null;
        };
    }

    private static Integer parseInteger(String value, String header) {
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException exception) {
            throw new ExcelCellException("cột '" + header + "' phải là số nguyên, nhận được '" + value + "'");
        }
    }

    private static Long parseLong(String value, String header) {
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException exception) {
            throw new ExcelCellException("cột '" + header + "' phải là số nguyên, nhận được '" + value + "'");
        }
    }

    private static Boolean parseBoolean(String value, String header) {
        String normalized = value.toLowerCase(Locale.ROOT);
        if (List.of("true", "1", "yes", "x", "có").contains(normalized)) {
            return Boolean.TRUE;
        }
        if (List.of("false", "0", "no", "không").contains(normalized)) {
            return Boolean.FALSE;
        }
        throw new ExcelCellException("cột '" + header + "' phải là true hoặc false, nhận được '" + value + "'");
    }

    private static LocalDateTime parseDateTime(Cell cell, String value, String header) {
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue();
        }
        for (DateTimeFormatter formatter : DATE_TIME_FORMATS) {
            try {
                return LocalDateTime.parse(value, formatter);
            } catch (DateTimeParseException ignored) {
            }
        }
        for (DateTimeFormatter formatter : DATE_FORMATS) {
            try {
                return LocalDate.parse(value, formatter).atStartOfDay();
            } catch (DateTimeParseException ignored) {
            }
        }
        throw new ExcelCellException("cột '" + header + "' phải là ngày giờ (vd 2026-01-31 08:30 hoặc 31/01/2026)"
            + ", nhận được '" + value + "'");
    }

    private static Object parseEnum(String value, Class<?> enumType, String header) {
        for (Object constant : enumType.getEnumConstants()) {
            if (((Enum<?>) constant).name().equalsIgnoreCase(value)) {
                return constant;
            }
        }
        String allowed = java.util.Arrays.stream(enumType.getEnumConstants())
            .map(constant -> ((Enum<?>) constant).name().toLowerCase(Locale.ROOT))
            .toList().toString();
        throw new ExcelCellException("cột '" + header + "' có giá trị '" + value + "' không hợp lệ. Cho phép: " + allowed);
    }
}
