package demo.socialanalytics.exception;

// resource là tên tài nguyên tiếng Việt (vd "bài viết") để ghép thẳng vào message.
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String resource) {
        super("Không tìm thấy " + resource);
    }
}
