package demo.socialanalytics.excel;

import java.util.List;

// Kết quả đọc file: các dòng hợp lệ (kèm số dòng gốc) + các dòng lỗi.
public record ExcelReadResult<T>(List<ExcelRow<T>> rows, List<ExcelRowError> errors) {

    public int totalRows() {
        return rows.size() + errors.size();
    }

    // Tiện cho chỗ chỉ cần dữ liệu, không quan tâm dòng nào.
    public List<T> values() {
        return rows.stream().map(ExcelRow::value).toList();
    }
}
