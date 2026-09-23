package demo.socialanalytics.excel;

import demo.socialanalytics.entity.Platform;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Test cho engine đọc/ghi Excel bằng Reflection. Không đụng Spring, không đụng DB.
class ExcelMapperTest {

    private final ExcelMapper mapper = new ExcelMapper();

    // ----- Class dùng làm "vật thí nghiệm" -----

    // Field khai lộn xộn so với order, để kiểm ExcelMapper sắp theo order
    static class SampleRow {
        @ExcelColumn(header = "Ngày đo", order = 3)
        private LocalDateTime measuredAt;

        @ExcelColumn(header = "Nền tảng", order = 1, required = true)
        private Platform platform;

        @ExcelColumn(header = "Lượt thích", order = 2)
        private Integer likes;

        private String ignoredField;
    }

    static class NoColumnRow {
        private String name;
    }

    // ----- Helper dựng file Excel trong bộ nhớ -----

    private byte[] workbook(List<String> headers, List<List<Object>> rows) {
        try (XSSFWorkbook book = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = book.createSheet("Sheet1");
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                headerRow.createCell(i).setCellValue(headers.get(i));
            }
            CellStyle dateStyle = book.createCellStyle();
            dateStyle.setDataFormat(book.getCreationHelper().createDataFormat().getFormat("yyyy-mm-dd hh:mm:ss"));

            for (int r = 0; r < rows.size(); r++) {
                Row row = sheet.createRow(r + 1);
                List<Object> values = rows.get(r);
                for (int c = 0; c < values.size(); c++) {
                    Object value = values.get(c);
                    Cell cell = row.createCell(c);
                    switch (value) {
                        case null -> cell.setBlank();
                        case Number number -> cell.setCellValue(number.doubleValue());
                        case LocalDateTime dateTime -> {
                            cell.setCellValue(dateTime);
                            cell.setCellStyle(dateStyle);
                        }
                        default -> cell.setCellValue(String.valueOf(value));
                    }
                }
            }
            book.write(out);
            return out.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private ExcelReadResult<SampleRow> read(byte[] bytes) {
        return mapper.read(new ByteArrayInputStream(bytes), SampleRow.class);
    }

    @Nested
    @DisplayName("describe() — quét annotation bằng Reflection")
    class Describe {

        @Test
        void sapCotTheoOrderVaBoQuaFieldKhongCoAnnotation() {
            List<ExcelField> fields = mapper.describe(SampleRow.class);

            assertThat(fields).extracting(ExcelField::header)
                .containsExactly("Nền tảng", "Lượt thích", "Ngày đo");
            assertThat(fields).extracting(ExcelField::name).doesNotContain("ignoredField");
        }

        @Test
        void giuNguyenCoBatBuocCuaTungCot() {
            assertThat(mapper.describe(SampleRow.class))
                .filteredOn(ExcelField::required)
                .extracting(ExcelField::header)
                .containsExactly("Nền tảng");
        }

        // Quét bằng Reflection tốn kém nên kết quả được cache; gọi lại phải trả đúng cùng một list.
        @Test
        void dungLaiKetQuaDaQuetOLanGoiSau() {
            assertThat(mapper.describe(SampleRow.class)).isSameAs(mapper.describe(SampleRow.class));
        }

        @Test
        void baoLoiKhiClassKhongCoCotNao() {
            assertThatThrownBy(() -> mapper.describe(NoColumnRow.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("@ExcelColumn");
        }
    }

    @Nested
    @DisplayName("read() — đọc file vào đối tượng")
    class Read {

        @Test
        void docDuocCacKieuDuLieuKhacNhau() {
            LocalDateTime measuredAt = LocalDateTime.of(2026, 3, 1, 9, 30, 0);
            byte[] file = workbook(
                List.of("Nền tảng", "Lượt thích", "Ngày đo"),
                List.of(List.of("facebook", 120, measuredAt)));

            ExcelReadResult<SampleRow> result = read(file);

            assertThat(result.errors()).isEmpty();
            assertThat(result.values()).singleElement().satisfies(row -> {
                assertThat(row.platform).isEqualTo(Platform.FACEBOOK);
                assertThat(row.likes).isEqualTo(120);
                assertThat(row.measuredAt).isEqualTo(measuredAt);
            });
        }

        // Cột được tìm theo TÊN nên người dùng đảo cột hoặc gõ khác hoa thường vẫn đọc đúng.
        @Test
        void khopCotTheoTenKhongPhuThuocThuTuVaHoaThuong() {
            byte[] file = workbook(
                List.of("lượt thích", "NỀN TẢNG"),
                List.of(List.of(7, "twitter")));

            ExcelReadResult<SampleRow> result = read(file);

            assertThat(result.values()).singleElement().satisfies(row -> {
                assertThat(row.platform).isEqualTo(Platform.TWITTER);
                assertThat(row.likes).isEqualTo(7);
            });
        }

        @Test
        void boQuaDongTrong() {
            byte[] file = workbook(
                List.of("Nền tảng"),
                java.util.Arrays.asList(
                    List.of("facebook"),
                    java.util.Collections.singletonList(null),
                    List.of("twitter")));

            ExcelReadResult<SampleRow> result = read(file);

            assertThat(result.rows()).hasSize(2);
            assertThat(result.errors()).isEmpty();
        }

        // Một dòng hỏng chỉ làm hỏng dòng đó; các dòng còn lại vẫn đọc được.
        @Test
        void dongSaiDinhDangKhongLamHongCaFile() {
            byte[] file = workbook(
                List.of("Nền tảng", "Lượt thích"),
                List.of(
                    List.of("facebook", 10),
                    List.of("instagram", 20),
                    List.of("twitter", "nhiều")));

            ExcelReadResult<SampleRow> result = read(file);

            assertThat(result.rows()).hasSize(1);
            assertThat(result.totalRows()).isEqualTo(3);
            // Dòng 1 là tiêu đề nên dòng dữ liệu thứ hai hiển thị là dòng 3.
            assertThat(result.errors()).extracting(ExcelRowError::rowNumber).containsExactly(3, 4);
            assertThat(result.errors().get(0).message()).contains("instagram").contains("Cho phép");
            assertThat(result.errors().get(1).message()).contains("phải là số nguyên");
        }

        @Test
        void baoLoiDongKhiThieuGiaTriOCotBatBuoc() {
            byte[] file = workbook(
                List.of("Nền tảng", "Lượt thích"),
                java.util.List.of(java.util.Arrays.asList(null, 5)));

            ExcelReadResult<SampleRow> result = read(file);

            assertThat(result.rows()).isEmpty();
            assertThat(result.errors()).singleElement()
                .satisfies(error -> assertThat(error.message()).contains("Nền tảng"));
        }

        // Thiếu hẳn cột bắt buộc là lỗi CẢ FILE -> dừng ngay, khác với lỗi từng dòng.
        @Test
        void némExcelParseExceptionKhiThieuCotBatBuoc() {
            byte[] file = workbook(List.of("Lượt thích"), List.of(List.of(1)));

            assertThatThrownBy(() -> read(file))
                .isInstanceOf(ExcelParseException.class)
                .hasMessageContaining("Nền tảng");
        }

        // Cột không bắt buộc mà file không có thì bỏ qua, không phải lỗi.
        @Test
        void chapNhanFileThieuCotKhongBatBuoc() {
            byte[] file = workbook(List.of("Nền tảng"), List.of(List.of("facebook")));

            ExcelReadResult<SampleRow> result = read(file);

            assertThat(result.errors()).isEmpty();
            assertThat(result.values()).singleElement()
                .satisfies(row -> assertThat(row.likes).isNull());
        }

        // Mỗi dòng hợp lệ phải mang theo SỐ DÒNG GỐC
        @Test
        void moiDongHopLeMangTheoSoDongGocTrongFile() {
            byte[] file = workbook(
                List.of("Nền tảng", "Lượt thích"),
                List.of(
                    List.of("facebook", 10),
                    List.of("instagram", 20),
                    List.of("twitter", 30)));

            ExcelReadResult<SampleRow> result = read(file);

            // Dòng 2 và dòng 4 của file là hợp lệ; dòng 3 lỗi và bị loại.
            assertThat(result.rows()).extracting(ExcelRow::rowNumber).containsExactly(2, 4);
            assertThat(result.rows()).extracting(row -> row.value().likes).containsExactly(10, 30);
        }

        @Test
        void némExcelParseExceptionKhiFileKhongPhaiExcel() {
            byte[] notExcel = "đây chỉ là văn bản thường".getBytes(java.nio.charset.StandardCharsets.UTF_8);

            assertThatThrownBy(() -> read(notExcel))
                .isInstanceOf(ExcelParseException.class)
                .hasMessageContaining("không phải file Excel hợp lệ hoặc đã bị hỏng");
        }
    }

    @Nested
    @DisplayName("write() — ghi đối tượng ra file")
    class Write {

        @Test
        void ghiDongTieuDe() throws Exception {
            byte[] file = mapper.write(List.of(), SampleRow.class, "Bao cao");

            try (Workbook book = WorkbookFactory.create(new ByteArrayInputStream(file))) {
                Row header = book.getSheetAt(0).getRow(0);
                assertThat(header.getCell(0).getStringCellValue()).isEqualTo("Nền tảng");
                assertThat(header.getCell(1).getStringCellValue()).isEqualTo("Lượt thích");
                assertThat(header.getCell(2).getStringCellValue()).isEqualTo("Ngày đo");
                assertThat(book.getSheetAt(0).getSheetName()).isEqualTo("Bao cao");
            }
        }

        // record dùng được cho chiều GHI vì ExcelMapper chỉ đọc field, không set.
        @Test
        void ghiDuocCaRecord() throws Exception {
            var rows = List.of(new SimpleReport(1L, "facebook", 10));

            byte[] file = mapper.write(rows, SimpleReport.class, "Bao cao");

            try (Workbook book = WorkbookFactory.create(new ByteArrayInputStream(file))) {
                Row row = book.getSheetAt(0).getRow(1);
                assertThat((long) row.getCell(0).getNumericCellValue()).isEqualTo(1L);
                assertThat(row.getCell(1).getStringCellValue()).isEqualTo("facebook");
                assertThat((int) row.getCell(2).getNumericCellValue()).isEqualTo(10);
            }
        }

        @Test
        void deTrongOCoGiaTriNull() throws Exception {
            var rows = java.util.Arrays.asList(new SimpleReport(1L, null, null));

            byte[] file = mapper.write(rows, SimpleReport.class, "Bao cao");

            try (Workbook book = WorkbookFactory.create(new ByteArrayInputStream(file))) {
                Row row = book.getSheetAt(0).getRow(1);
                assertThat(row.getCell(1).getCellType()).isEqualTo(CellType.BLANK);
                assertThat(row.getCell(2).getCellType()).isEqualTo(CellType.BLANK);
            }
        }
    }

    record SimpleReport(
        @ExcelColumn(header = "ID", order = 1) Long id,
        @ExcelColumn(header = "Nền tảng", order = 2) String platform,
        @ExcelColumn(header = "Lượt thích", order = 3) Integer likes
    ) {
    }

    // Ghi ra rồi đọc lại phải ra đúng dữ liệu ban đầu — phép thử chắc chắn nhất cho cặp read/write.
    @Test
    void ghiRaRoiDocLaiDuocDungDuLieu() {
        SampleRow original = new SampleRow();
        original.platform = Platform.TWITTER;
        original.likes = 42;
        original.measuredAt = LocalDateTime.of(2026, 5, 20, 14, 0, 0);

        byte[] file = mapper.write(List.of(original), SampleRow.class, "Bao cao");
        ExcelReadResult<SampleRow> result = read(file);

        assertThat(result.errors()).isEmpty();
        assertThat(result.values()).singleElement().satisfies(row -> {
            assertThat(row.platform).isEqualTo(Platform.TWITTER);
            assertThat(row.likes).isEqualTo(42);
            assertThat(row.measuredAt).isEqualTo(original.measuredAt);
        });
    }
}
