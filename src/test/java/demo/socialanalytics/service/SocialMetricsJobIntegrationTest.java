package demo.socialanalytics.service;

import demo.socialanalytics.entity.*;
import demo.socialanalytics.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import demo.socialanalytics.client.SocialApiClient;
import demo.socialanalytics.client.SocialMetricsSnapshot;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

// Chạy job thật: qua ThreadPoolTaskExecutor thật, ghi vào DB thật (H2).
@SpringBootTest(properties = {
    "social.crawl.enabled=true",
    // Lịch tự động lùi thật xa: test tự gọi runOnce(), không muốn job tự chạy chen vào.
    "social.crawl.initial-delay=PT24H",
    "social.crawl.pool-size=4",
    "social.crawl.mock.latency-ms=0",
    "social.crawl.mock.failure-rate=0"
})
@ActiveProfiles("test")
@Import(SocialMetricsJobIntegrationTest.ThreadRecordingClient.class)
class SocialMetricsJobIntegrationTest {

    // Tên các luồng đã thực sự gọi API, ghi lại để kiểm tra phần chạy song song.
    static final Set<String> crawlThreadNames = ConcurrentHashMap.newKeySet();

    // Thay client giả lập mặc định bằng bản có ghi lại tên luồng
    @TestConfiguration
    static class ThreadRecordingClient {
        @Bean
        @Primary
        SocialApiClient recordingSocialApiClient() {
            return post -> {
                crawlThreadNames.add(Thread.currentThread().getName());
                return new SocialMetricsSnapshot(100, 10, 5, 1_000);
            };
        }
    }

    @Autowired SocialMetricsUpdateJob job;
    @Autowired CrawlStatusService crawlStatusService;
    @Autowired UserRepository users;
    @Autowired PostRepository posts;
    @Autowired SocialMetricRepository metrics;
    @Autowired CrawlRunRepository crawlRuns;

    @BeforeEach
    void setUp() {
        metrics.deleteAll();
        posts.deleteAll();
        users.deleteAll();
        crawlRuns.deleteAll();
    }

    private User saveUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setFullName("Chủ tài khoản " + email);
        user.setRole(Role.ADMIN);
        return users.save(user);
    }

    private Post savePost(User owner, String externalId) {
        Post post = new Post();
        post.setUser(owner);
        post.setPlatform(Platform.FACEBOOK);
        post.setExternalId(externalId);
        post.setContent("Nội dung " + externalId);
        return posts.save(post);
    }

    @Test
    void ghiMotLanDoMoiChoTungBaiViet() {
        User owner = saveUser("a@example.com");
        savePost(owner, "fb-1");
        savePost(owner, "fb-2");

        CrawlRun run = job.runOnce();

        assertThat(run.getStatus()).isEqualTo(CrawlStatus.SUCCESS);
        assertThat(run.getTotalPosts()).isEqualTo(2);
        assertThat(metrics.findAll()).hasSize(2);
        assertThat(metrics.findAll()).allSatisfy(metric -> {
            assertThat(metric.getLikes()).isPositive();
            assertThat(metric.getCollectedAt()).isNotNull();
        });
    }

    // Chạy hai lần thì mỗi bài có hai dòng lịch sử — chỉ số lưu theo chuỗi thời gian, không ghi đè.
    @Test
    void chayNhieuLanThiCongDonLichSuChuKhongGhiDe() {
        User owner = saveUser("a@example.com");
        savePost(owner, "fb-1");

        job.runOnce();
        job.runOnce();

        assertThat(metrics.findAll()).hasSize(2);
        assertThat(crawlRuns.findAll()).hasSize(2);
    }

    // Nhiều tài khoản phải được xử lý trên NHIỀU luồng khác nhau, không dồn về một luồng.
    @Test
    void xuLyNhieuTaiKhoanTrenNhieuLuongKhacNhau() {
        for (int i = 1; i <= 4; i++) {
            User owner = saveUser("user" + i + "@example.com");
            savePost(owner, "fb-" + i);
        }

        job.runOnce();

        assertThat(crawlRuns.findAll()).singleElement()
            .satisfies(run -> assertThat(run.getTotalAccounts()).isEqualTo(4));
        assertThat(metrics.findAll()).hasSize(4);
    }

    // Việc phải chạy trên bể luồng riêng (tiền tố crawl-) và trải qua nhiều luồng
    @Test
    void chayTrenNhieuLuongRieng() {
        for (int i = 1; i <= 4; i++) {
            savePost(saveUser("user" + i + "@example.com"), "fb-" + i);
        }
        String callerThread = Thread.currentThread().getName();
        crawlThreadNames.clear();

        job.runOnce();

        assertThat(crawlThreadNames).isNotEmpty();
        assertThat(crawlThreadNames).allSatisfy(name -> assertThat(name).startsWith("crawl-"));
        assertThat(crawlThreadNames).doesNotContain(callerThread);
        // 4 tài khoản, bể 4 luồng -> phải dùng nhiều hơn một luồng.
        assertThat(crawlThreadNames).hasSizeGreaterThan(1);
    }

    @Test
    void khongCoBaiVietNaoThiVanGhiLaiLanChay() {
        saveUser("a@example.com");

        CrawlRun run = job.runOnce();

        assertThat(run.getStatus()).isEqualTo(CrawlStatus.SUCCESS);
        assertThat(run.getTotalAccounts()).isZero();
        assertThat(metrics.findAll()).isEmpty();
    }

    // "Last updated time": lấy đúng lần chạy gần nhất.
    @Test
    void trangThaiTraVeLanChayGanNhat() {
        User owner = saveUser("a@example.com");
        savePost(owner, "fb-1");

        assertThat(crawlStatusService.lastRun()).isEmpty();

        job.runOnce();
        job.runOnce();

        assertThat(crawlStatusService.lastRun()).isPresent().get().satisfies(last -> {
            assertThat(last.status()).isEqualTo("SUCCESS");
            assertThat(last.startedAt()).isNotNull();
            assertThat(last.finishedAt()).isNotNull();
            assertThat(last.totalPosts()).isEqualTo(1);
        });
        assertThat(crawlStatusService.recentRuns()).hasSize(2);
        // Mới nhất đứng trước.
        assertThat(crawlStatusService.recentRuns().get(0).id())
            .isGreaterThan(crawlStatusService.recentRuns().get(1).id());
    }
}
