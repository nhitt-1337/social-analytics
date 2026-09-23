package demo.socialanalytics.excel;

// Lỗi ở phạm vi CẢ FILE (không mở được
public class ExcelParseException extends RuntimeException {
    public ExcelParseException(String message) {
        super(message);
    }

    public ExcelParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
