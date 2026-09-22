package demo.socialanalytics.excel;

// Lỗi ở phạm vi CẢ FILE (không mở được, sai sheet, thiếu cột bắt buộc) -> dừng luôn,
// khác với ExcelCellException chỉ làm hỏng một dòng.
public class ExcelParseException extends RuntimeException {
    public ExcelParseException(String message) {
        super(message);
    }

    public ExcelParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
