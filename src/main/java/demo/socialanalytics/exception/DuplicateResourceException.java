package demo.socialanalytics.exception;

// Ném khi tạo trùng bản ghi đã tồn tại (vd import lại cùng một bài viết); ánh xạ sang 409.
public class DuplicateResourceException extends RuntimeException {
    public DuplicateResourceException(String message) {
        super(message);
    }
}
