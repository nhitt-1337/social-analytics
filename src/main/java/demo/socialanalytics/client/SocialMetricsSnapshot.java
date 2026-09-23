package demo.socialanalytics.client;

// Số liệu đọc được từ nhà cung cấp tại một thời điểm.
public record SocialMetricsSnapshot(int likes, int shares, int comments, int followers) {
}
