package demo.socialanalytics.exception;

// Giá trị tham số sai (vd platform lạ); ánh xạ sang 400.
public class InvalidRequestParameterException extends RuntimeException {
    public InvalidRequestParameterException(String message) {
        super(message);
    }
}
