package demo.socialanalytics.excel;

import org.apache.poi.EmptyFileException;
import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.UnsupportedFileFormatException;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ExcelMapper {
    public static final int MAX_DATA_ROWS = 5_000;

    private static final String DATE_TIME_PATTERN = "yyyy-mm-dd hh:mm:ss";

    private final Map<Class<?>, List<ExcelField>> cache = new ConcurrentHashMap<>();

    public List<ExcelField> describe(Class<?> type) {
        return cache.computeIfAbsent(type, ExcelMapper::scan);
    }

    private static List<ExcelField> scan(Class<?> type) {
        List<ExcelField> fields = new ArrayList<>();
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                ExcelColumn column = field.getAnnotation(ExcelColumn.class);
                if (column == null) {
                    continue;
                }
                field.setAccessible(true);
                fields.add(new ExcelField(field, column.header(), column.order(), column.required()));
            }
        }
        if (fields.isEmpty()) {
            throw new IllegalStateException(
                type.getSimpleName() + " không có field nào gắn @ExcelColumn");
        }
        fields.sort(Comparator.comparingInt(ExcelField::order));
        return List.copyOf(fields);
    }

    public <T> ExcelReadResult<T> read(InputStream input, Class<T> type) {
        List<ExcelField> fields = describe(type);

        try (Workbook workbook = WorkbookFactory.create(input)) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new ExcelParseException("File Excel không có sheet nào");
            }
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) {
                throw new ExcelParseException("File Excel trống, thiếu dòng tiêu đề");
            }

            Map<String, Integer> columnIndexes = readHeader(headerRow);
            Map<ExcelField, Integer> layout = matchColumns(fields, columnIndexes);

            List<ExcelRow<T>> rows = new ArrayList<>();
            List<ExcelRowError> errors = new ArrayList<>();
            int lastRow = sheet.getLastRowNum();

            for (int index = headerRow.getRowNum() + 1; index <= lastRow; index++) {
                Row row = sheet.getRow(index);
                if (isBlank(row)) {
                    continue;
                }
                if (rows.size() + errors.size() >= MAX_DATA_ROWS) {
                    throw new ExcelParseException(
                        "File vượt quá " + MAX_DATA_ROWS + " dòng dữ liệu, hãy tách nhỏ file");
                }
                int displayRow = index + 1;
                try {
                    rows.add(new ExcelRow<>(displayRow, bind(row, type, layout)));
                } catch (ExcelCellException exception) {
                    errors.add(new ExcelRowError(displayRow, exception.getMessage()));
                }
            }
            return new ExcelReadResult<>(rows, errors);

        } catch (EmptyFileException exception) {
            throw new ExcelParseException("File tải lên rỗng", exception);
        } catch (EncryptedDocumentException exception) {
            throw new ExcelParseException("File Excel đang được đặt mật khẩu", exception);
        } catch (UnsupportedFileFormatException | IOException exception) {
            throw new ExcelParseException("File tải lên không phải file Excel hợp lệ hoặc đã bị hỏng", exception);
        }
    }

    private Map<String, Integer> readHeader(Row headerRow) {
        Map<String, Integer> indexes = new HashMap<>();
        for (Cell cell : headerRow) {
            String text = cell.getCellType() == CellType.STRING ? cell.getStringCellValue() : null;
            if (text != null && !text.isBlank()) {
                indexes.putIfAbsent(normalize(text), cell.getColumnIndex());
            }
        }
        return indexes;
    }

    private Map<ExcelField, Integer> matchColumns(List<ExcelField> fields, Map<String, Integer> columnIndexes) {
        Map<ExcelField, Integer> layout = new LinkedHashMap<>();
        List<String> missingRequired = new ArrayList<>();

        for (ExcelField field : fields) {
            Integer index = columnIndexes.get(normalize(field.header()));
            if (index == null) {
                if (field.required()) {
                    missingRequired.add(field.header());
                }
                continue;
            }
            layout.put(field, index);
        }
        if (!missingRequired.isEmpty()) {
            throw new ExcelParseException("File thiếu cột bắt buộc: " + String.join(", ", missingRequired));
        }
        return layout;
    }

    private <T> T bind(Row row, Class<T> type, Map<ExcelField, Integer> layout) {
        T target = instantiate(type);
        for (Map.Entry<ExcelField, Integer> entry : layout.entrySet()) {
            ExcelField field = entry.getKey();
            Cell cell = row.getCell(entry.getValue(), Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            Object value = ExcelCellConverter.convert(cell, field.type(), field.header());
            if (value == null) {
                if (field.required()) {
                    throw new ExcelCellException("thiếu giá trị ở cột bắt buộc '" + field.header() + "'");
                }
                continue;
            }
            field.write(target, value);
        }
        return target;
    }

    private <T> T instantiate(Class<T> type) {
        try {
            var constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(
                type.getSimpleName() + " cần có constructor rỗng để ExcelMapper tạo đối tượng khi đọc file",
                exception);
        }
    }

    private boolean isBlank(Row row) {
        if (row == null) {
            return true;
        }
        for (Cell cell : row) {
            if (cell.getCellType() != CellType.BLANK) {
                return false;
            }
        }
        return true;
    }

    private static String normalize(String header) {
        return header.trim().toLowerCase(Locale.ROOT);
    }

    public <T> byte[] write(List<T> rows, Class<T> type, String sheetName) {
        List<ExcelField> fields = describe(type);

        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(sheetName);
            CellStyle headerStyle = headerStyle(workbook);
            CellStyle dateStyle = dateStyle(workbook);

            Row headerRow = sheet.createRow(0);
            for (int column = 0; column < fields.size(); column++) {
                Cell cell = headerRow.createCell(column);
                cell.setCellValue(fields.get(column).header());
                cell.setCellStyle(headerStyle);
            }

            for (int index = 0; index < rows.size(); index++) {
                Row row = sheet.createRow(index + 1);
                T item = rows.get(index);
                for (int column = 0; column < fields.size(); column++) {
                    fill(row.createCell(column), fields.get(column).read(item), dateStyle);
                }
            }

            for (int column = 0; column < fields.size(); column++) {
                sheet.autoSizeColumn(column);
            }
            sheet.createFreezePane(0, 1);

            workbook.write(output);
            return output.toByteArray();

        } catch (IOException exception) {
            throw new ExcelParseException("Không ghi được file Excel: " + exception.getMessage(), exception);
        }
    }

    private void fill(Cell cell, Object value, CellStyle dateStyle) {
        switch (value) {
            case null -> cell.setBlank();
            case Number number -> cell.setCellValue(number.doubleValue());
            case Boolean bool -> cell.setCellValue(bool);
            case LocalDateTime dateTime -> {
                cell.setCellValue(dateTime);
                cell.setCellStyle(dateStyle);
            }
            case LocalDate date -> {
                cell.setCellValue(date);
                cell.setCellStyle(dateStyle);
            }
            case Enum<?> enumValue -> cell.setCellValue(enumValue.name().toLowerCase(Locale.ROOT));
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

    private CellStyle dateStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat(DATE_TIME_PATTERN));
        return style;
    }
}
