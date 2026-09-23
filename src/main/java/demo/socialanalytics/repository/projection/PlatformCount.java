package demo.socialanalytics.repository.projection;

import demo.socialanalytics.entity.Platform;

public record PlatformCount(Platform platform, long postCount, long accountCount) {
}
