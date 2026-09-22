package demo.socialanalytics.excel;

// Một dòng bị bỏ qua và lý do. rowNumber đếm như Excel hiển thị (dòng 1 là tiêu đề)
// để người dùng mở file lên là thấy đúng chỗ sai.
public record ExcelRowError(int rowNumber, String message) {
}
