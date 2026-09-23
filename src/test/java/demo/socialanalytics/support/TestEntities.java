package demo.socialanalytics.support;

import demo.socialanalytics.entity.*;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

// Dựng sẵn entity cho test.
public final class TestEntities {

    private TestEntities() {
    }

    public static User user(Long id, String email) {
        User user = new User();
        set(user, "id", id);
        user.setEmail(email);
        user.setFullName("Người dùng " + id);
        user.setRole(Role.ADMIN);
        stampCreated(user);
        return user;
    }

    public static Post post(Long id, User user, Platform platform, String externalId) {
        Post post = new Post();
        set(post, "id", id);
        post.setUser(user);
        post.setPlatform(platform);
        post.setExternalId(externalId);
        post.setContent("Nội dung " + externalId);
        post.setUrl("https://example.com/" + externalId);
        stampCreated(post);
        return post;
    }

    public static SocialMetric metric(Long id, Post post, int likes, LocalDateTime collectedAt) {
        SocialMetric metric = new SocialMetric();
        set(metric, "id", id);
        metric.setPost(post);
        metric.setLikes(likes);
        metric.setShares(likes / 2);
        metric.setComments(likes / 4);
        metric.setFollowers(1_000);
        metric.setCollectedAt(collectedAt);
        set(metric, "createdAt", collectedAt);
        return metric;
    }

    private static void stampCreated(Object entity) {
        LocalDateTime now = LocalDateTime.now();
        set(entity, "createdAt", now);
        set(entity, "updatedAt", now);
    }

    private static void set(Object target, String field, Object value) {
        ReflectionTestUtils.setField(target, field, value);
    }
}
