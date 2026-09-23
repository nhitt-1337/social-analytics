package demo.socialanalytics.repository.projection;

import demo.socialanalytics.entity.Platform;

public record PostIdentity(Platform platform, String externalId) {
}
