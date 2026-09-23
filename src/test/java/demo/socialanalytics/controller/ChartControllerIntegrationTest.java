package demo.socialanalytics.controller;

import demo.socialanalytics.entity.*;
import demo.socialanalytics.repository.*;
import demo.socialanalytics.service.ChartDataService;
import demo.socialanalytics.service.StatisticsService;
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

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser
class ChartControllerIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired StatisticsService statisticsService;
    @Autowired UserRepository users;
    @Autowired PostRepository posts;
    @Autowired SocialMetricRepository metrics;
    @Autowired PlatformSummaryRepository summaries;

    private User owner;

    @BeforeEach
    void setUp() {
        metrics.deleteAll();
        posts.deleteAll();
        summaries.deleteAll();
        users.deleteAll();

        owner = new User();
        owner.setEmail("a@example.com");
        owner.setFullName("Chủ tài khoản");
        owner.setRole(Role.ADMIN);
        owner = users.save(owner);
    }

    private MockHttpServletRequestBuilder chartData() {
        return get("/api/v1/chart-data").contextPath("/api/v1").servletPath("/chart-data")
            .accept(MediaType.APPLICATION_JSON);
    }

    private Post savePost(Platform platform, String externalId) {
        Post post = new Post();
        post.setUser(owner);
        post.setPlatform(platform);
        post.setExternalId(externalId);
        return posts.save(post);
    }

    private void saveMetric(Post post, int likes, LocalDateTime collectedAt) {
        SocialMetric metric = new SocialMetric();
        metric.setPost(post);
        metric.setLikes(likes);
        metric.setShares(likes / 2);
        metric.setComments(likes / 4);
        metric.setFollowers(1_000);
        metric.setCollectedAt(collectedAt);
        metrics.save(metric);
    }

    @Test
    void traVeDuLieuDaSapSanChoChartJs() throws Exception {
        Post post = savePost(Platform.FACEBOOK, "fb-1");
        saveMetric(post, 100, LocalDate.now().minusDays(1).atTime(9, 0));
        saveMetric(post, 250, LocalDate.now().atTime(9, 0));
        statisticsService.refresh();

        mvc.perform(chartData())
            .andExpect(status().isOk())
            // labels và mỗi series cùng số phần tử, cùng thứ tự — đúng dạng Chart.js cần.
            .andExpect(jsonPath("$.labels.length()").value(2))
            .andExpect(jsonPath("$.likes.length()").value(2))
            .andExpect(jsonPath("$.likes[0]").value(100))
            .andExpect(jsonPath("$.likes[1]").value(250))
            .andExpect(jsonPath("$.shares[1]").value(125))
            .andExpect(jsonPath("$.platforms.length()").value(Platform.values().length))
            .andExpect(jsonPath("$.generatedAt").exists());
    }

    @Test
    void chuaCoSoLieuThiTraVeCacMangRong() throws Exception {
        mvc.perform(chartData())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.labels").isEmpty())
            .andExpect(jsonPath("$.likes").isEmpty());
    }

    @Test
    void locTheoNenTang() throws Exception {
        saveMetric(savePost(Platform.FACEBOOK, "fb-1"), 100, LocalDate.now().atTime(9, 0));
        saveMetric(savePost(Platform.TWITTER, "tw-1"), 40, LocalDate.now().atTime(9, 0));

        mvc.perform(chartData().param("platform", "twitter"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.likes[0]").value(40));
    }

    @Test
    void nenTangKhongHopLeThiTraVe400() throws Exception {
        mvc.perform(chartData().param("platform", "instagram"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.message")
                .value(org.hamcrest.Matchers.containsString("instagram")));
    }

    @Test
    void daysVuotTranThiTraVe400() throws Exception {
        mvc.perform(chartData().param("days", String.valueOf(ChartDataService.MAX_DAYS + 1)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
    }

    @Test
    void daysKhongPhaiSoThiTraVe400() throws Exception {
        mvc.perform(chartData().param("days", "nhieu"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.message")
                .value(org.hamcrest.Matchers.containsString("phải là số nguyên")));
    }
}
