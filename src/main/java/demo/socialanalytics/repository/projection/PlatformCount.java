package demo.socialanalytics.repository.projection;

import demo.socialanalytics.entity.Platform;

// Kết quả đếm gộp theo nền tảng, dùng để dựng lại bảng thống kê tổng hợp.
public record PlatformCount(Platform platform, long postCount, long accountCount) {
}
