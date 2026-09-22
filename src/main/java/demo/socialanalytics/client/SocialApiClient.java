package demo.socialanalytics.client;

import demo.socialanalytics.entity.Post;

// Cổng ra tới API của Facebook / X.
//
// Để là interface vì bản hiện tại chỉ là dữ liệu giả; khi nối API thật chỉ cần thêm một
// implementation đọc access token từ oauth2_authorized_clients, phần job không phải sửa gì.
public interface SocialApiClient {

    // Ném SocialApiException khi không lấy được số liệu.
    SocialMetricsSnapshot fetchMetrics(Post post);
}
