package demo.socialanalytics.controller;

import demo.socialanalytics.entity.Platform;
import demo.socialanalytics.entity.Post;
import demo.socialanalytics.entity.SocialMetric;
import demo.socialanalytics.entity.User;
import demo.socialanalytics.repository.PostRepository;
import demo.socialanalytics.repository.SocialMetricRepository;
import demo.socialanalytics.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
// Giả lập sẵn người dùng thay vì đi qua luồng OAuth2 thật
@WithMockUser
class MetricControllerIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PostRepository posts;
    @Autowired SocialMetricRepository metrics;

    private Post post;

    @BeforeEach
    void setUp() {
        metrics.deleteAll();
        posts.deleteAll();
        users.deleteAll();

        User user = new User();
        user.setEmail("admin@example.com");
        user.setFullName("Quản trị viên");
        user = users.save(user);

        post = new Post();
        post.setUser(user);
        post.setPlatform(Platform.FACEBOOK);
        post.setExternalId("fb-metric");
        post = posts.save(post);
    }

    private SocialMetric saveMetric(int likes, LocalDateTime collectedAt) {
        SocialMetric metric = new SocialMetric();
        metric.setPost(post);
        metric.setLikes(likes);
        metric.setShares(likes / 2);
        metric.setComments(1);
        metric.setFollowers(1000 + likes);
        metric.setCollectedAt(collectedAt);
        return metrics.save(metric);
    }

    private String body(long postId, int likes) {
        return """
            {"postId":%d,"likes":%d,"shares":5,"comments":2,"followers":1200}
            """.formatted(postId, likes);
    }

    @Test
    void recordReturns201AndDefaultsCollectedAtToNow() throws Exception {
        mvc.perform(post("/api/v1/metrics").contextPath("/api/v1").servletPath("/metrics").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body(post.getId(), 100)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.postId").value(post.getId()))
            .andExpect(jsonPath("$.likes").value(100))
            // Không gửi collectedAt -> entity tự điền thời điểm hiện tại.
            .andExpect(jsonPath("$.collectedAt").isNotEmpty());
    }

    // Số liệu mới nhất phải xuất hiện ngay trên response của bài viết.
    @Test
    void latestMetricShowsUpOnPostDetail() throws Exception {
        saveMetric(10, LocalDateTime.now().minusDays(2));
        saveMetric(50, LocalDateTime.now().minusHours(1));

        mvc.perform(get("/api/v1/posts/" + post.getId())
                .contextPath("/api/v1").servletPath("/posts/" + post.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.latestMetric.likes").value(50));
    }

    @Test
    void listByPostReturnsNewestFirst() throws Exception {
        saveMetric(10, LocalDateTime.now().minusDays(3));
        saveMetric(30, LocalDateTime.now().minusDays(1));

        mvc.perform(get("/api/v1/metrics").contextPath("/api/v1").servletPath("/metrics")
                .param("postId", post.getId().toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(2))
            .andExpect(jsonPath("$.data[0].likes").value(30));
    }

    // Chuỗi thời gian cho Chart.js: tăng dần theo thời điểm đo.
    @Test
    void timeSeriesReturnsAscendingOrder() throws Exception {
        saveMetric(10, LocalDateTime.now().minusDays(3));
        saveMetric(30, LocalDateTime.now().minusDays(1));

        mvc.perform(get("/api/v1/metrics/series").contextPath("/api/v1").servletPath("/metrics/series")
                .param("postId", post.getId().toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(2))
            .andExpect(jsonPath("$.data[0].likes").value(10))
            .andExpect(jsonPath("$.data[1].likes").value(30));
    }

    @Test
    void recordForUnknownPostReturns404() throws Exception {
        mvc.perform(post("/api/v1/metrics").contextPath("/api/v1").servletPath("/metrics").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body(999999L, 10)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.message").value("Không tìm thấy bài viết"));
    }

    @Test
    void negativeLikesReturns422() throws Exception {
        mvc.perform(post("/api/v1/metrics").contextPath("/api/v1").servletPath("/metrics").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"postId\":%d,\"likes\":-5,\"shares\":0,\"followers\":0}".formatted(post.getId())))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.error.fields.likes").isNotEmpty());
    }

    // Xoá bài viết phải xoá luôn lịch sử chỉ số (orphanRemoval).
    @Test
    void deletingPostCascadesToMetrics() throws Exception {
        saveMetric(10, LocalDateTime.now());

        mvc.perform(delete("/api/v1/posts/" + post.getId())
                .contextPath("/api/v1").servletPath("/posts/" + post.getId()).with(csrf()))
            .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/metrics").contextPath("/api/v1").servletPath("/metrics")
                .param("postId", post.getId().toString()))
            .andExpect(status().isNotFound());
    }
}
