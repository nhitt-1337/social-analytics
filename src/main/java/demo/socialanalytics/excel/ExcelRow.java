package demo.socialanalytics.excel;

// Một dòng đọc được, kèm SỐ DÒNG GỐC trong file.
public record ExcelRow<T>(int rowNumber, T value) {
}
