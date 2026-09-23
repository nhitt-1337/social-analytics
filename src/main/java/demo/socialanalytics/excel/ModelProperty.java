package demo.socialanalytics.excel;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

// Một thuộc tính đọc được của model, không quan tâm nó lộ ra qua getter hay qua field.
//
// Ưu tiên GỌI METHOD (getter) thay vì đọc thẳng field: getter mới là phần công khai của lớp,
// và nhiều lớp tính toán giá trị trong getter chứ không lưu sẵn (vd Post.getPlatform().getSlug(),
// hay một trường "fullName" ghép từ hai field).
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
            // Getter tự ném lỗi (vd lazy loading ngoài transaction). Nói rõ thuộc tính nào hỏng,
            // nếu không thì chỉ thấy một LazyInitializationException không biết từ đâu ra.
            throw new IllegalStateException(
                "Lỗi khi đọc thuộc tính '" + name + "': " + exception.getCause(), exception.getCause());
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Không đọc được thuộc tính '" + name + "'", exception);
        }
    }
}
