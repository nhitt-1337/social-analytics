package demo.socialanalytics.support;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.List;

// Dựng file .xlsx trong bộ nhớ cho test import, và đọc lại file xuất ra để kiểm tra.
public final class ExcelTestFiles {

    public static final List<String> POST_HEADERS =
        List.of("platform", "externalId", "content", "url", "postedAt");

    private ExcelTestFiles() {
    }

    // rows: mỗi phần tử là một dòng, giá trị null -> ô trống.
    public static byte[] postsFile(List<List<Object>> rows) {
        return file(POST_HEADERS, rows);
    }

    public static byte[] file(List<String> headers, List<List<Object>> rows) {
        try (XSSFWorkbook book = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = book.createSheet("Sheet1");
            CellStyle dateStyle = book.createCellStyle();
            dateStyle.setDataFormat(book.getCreationHelper().createDataFormat().getFormat("yyyy-mm-dd hh:mm:ss"));

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                headerRow.createCell(i).setCellValue(headers.get(i));
            }
            for (int r = 0; r < rows.size(); r++) {
                Row row = sheet.createRow(r + 1);
                List<Object> values = rows.get(r);
                for (int c = 0; c < values.size(); c++) {
                    Cell cell = row.createCell(c);
                    switch (values.get(c)) {
                        case null -> cell.setBlank();
                        case Number number -> cell.setCellValue(number.doubleValue());
                        case LocalDateTime dateTime -> {
                            cell.setCellValue(dateTime);
                            cell.setCellStyle(dateStyle);
                        }
                        case Object value -> cell.setCellValue(String.valueOf(value));
                    }
                }
            }
            book.write(out);
            return out.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException("Không dựng được file Excel cho test", exception);
        }
    }

    // Đọc toàn bộ sheet đầu tiên về dạng chuỗi để test so khớp cho dễ.
    public static List<List<String>> readAll(byte[] bytes) {
        try (Workbook book = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            Sheet sheet = book.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();
            java.util.List<java.util.List<String>> result = new java.util.ArrayList<>();
            for (int r = 0; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                java.util.List<String> values = new java.util.ArrayList<>();
                if (row != null) {
                    for (int c = 0; c < row.getLastCellNum(); c++) {
                        values.add(formatter.formatCellValue(row.getCell(c)));
                    }
                }
                result.add(values);
            }
            return result;
        } catch (Exception exception) {
            throw new IllegalStateException("Không đọc được file Excel trong test", exception);
        }
    }
}
