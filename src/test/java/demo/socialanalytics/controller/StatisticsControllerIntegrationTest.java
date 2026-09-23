package demo.socialanalytics.controller;

import demo.socialanalytics.entity.*;
import demo.socialanalytics.repository.*;
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

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StatisticsControllerIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired StatisticsService statisticsService;
    @Autowired UserRepository users;
    @Autowired PostRepository posts;
    @Autowired SocialMetricRepository metrics;
    @Autowired PlatformSummaryRepository summaries;
    @Autowired DeadLetterRepository deadLetters;

    private User admin;

    @BeforeEach
    void setUp() {
        metrics.deleteAll();
        posts.deleteAll();
        summaries.deleteAll();
        deadLetters.deleteAll();
        users.deleteAll();

        admin = new User();
        admin.setEmail("admin@example.com");
        admin.setFullName("Quản trị viên");
        admin.setRole(Role.ADMIN);
        admin = users.save(admin);
    }

    private MockHttpServletRequestBuilder api(String path) {
        return get("/api/v1" + path).contextPath("/api/v1").servletPath(path)
            .accept(MediaType.APPLICATION_JSON);
    }

    private void savePost(Platform platform, String externalId) {
        Post post = new Post();
        post.setUser(admin);
        post.setPlatform(platform);
        post.setExternalId(externalId);
        posts.save(post);
    }

    @Test
    void chuaDangNhapKhongXemDuocThongKe() throws Exception {
        mvc.perform(api("/statistics")).andExpect(status().isUnauthorized());
    }

    @Test
    void chuaDangNhapKhongXemDuocDlq() throws Exception {
        mvc.perform(api("/statistics/dead-letters")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void traVeThongKeTheoTungNenTang() throws Exception {
        savePost(Platform.FACEBOOK, "fb-1");
        savePost(Platform.FACEBOOK, "fb-2");
        savePost(Platform.TWITTER, "tw-1");
        statisticsService.refresh();

        mvc.perform(api("/statistics"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(Platform.values().length))
            .andExpect(jsonPath("$.data[?(@.platform == 'facebook')].postCount").value(2))
            .andExpect(jsonPath("$.data[?(@.platform == 'twitter')].postCount").value(1))
            .andExpect(jsonPath("$.data[0].updatedAt").exists());
    }

    @Test
    @WithMockUser
    void chuaTinhLanNaoThiTraVeDanhSachRong() throws Exception {
        mvc.perform(api("/statistics"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @WithMockUser
    void lietKeMessageDaVaoDeadLetterQueue() throws Exception {
        DeadLetter letter = new DeadLetter();
        letter.setSourceQueue("import.completed");
        letter.setMessageId("ID:test-1");
        letter.setPayload("{\"imported\":3}");
        letter.setFailureCause("Delivery[4] exceeds redelivery policy limit");
        letter.setReceivedAt(LocalDateTime.now());
        deadLetters.save(letter);

        mvc.perform(api("/statistics/dead-letters"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].sourceQueue").value("import.completed"))
            .andExpect(jsonPath("$.data[0].failureCause").value("Delivery[4] exceeds redelivery policy limit"))
            .andExpect(jsonPath("$.data[0].payload").value("{\"imported\":3}"));
    }
}
