package demo.socialanalytics.dto.response;

import java.util.List;

// Kết quả import Excel. Import "chịu lỗi": dòng nào sai thì bỏ qua và ghi vào errors,
// các dòng còn lại vẫn được lưu -> người dùng sửa vài dòng rồi import lại phần thiếu.
//
// totalRows: số dòng dữ liệu đọc được (không tính tiêu đề)
// imported : số bài viết đã tạo mới
// skipped  : số dòng bị bỏ (trùng bài đã có, hoặc dữ liệu sai)
public record ImportResultResponse(
    int totalRows,
    int imported,
    int skipped,
    List<RowErrorResponse> errors
) {
    // rowNumber đếm đúng như Excel hiển thị để người dùng mở file lên là thấy dòng sai.
    public record RowErrorResponse(int rowNumber, String message) {
    }
}
