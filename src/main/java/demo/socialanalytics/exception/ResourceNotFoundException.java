package demo.socialanalytics.exception;

public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String resource) {
        super("Không tìm thấy " + resource);
    }
}
