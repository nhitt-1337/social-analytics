package demo.socialanalytics.excel;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

// Xuất Excel cho MỌI model, không cần model đó biết gì về Excel.
//
// Khác ExcelMapper: ExcelMapper cần @ExcelColumn để biết cột nào, tên gì, thứ tự ra sao — phù hợp
// khi mình làm chủ lớp đó và muốn kiểm soát chính xác. ModelExporter thì suy ra hết bằng
// Reflection, dùng được cho lớp không sửa được (thư viện ngoài, lớp sinh từ XSD) và cho các
// màn hình kiểu "xuất bảng này ra Excel" mà không phải tạo thêm DTO.
//
// Có @ExcelColumn thì vẫn ưu tiên ExcelMapper — khai báo tường minh luôn thắng suy đoán.
@Component
public class ModelExporter {

    private static final String DATE_TIME_PATTERN = "yyyy-mm-dd hh:mm:ss";
    private static final String DATE_PATTERN = "yyyy-mm-dd";

    private final ExcelMapper excelMapper;

    public ModelExporter(ExcelMapper excelMapper) {
        this.excelMapper = excelMapper;
    }

    public <T> byte[] export(Collection<T> rows, Class<T> type, String sheetName) {
        if (hasExcelColumns(type)) {
            return excelMapper.write(List.copyOf(rows), type, sheetName);
        }
        return writeByReflection(rows, type, sheetName);
    }

    // Tên cột suy ra được, cho phía gọi biết trước file sẽ có gì.
    public List<String> headers(Class<?> type) {
        return hasExcelColumns(type)
            ? excelMapper.describe(type).stream().map(ExcelField::header).toList()
            : ModelIntrospector.describe(type).stream().map(ModelProperty::header).toList();
    }

    private boolean hasExcelColumns(Class<?> type) {
        for (Class<?> current = type; current != null && current != Object.class;
             current = current.getSuperclass()) {
            for (var field : current.getDeclaredFields()) {
                if (field.isAnnotationPresent(ExcelColumn.class)) {
                    return true;
                }
            }
        }
        return false;
    }

    private <T> byte[] writeByReflection(Collection<T> rows, Class<T> type, String sheetName) {
        List<ModelProperty> properties = ModelIntrospector.describe(type);

        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet(sheetName);
            CellStyle headerStyle = headerStyle(workbook);
            CellStyle dateTimeStyle = dateStyle(workbook, DATE_TIME_PATTERN);
            CellStyle dateOnlyStyle = dateStyle(workbook, DATE_PATTERN);

            Row headerRow = sheet.createRow(0);
            for (int column = 0; column < properties.size(); column++) {
                Cell cell = headerRow.createCell(column);
                cell.setCellValue(properties.get(column).header());
                cell.setCellStyle(headerStyle);
            }

            int rowIndex = 1;
            for (T item : rows) {
                Row row = sheet.createRow(rowIndex++);
                for (int column = 0; column < properties.size(); column++) {
                    fill(row.createCell(column), properties.get(column).read(item),
                        dateTimeStyle, dateOnlyStyle);
                }
            }

            for (int column = 0; column < properties.size(); column++) {
                sheet.autoSizeColumn(column);
            }
            sheet.createFreezePane(0, 1);

            workbook.write(output);
            return output.toByteArray();

        } catch (IOException exception) {
            throw new ExcelParseException("Không ghi được file Excel: " + exception.getMessage(), exception);
        }
    }

    // Kiểu nào không biết cách hiển thị thì đổ về String.valueOf: thà có một cột đọc tạm được
    // còn hơn cả file xuất hỏng vì một thuộc tính lạ.
    private void fill(Cell cell, Object value, CellStyle dateTimeStyle, CellStyle dateOnlyStyle) {
        switch (value) {
            case null -> cell.setBlank();
            // Number đã bao cả BigDecimal (tỷ giá từ SOAP), Integer, Long...
            case Number number -> cell.setCellValue(number.doubleValue());
            case Boolean bool -> cell.setCellValue(bool);
            case LocalDateTime dateTime -> {
                cell.setCellValue(dateTime);
                cell.setCellStyle(dateTimeStyle);
            }
            case LocalDate date -> {
                cell.setCellValue(date);
                cell.setCellStyle(dateOnlyStyle);
            }
            case Enum<?> enumValue -> cell.setCellValue(enumValue.name().toLowerCase(Locale.ROOT));
            case Collection<?> collection -> cell.setCellValue(collection.size() + " mục");
            default -> cell.setCellValue(String.valueOf(value));
        }
    }

    private CellStyle headerStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private CellStyle dateStyle(Workbook workbook, String pattern) {
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat(pattern));
        return style;
    }
}
