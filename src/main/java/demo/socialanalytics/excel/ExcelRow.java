package demo.socialanalytics.excel;

// Một dòng đọc được, kèm SỐ DÒNG GỐC trong file.
//
// Phải mang theo số dòng vì các dòng lỗi bị loại khỏi kết quả: nếu tầng trên tự suy số dòng
// từ vị trí trong danh sách thì mọi dòng nằm sau một dòng lỗi sẽ bị báo sai vị trí.
// rowNumber đếm như Excel hiển thị (dòng 1 là tiêu đề).
public record ExcelRow<T>(int rowNumber, T value) {
}
