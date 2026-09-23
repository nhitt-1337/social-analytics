package demo.socialanalytics.dto.response;

import java.util.List;

// Kết quả import Excel. Import "chịu lỗi": dòng nào sai thì bỏ qua và ghi vào errors,
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
