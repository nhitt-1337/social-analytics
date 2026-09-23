package demo.socialanalytics.excel;

public class ExcelParseException extends RuntimeException {
    public ExcelParseException(String message) {
        super(message);
    }

    public ExcelParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
