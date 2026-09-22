package demo.socialanalytics.controller;

import demo.socialanalytics.entity.*;
import demo.socialanalytics.repository.*;
import demo.socialanalytics.service.SocialMetricsUpdateJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// API trạng thái job + màn hình "Last updated time".
@SpringBootTest(properties = {
    "social.crawl.enabled=true",
    "social.crawl.initial-delay=PT24H",
    "social.crawl.mock.latency-ms=0",
    "social.crawl.mock.failure-rate=0"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CrawlControllerIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired SocialMetricsUpdateJob job;
    @Autowired UserRepository users;
    @Autowired PostRepository posts;
    @Autowired SocialMetricRepository metrics;
    @Autowired CrawlRunRepository crawlRuns;

    private User admin;

    @BeforeEach
    void setUp() {
        metrics.deleteAll();
        posts.deleteAll();
        users.deleteAll();
        crawlRuns.deleteAll();

        admin = new User();
        admin.setEmail("admin@example.com");
        admin.setFullName("Quản trị viên");
        admin.setRole(Role.ADMIN);
        admin = users.save(admin);
    }

    private void savePost(String externalId) {
        Post post = new Post();
        post.setUser(admin);
        post.setPlatform(Platform.FACEBOOK);
        post.setExternalId(externalId);
        posts.save(post);
    }

    private MockHttpServletRequestBuilder api(String method, String path) {
        var builder = "post".equals(method) ? post("/api/v1" + path) : get("/api/v1" + path);
        return builder.contextPath("/api/v1").servletPath(path);
    }

    // ----- bảo mật -----

    @Test
    void chuaDangNhapThiKhongXemDuocTrangThai() throws Exception {
        mvc.perform(api("get", "/crawl/last-run").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void chayNgayThieuTokenCsrfThiBiChan() throws Exception {
        mvc.perform(api("post", "/crawl/run").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isForbidden());

        assertThat(crawlRuns.findAll()).isEmpty();
    }

    // ----- trạng thái -----

    @Test
    @WithMockUser
    void chuaChayLanNaoThiTraVe204() throws Exception {
        mvc.perform(api("get", "/crawl/last-run").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser
    void traVeThongTinLanChayGanNhat() throws Exception {
        savePost("fb-1");
        savePost("fb-2");
        job.runOnce();

        mvc.perform(api("get", "/crawl/last-run").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("SUCCESS"))
            .andExpect(jsonPath("$.totalAccounts").value(1))
            .andExpect(jsonPath("$.totalPosts").value(2))
            .andExpect(jsonPath("$.succeededPosts").value(2))
            .andExpect(jsonPath("$.failedPosts").value(0))
            .andExpect(jsonPath("$.startedAt").exists())
            .andExpect(jsonPath("$.finishedAt").exists());
    }

    @Test
    @WithMockUser
    void lietKeCacLanChayGanDayMoiNhatTruoc() throws Exception {
        savePost("fb-1");
        job.runOnce();
        job.runOnce();

        mvc.perform(api("get", "/crawl/runs").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(2));
    }

    // ----- chạy ngay -----

    @Test
    @WithMockUser
    void chayNgayKemTokenThiCapNhatDuLieu() throws Exception {
        savePost("fb-1");

        mvc.perform(api("post", "/crawl/run").with(csrf()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("SUCCESS"))
            .andExpect(jsonPath("$.succeededPosts").value(1));

        assertThat(metrics.findAll()).hasSize(1);
    }

    // ----- màn hình "Last updated time" -----

    @Test
    @WithMockUser
    void dashboardBaoChuaChayLanNao() throws Exception {
        mvc.perform(api("get", "/dashboard").accept(MediaType.TEXT_HTML))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Job chưa chạy lần nào")));
    }

    @Test
    @WithMockUser
    void dashboardHienThoiDiemCapNhatGanNhat() throws Exception {
        savePost("fb-1");
        savePost("fb-2");
        job.runOnce();

        mvc.perform(api("get", "/dashboard").accept(MediaType.TEXT_HTML))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Cập nhật lần cuối")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("SUCCESS")))
            .andExpect(content().string(
                org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Job chưa chạy lần nào"))));
    }
}
