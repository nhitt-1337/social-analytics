package demo.socialanalytics.excel;

// Lỗi ở phạm vi MỘT ô. ExcelMapper bắt lại, gắn thêm số dòng rồi bỏ qua dòng đó
// thay vì làm hỏng cả lần import.
public class ExcelCellException extends RuntimeException {
    public ExcelCellException(String message) {
        super(message);
    }
}
