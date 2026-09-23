package demo.socialanalytics.soap;

// Không lấy được tỷ giá — do nhà cung cấp từ chối hoặc không gọi tới được.
// Ánh xạ sang 503 ở GlobalExceptionHandler: đây là lỗi của dịch vụ NGOÀI, không phải lỗi
// của người gọi, nên trả 400 là đổ oan cho client.
public class ExchangeRateUnavailableException extends RuntimeException {
    public ExchangeRateUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
