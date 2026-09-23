package demo.socialanalytics.messaging;

import demo.socialanalytics.entity.*;
import demo.socialanalytics.repository.*;
import demo.socialanalytics.service.PostImportService;
import demo.socialanalytics.service.StatisticsService;
import demo.socialanalytics.support.ExcelTestFiles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static java.time.Duration.ofSeconds;

@SpringBootTest
@ActiveProfiles("test")
class ImportMessagingIntegrationTest {
    @Autowired PostImportService importService;
    @Autowired StatisticsService statisticsService;
    @Autowired UserRepository users;
    @Autowired PostRepository posts;
    @Autowired SocialMetricRepository metrics;
    @Autowired PlatformSummaryRepository summaries;

    private User admin;

    @BeforeEach
    void setUp() {
        metrics.deleteAll();
        posts.deleteAll();
        summaries.deleteAll();
        users.deleteAll();

        admin = new User();
        admin.setEmail("admin@example.com");
        admin.setFullName("Quản trị viên");
        admin.setRole(Role.ADMIN);
        admin = users.save(admin);
    }

    private MultipartFile upload(List<List<Object>> rows) {
        return new MockMultipartFile("file", "posts.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            ExcelTestFiles.postsFile(rows));
    }

    @Test
    void importXongThiThongKeDuocTinhLai() {
        importService.importPosts(upload(List.of(
            List.of("facebook", "fb-1", "Bài 1", "", ""),
            List.of("facebook", "fb-2", "Bài 2", "", ""),
            List.of("twitter", "tw-1", "Bài 3", "", ""))), admin.getId());

        await().atMost(ofSeconds(10)).untilAsserted(() -> {
            var current = statisticsService.current();
            assertThat(current).hasSize(Platform.values().length);
            assertThat(current).filteredOn(s -> s.getPlatform() == Platform.FACEBOOK)
                .singleElement()
                .satisfies(s -> {
                    assertThat(s.getPostCount()).isEqualTo(2);
                    assertThat(s.getAccountCount()).isEqualTo(1);
                });
            assertThat(current).filteredOn(s -> s.getPlatform() == Platform.TWITTER)
                .singleElement()
                .satisfies(s -> assertThat(s.getPostCount()).isEqualTo(1));
        });
    }

    @Test
    void chiGuiSauKhiCommit() {
        importService.importPosts(upload(List.of(
            List.of("facebook", "fb-1", "Bài 1", "", ""),
            List.of("facebook", "fb-2", "Bài 2", "", ""))), admin.getId());

        await().atMost(ofSeconds(10)).untilAsserted(() ->
            assertThat(summaries.findByPlatform(Platform.FACEBOOK))
                .get()
                .satisfies(s -> assertThat(s.getPostCount()).isEqualTo(2)));
    }

    @Test
    void nenTangKhongConBaiNaoThiVeKhong() {
        importService.importPosts(upload(List.of(
            List.of("twitter", "tw-1", "Bài", "", ""))), admin.getId());
        await().atMost(ofSeconds(10)).untilAsserted(() ->
            assertThat(summaries.findByPlatform(Platform.TWITTER))
                .get().satisfies(s -> assertThat(s.getPostCount()).isEqualTo(1)));

        posts.deleteAll();
        statisticsService.refresh();

        assertThat(summaries.findByPlatform(Platform.TWITTER))
            .get().satisfies(s -> assertThat(s.getPostCount()).isZero());
    }

    // Xử lý lại cùng một message không được làm sai số liệu: JMS chỉ bảo đảm "ít nhất một lần".
    @Test
    void chayLaiNhieuLanVanRaCungKetQua() {
        importService.importPosts(upload(List.of(
            List.of("facebook", "fb-1", "Bài 1", "", ""))), admin.getId());

        await().atMost(ofSeconds(10)).untilAsserted(() ->
            assertThat(summaries.findByPlatform(Platform.FACEBOOK))
                .get().satisfies(s -> assertThat(s.getPostCount()).isEqualTo(1)));

        statisticsService.refresh();
        statisticsService.refresh();

        assertThat(summaries.findByPlatform(Platform.FACEBOOK))
            .get().satisfies(s -> assertThat(s.getPostCount()).isEqualTo(1));
        assertThat(summaries.findAll()).hasSize(Platform.values().length);
    }
}
