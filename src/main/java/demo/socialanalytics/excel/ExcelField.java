package demo.socialanalytics.excel;

import java.lang.reflect.Field;

public record ExcelField(Field field, String header, int order, boolean required) {
    public Object read(Object target) {
        try {
            return field.get(target);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Không đọc được field " + field.getName(), exception);
        }
    }

    public void write(Object target, Object value) {
        try {
            field.set(target, value);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Không ghi được field " + field.getName(), exception);
        }
    }

    public Class<?> type() {
        return field.getType();
    }

    public String name() {
        return field.getName();
    }
}
