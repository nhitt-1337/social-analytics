package demo.socialanalytics.controller;

import demo.socialanalytics.entity.Platform;
import demo.socialanalytics.entity.Post;
import demo.socialanalytics.entity.Role;
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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
// Mọi endpoint đều yêu cầu đăng nhập; test này nhắm vào hành vi của API nên giả lập sẵn
// một người dùng thay vì đi qua luồng OAuth2 thật.
@WithMockUser
class PostControllerIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PostRepository posts;
    @Autowired SocialMetricRepository metrics;

    private User admin;

    @BeforeEach
    void setUp() {
        metrics.deleteAll();
        posts.deleteAll();
        users.deleteAll();

        admin = new User();
        admin.setEmail("admin@example.com");
        admin.setFullName("Quản trị viên");
        admin.setRole(Role.ADMIN);
        admin = users.save(admin);
    }

    private Post savePost(Platform platform, String externalId) {
        Post post = new Post();
        post.setUser(admin);
        post.setPlatform(platform);
        post.setExternalId(externalId);
        post.setContent("Nội dung " + externalId);
        return posts.save(post);
    }

    private String body(String platform, String externalId) {
        return """
            {"userId":%d,"platform":"%s","externalId":"%s","content":"Bài viết mẫu","url":"https://example.com/p"}
            """.formatted(admin.getId(), platform, externalId);
    }

    @Test
    void createReturns201AndPersistsPost() throws Exception {
        mvc.perform(post("/api/v1/posts").contextPath("/api/v1").servletPath("/posts").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body("facebook", "fb-001")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.platform").value("facebook"))
            .andExpect(jsonPath("$.externalId").value("fb-001"))
            .andExpect(jsonPath("$.user.email").value("admin@example.com"))
            // Bài mới chưa crawl lần nào -> chưa có số liệu.
            .andExpect(jsonPath("$.latestMetric").doesNotExist());
    }

    // Cặp (platform, externalId) là duy nhất -> import lại cùng một bài không tạo bản ghi trùng.
    @Test
    void createDuplicateExternalIdReturns409() throws Exception {
        savePost(Platform.FACEBOOK, "fb-dup");

        mvc.perform(post("/api/v1/posts").contextPath("/api/v1").servletPath("/posts").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body("facebook", "fb-dup")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("CONFLICT"));
    }

    // Cùng externalId nhưng khác nền tảng thì hợp lệ.
    @Test
    void sameExternalIdOnAnotherPlatformIsAllowed() throws Exception {
        savePost(Platform.FACEBOOK, "same-id");

        mvc.perform(post("/api/v1/posts").contextPath("/api/v1").servletPath("/posts").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body("twitter", "same-id")))
            .andExpect(status().isCreated());
    }

    @Test
    void listFiltersByPlatformAndPaginates() throws Exception {
        savePost(Platform.FACEBOOK, "fb-1");
        savePost(Platform.FACEBOOK, "fb-2");
        savePost(Platform.TWITTER, "tw-1");

        mvc.perform(get("/api/v1/posts").contextPath("/api/v1").servletPath("/posts")
                .param("platform", "facebook").param("page", "1").param("limit", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(2))
            .andExpect(jsonPath("$.page").value(1))
            .andExpect(jsonPath("$.limit").value(1))
            .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void unknownPlatformReturns400() throws Exception {
        mvc.perform(get("/api/v1/posts").contextPath("/api/v1").servletPath("/posts")
                .param("platform", "tiktok"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
    }

    @Test
    void invalidBodyReturns422WithFields() throws Exception {
        mvc.perform(post("/api/v1/posts").contextPath("/api/v1").servletPath("/posts").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"platform\":\"\",\"externalId\":\"\"}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.error.code").value("VALIDATION"))
            .andExpect(jsonPath("$.error.fields.userId").isNotEmpty())
            .andExpect(jsonPath("$.error.fields.platform").isNotEmpty());
    }

    @Test
    void getUnknownPostReturns404() throws Exception {
        mvc.perform(get("/api/v1/posts/999999").contextPath("/api/v1").servletPath("/posts/999999"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.message").value("Không tìm thấy bài viết"));
    }

    @Test
    void updateThenDeleteWorks() throws Exception {
        Long id = savePost(Platform.FACEBOOK, "fb-edit").getId();

        mvc.perform(put("/api/v1/posts/" + id).contextPath("/api/v1").servletPath("/posts/" + id).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body("twitter", "tw-edited")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.platform").value("twitter"))
            .andExpect(jsonPath("$.externalId").value("tw-edited"));

        mvc.perform(delete("/api/v1/posts/" + id).contextPath("/api/v1").servletPath("/posts/" + id).with(csrf()))
            .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/posts/" + id).contextPath("/api/v1").servletPath("/posts/" + id))
            .andExpect(status().isNotFound());
    }
}
