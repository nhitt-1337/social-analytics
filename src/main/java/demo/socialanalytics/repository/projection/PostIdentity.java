package demo.socialanalytics.repository.projection;

import demo.socialanalytics.entity.Platform;

// Cặp khoá định danh bài viết.
public record PostIdentity(Platform platform, String externalId) {
}
