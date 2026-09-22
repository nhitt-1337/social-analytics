package demo.socialanalytics.client;

// Số liệu đọc được từ nhà cung cấp tại một thời điểm.
// Là bản ghi thuần, không dính JPA — client không cần biết gì về cách mình lưu trữ.
public record SocialMetricsSnapshot(int likes, int shares, int comments, int followers) {
}
