package demo.socialanalytics.excel;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

// Ưu tiên getter vì nhiều lớp tính giá trị trong getter
public record ModelProperty(String name, String header, Method getter, Field field, Class<?> type) {
    static ModelProperty ofGetter(String name, String header, Method getter) {
        getter.setAccessible(true);
        return new ModelProperty(name, header, getter, null, getter.getReturnType());
    }

    static ModelProperty ofField(String name, String header, Field field) {
        field.setAccessible(true);
        return new ModelProperty(name, header, null, field, field.getType());
    }

    public Object read(Object target) {
        try {
            return getter != null ? getter.invoke(target) : field.get(target);
        } catch (InvocationTargetException exception) {
            throw new IllegalStateException(
                "Lỗi khi đọc thuộc tính '" + name + "': " + exception.getCause(), exception.getCause());
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Không đọc được thuộc tính '" + name + "'", exception);
        }
    }
}
