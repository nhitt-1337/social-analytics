package demo.socialanalytics.excel;

// Lỗi ở phạm vi MỘT ô.
public class ExcelCellException extends RuntimeException {
    public ExcelCellException(String message) {
        super(message);
    }
}
