package demo.socialanalytics.service;

import demo.socialanalytics.entity.*;
import demo.socialanalytics.repository.PlatformSummaryRepository;
import demo.socialanalytics.repository.PostRepository;
import demo.socialanalytics.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

// Chạy trên DB thật (H2) vì cái cần kiểm là câu truy vấn đếm gộp và việc ghi đè bản ghi cũ —
@DataJpaTest
@Import(StatisticsService.class)
@ActiveProfiles("test")
class StatisticsServiceTest {

    @Autowired StatisticsService statisticsService;
    @Autowired PlatformSummaryRepository summaries;
    @Autowired PostRepository posts;
    @Autowired UserRepository users;
    @Autowired TestEntityManager entityManager;

    User first;
    User second;

    @BeforeEach
    void setUp() {
        first = saveUser("a@example.com");
        second = saveUser("b@example.com");
    }

    private User saveUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setFullName("Người dùng " + email);
        user.setRole(Role.USER);
        return users.save(user);
    }

    private void savePost(User owner, Platform platform, String externalId) {
        Post post = new Post();
        post.setUser(owner);
        post.setPlatform(platform);
        post.setExternalId(externalId);
        posts.save(post);
    }

    private PlatformSummary summaryOf(Platform platform) {
        return summaries.findByPlatform(platform).orElseThrow();
    }

    @Test
    void demSoBaiVaSoTaiKhoanTheoTungNenTang() {
        savePost(first, Platform.FACEBOOK, "fb-1");
        savePost(first, Platform.FACEBOOK, "fb-2");
        savePost(second, Platform.FACEBOOK, "fb-3");
        savePost(second, Platform.TWITTER, "tw-1");
        entityManager.flush();

        statisticsService.refresh();

        assertThat(summaryOf(Platform.FACEBOOK).getPostCount()).isEqualTo(3);
        // Hai tài khoản khác nhau cùng đăng trên Facebook -> đếm distinct.
        assertThat(summaryOf(Platform.FACEBOOK).getAccountCount()).isEqualTo(2);
        assertThat(summaryOf(Platform.TWITTER).getPostCount()).isEqualTo(1);
        assertThat(summaryOf(Platform.TWITTER).getAccountCount()).isEqualTo(1);
    }

    // Nền tảng chưa có bài nào vẫn phải có dòng với số 0, không được thiếu.
    @Test
    void nenTangChuaCoBaiVanCoDongVoiSoKhong() {
        savePost(first, Platform.FACEBOOK, "fb-1");
        entityManager.flush();

        statisticsService.refresh();

        assertThat(summaries.findAll()).hasSize(Platform.values().length);
        assertThat(summaryOf(Platform.TWITTER).getPostCount()).isZero();
    }

    // Tính LẠI TỪ ĐẦU chứ không cộng dồn: xoá bài đi thì số phải giảm.
    @Test
    void xoaBaiThiSoLieuGiam() {
        savePost(first, Platform.FACEBOOK, "fb-1");
        savePost(first, Platform.FACEBOOK, "fb-2");
        entityManager.flush();
        statisticsService.refresh();
        assertThat(summaryOf(Platform.FACEBOOK).getPostCount()).isEqualTo(2);

        posts.deleteAll();
        entityManager.flush();
        statisticsService.refresh();

        assertThat(summaryOf(Platform.FACEBOOK).getPostCount()).isZero();
    }

    // Chạy lại nhiều lần cho ra cùng kết quả và KHÔNG sinh thêm dòng trùng — điều bắt buộc vì JMS
    @Test
    void chayLaiKhongSinhDongTrung() {
        savePost(first, Platform.FACEBOOK, "fb-1");
        entityManager.flush();

        statisticsService.refresh();
        statisticsService.refresh();
        statisticsService.refresh();

        assertThat(summaries.findAll()).hasSize(Platform.values().length);
        assertThat(summaryOf(Platform.FACEBOOK).getPostCount()).isEqualTo(1);
    }

    @Test
    void khongCoBaiNaoThiTatCaVeKhong() {
        statisticsService.refresh();

        assertThat(summaries.findAll())
            .hasSize(Platform.values().length)
            .allSatisfy(summary -> {
                assertThat(summary.getPostCount()).isZero();
                assertThat(summary.getAccountCount()).isZero();
                assertThat(summary.getUpdatedAt()).isNotNull();
            });
    }

    @Test
    void currentSapTheoNenTang() {
        statisticsService.refresh();

        assertThat(statisticsService.current())
            .extracting(PlatformSummary::getPlatform)
            .containsExactly(Platform.FACEBOOK, Platform.TWITTER);
    }
}
