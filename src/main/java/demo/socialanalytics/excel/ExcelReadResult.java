package demo.socialanalytics.excel;

import java.util.List;

public record ExcelReadResult<T>(List<ExcelRow<T>> rows, List<ExcelRowError> errors) {
    public int totalRows() {
        return rows.size() + errors.size();
    }

    public List<T> values() {
        return rows.stream().map(ExcelRow::value).toList();
    }
}
