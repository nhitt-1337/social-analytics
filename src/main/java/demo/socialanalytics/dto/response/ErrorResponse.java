package demo.socialanalytics.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

// Khuôn lỗi thống nhất cho toàn API: { "error": { "code", "message", "fields"? } }.
public record ErrorResponse(ErrorBody error) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ErrorBody(String code, String message, Map<String, String> fields) {
    }

    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(new ErrorBody(code, message, null));
    }

    public static ErrorResponse of(String code, String message, Map<String, String> fields) {
        return new ErrorResponse(new ErrorBody(code, message, fields));
    }
}
