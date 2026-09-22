package demo.socialanalytics.client;

// Gọi API nhà cung cấp thất bại (mạng lỗi, hết hạn token, bị giới hạn tần suất...).
// Job bắt lại theo từng bài, ghi nhận là "thất bại" rồi đi tiếp thay vì dừng cả lần chạy.
public class SocialApiException extends RuntimeException {
    public SocialApiException(String message) {
        super(message);
    }

    public SocialApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
