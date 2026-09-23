package demo.socialanalytics.excel;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

// Suy ra danh sách cột của MỘT LỚP BẤT KỲ bằng Reflection, không cần annotation nào.
//
// Khác với ExcelMapper (đòi @ExcelColumn): ở đây model nào cũng xuất được, kể cả lớp của thư viện
// ngoài hay lớp sinh tự động từ XSD — những thứ không sửa được để gắn annotation vào.
//
// Thứ tự cột:
//   - record  -> đúng thứ tự khai báo component (Java giữ lại thông tin này)
//   - lớp thường -> thứ tự khai báo field, vì getXxx() do Reflection trả về KHÔNG có thứ tự
//     ổn định giữa các lần chạy JVM; xếp theo field thì file xuất ra lần nào cũng như nhau.
public final class ModelIntrospector {

    // Không phải thuộc tính dữ liệu, bỏ qua.
    private static final Set<String> IGNORED = Set.of("class", "hashCode", "toString");

    private static final Map<Class<?>, List<ModelProperty>> CACHE = new ConcurrentHashMap<>();

    private ModelIntrospector() {
    }

    public static List<ModelProperty> describe(Class<?> type) {
        return CACHE.computeIfAbsent(type, ModelIntrospector::scan);
    }

    private static List<ModelProperty> scan(Class<?> type) {
        List<ModelProperty> properties = type.isRecord() ? scanRecord(type) : scanBean(type);
        if (properties.isEmpty()) {
            throw new IllegalStateException(
                type.getSimpleName() + " không có thuộc tính nào đọc được để xuất");
        }
        return List.copyOf(properties);
    }

    // record: mỗi component có sẵn một accessor cùng tên.
    private static List<ModelProperty> scanRecord(Class<?> type) {
        List<ModelProperty> properties = new ArrayList<>();
        for (RecordComponent component : type.getRecordComponents()) {
            properties.add(ModelProperty.ofGetter(
                component.getName(), toHeader(component.getName()), component.getAccessor()));
        }
        return properties;
    }

    // Lớp thường: duyệt theo field (để có thứ tự ổn định), mỗi field tìm getter tương ứng.
    // Không có getter thì đọc thẳng field.
    private static List<ModelProperty> scanBean(Class<?> type) {
        List<ModelProperty> properties = new ArrayList<>();
        for (Class<?> current = type; current != null && current != Object.class;
             current = current.getSuperclass()) {

            for (Field field : current.getDeclaredFields()) {
                if (field.isSynthetic() || Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                String name = field.getName();
                if (IGNORED.contains(name)) {
                    continue;
                }
                Method getter = findGetter(type, name, field.getType());
                properties.add(getter != null
                    ? ModelProperty.ofGetter(name, toHeader(name), getter)
                    : ModelProperty.ofField(name, toHeader(name), field));
            }
        }
        return properties;
    }

    private static Method findGetter(Class<?> type, String fieldName, Class<?> fieldType) {
        String capitalized = Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
        // boolean dùng isXxx() thay vì getXxx().
        List<String> candidates = (fieldType == boolean.class || fieldType == Boolean.class)
            ? List.of("is" + capitalized, "get" + capitalized)
            : List.of("get" + capitalized);

        for (String candidate : candidates) {
            try {
                Method method = type.getMethod(candidate);
                if (method.getParameterCount() == 0 && method.getReturnType() != void.class) {
                    return method;
                }
            } catch (NoSuchMethodException ignored) {
                // thử tên kế tiếp
            }
        }
        return null;
    }

    // camelCase -> "Camel case", để tiêu đề cột đọc được mà không phải khai tay từng cái.
    static String toHeader(String name) {
        StringBuilder header = new StringBuilder();
        for (int index = 0; index < name.length(); index++) {
            char character = name.charAt(index);
            if (index > 0 && Character.isUpperCase(character)) {
                header.append(' ').append(Character.toLowerCase(character));
            } else if (index == 0) {
                header.append(Character.toUpperCase(character));
            } else {
                header.append(character);
            }
        }
        return header.toString();
    }
}
