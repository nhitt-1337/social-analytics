package demo.socialanalytics.excel;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Đánh dấu một field sẽ ứng với một cột trong file Excel.
// ExcelMapper đọc annotation này bằng Reflection nên thêm/bớt/đổi thứ tự cột chỉ cần sửa ở đây,
// không phải đụng vào code đọc/ghi file.
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ExcelColumn {

    // Tiêu đề cột hiển thị ở dòng đầu file. Khi đọc, cột được tìm theo tên này
    // (không phân biệt hoa thường) nên người dùng có thể đảo thứ tự cột trong file.
    String header();

    // Thứ tự cột lúc ghi file. Số nhỏ đứng trước.
    int order();

    // Bắt buộc có giá trị khi import. Ô trống -> báo lỗi dòng đó.
    boolean required() default false;
}
