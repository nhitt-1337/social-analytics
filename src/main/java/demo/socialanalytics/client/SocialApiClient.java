package demo.socialanalytics.client;

import demo.socialanalytics.entity.Post;

public interface SocialApiClient {
    SocialMetricsSnapshot fetchMetrics(Post post);
}
