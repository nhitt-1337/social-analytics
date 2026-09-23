package demo.socialanalytics.soap;

// Không lấy được tỷ giá — do nhà cung cấp từ chối hoặc không gọi tới được.
public class ExchangeRateUnavailableException extends RuntimeException {
    public ExchangeRateUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
