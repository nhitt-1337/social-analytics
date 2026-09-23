package demo.socialanalytics.client;

import demo.socialanalytics.entity.Post;

// Cổng ra tới API của Facebook / X.
public interface SocialApiClient {

    // Ném SocialApiException khi không lấy được số liệu.
    SocialMetricsSnapshot fetchMetrics(Post post);
}
