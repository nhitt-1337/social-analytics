package demo.socialanalytics.exception;

import demo.socialanalytics.dto.response.ErrorResponse;
import demo.socialanalytics.excel.ExcelParseException;
import demo.socialanalytics.soap.ExchangeRateUnavailableException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.MismatchedInputException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

// Điều phối toàn bộ lỗi về một khuôn { "error": { code, message, fields? } }.
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException exception) {
        return build(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ErrorResponse> handleDuplicate(DuplicateResourceException exception) {
        return build(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(InvalidRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleInvalidParameter(InvalidRequestParameterException exception) {
        return build(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    // Chốt chặn cuối của DB (unique constraint) khi hai request cùng tạo một bản ghi.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException exception) {
        return build(HttpStatus.CONFLICT, "Dữ liệu bị trùng với bản ghi đã tồn tại");
    }

    // Body JSON không parse được. Bắt ở đây để không bị forward sang /error rồi mất thông tin lỗi.
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException exception) {
        if (exception.getCause() instanceof MismatchedInputException mismatch) {
            String field = mismatch.getPath().stream()
                .map(JacksonException.Reference::getPropertyName)
                .filter(name -> name != null && !name.isBlank())
                .reduce((parent, child) -> parent + "." + child)
                .orElse(null);
            if (field != null) {
                return buildValidation(new LinkedHashMap<>(
                    Map.of(field, field + " " + describeType(mismatch.getTargetType()))));
            }
        }
        return build(HttpStatus.BAD_REQUEST, "Body của request không hợp lệ hoặc không phải JSON đúng định dạng");
    }

    // Dịch vụ tỷ giá bên ngoài không dùng được -> 503, KHÔNG phải 400 hay 500.
    @ExceptionHandler(ExchangeRateUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleExchangeRateUnavailable(
        ExchangeRateUnavailableException exception) {
        return build(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
    }

    // File Excel hỏng, sai sheet hoặc thiếu cột bắt buộc -> lỗi của dữ liệu gửi lên, không phải lỗi server.
    @ExceptionHandler(ExcelParseException.class)
    public ResponseEntity<ErrorResponse> handleExcelParse(ExcelParseException exception) {
        return build(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErrorResponse> handleMissingPart(MissingServletRequestPartException exception) {
        return build(HttpStatus.BAD_REQUEST, "Thiếu phần '" + exception.getRequestPartName() + "' trong request");
    }

    // Trần dung lượng cấu hình ở spring.servlet.multipart.max-file-size.
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleUploadTooLarge(MaxUploadSizeExceededException exception) {
        return build(HttpStatus.PAYLOAD_TOO_LARGE, "File tải lên vượt quá dung lượng cho phép");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        return build(HttpStatus.BAD_REQUEST,
            "Tham số '" + exception.getName() + "' " + describeType(exception.getRequiredType()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> fields = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors()
            .forEach(error -> fields.putIfAbsent(error.getField(), fieldErrorMessage(error)));
        return buildValidation(fields);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException exception) {
        Map<String, String> fields = new LinkedHashMap<>();
        exception.getConstraintViolations().forEach(violation -> {
            String path = violation.getPropertyPath().toString();
            String param = path.contains(".") ? path.substring(path.lastIndexOf('.') + 1) : path;
            fields.putIfAbsent(param, violation.getMessage());
        });
        return buildValidation(fields);
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(ErrorResponse.of(status.name(), message));
    }

    private ResponseEntity<ErrorResponse> buildValidation(Map<String, String> fields) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
            .body(ErrorResponse.of("VALIDATION", "Dữ liệu không hợp lệ", fields));
    }

    private String fieldErrorMessage(FieldError error) {
        if ("typeMismatch".equals(error.getCode())) {
            return error.getField() + " " + describeType(requiredType(error));
        }
        return error.getDefaultMessage();
    }

    private Class<?> requiredType(FieldError error) {
        try {
            return error.unwrap(TypeMismatchException.class).getRequiredType();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    // Dịch kiểu Java sang mô tả cho người dùng cuối, không lộ tên lớp ("Long", "LocalDateTime").
    private String describeType(Class<?> type) {
        if (type == null) {
            return "có giá trị không đúng định dạng";
        }
        if (type == Long.class || type == Integer.class || type == long.class || type == int.class) {
            return "phải là số nguyên";
        }
        if (Number.class.isAssignableFrom(type)) {
            return "phải là số";
        }
        if (type == LocalDate.class) {
            return "phải là ngày theo định dạng YYYY-MM-DD";
        }
        if (type == LocalDateTime.class) {
            return "phải là ngày giờ theo định dạng YYYY-MM-DDTHH:mm:ss";
        }
        if (type == Boolean.class || type == boolean.class) {
            return "phải là true hoặc false";
        }
        return "có giá trị không đúng định dạng";
    }
}
