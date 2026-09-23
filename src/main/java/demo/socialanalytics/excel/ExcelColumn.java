package demo.socialanalytics.excel;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Đánh dấu một field sẽ ứng với một cột trong file Excel.
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ExcelColumn {

    // Tiêu đề cột hiển thị ở dòng đầu file.
    String header();

    // Thứ tự cột lúc ghi file. Số nhỏ đứng trước.
    int order();

    // Bắt buộc có giá trị khi import. Ô trống -> báo lỗi dòng đó.
    boolean required() default false;
}
