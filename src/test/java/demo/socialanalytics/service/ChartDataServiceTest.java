package demo.socialanalytics.service;

import demo.socialanalytics.dto.response.ChartDataResponse;
import demo.socialanalytics.entity.*;
import demo.socialanalytics.exception.InvalidRequestParameterException;
import demo.socialanalytics.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({ChartDataService.class, StatisticsService.class})
@ActiveProfiles("test")
class ChartDataServiceTest {
    @Autowired ChartDataService chartDataService;
    @Autowired StatisticsService statisticsService;
    @Autowired UserRepository users;
    @Autowired PostRepository posts;
    @Autowired SocialMetricRepository metrics;
    @Autowired TestEntityManager entityManager;

    User owner;

    @BeforeEach
    void setUp() {
        owner = new User();
        owner.setEmail("a@example.com");
        owner.setFullName("Chủ tài khoản");
        owner.setRole(Role.ADMIN);
        owner = users.save(owner);
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
    void gopTongTuongTacTheoTungNgay() {
        Post post = savePost(Platform.FACEBOOK, "fb-1");
        saveMetric(post, 100, LocalDate.now().minusDays(2).atTime(9, 0));
        saveMetric(post, 200, LocalDate.now().minusDays(1).atTime(9, 0));
        entityManager.flush();

        ChartDataResponse data = chartDataService.chartData(null, 7);

        assertThat(data.labels()).containsExactly(
            LocalDate.now().minusDays(2), LocalDate.now().minusDays(1));
        assertThat(data.likes()).containsExactly(100L, 200L);
        assertThat(data.shares()).containsExactly(50L, 100L);
    }

    // Một bài crawl nhiều lần trong ngày thì chỉ lấy lần đo cuối, không cộng dồn
    @Test
    void motNgayNhieuLanChiLayLanCuoi() {
        Post post = savePost(Platform.FACEBOOK, "fb-1");
        LocalDate today = LocalDate.now();
        saveMetric(post, 100, today.atTime(8, 0));
        saveMetric(post, 150, today.atTime(12, 0));
        saveMetric(post, 180, today.atTime(18, 0));
        entityManager.flush();

        ChartDataResponse data = chartDataService.chartData(null, 7);

        assertThat(data.labels()).containsExactly(today);
        assertThat(data.likes()).containsExactly(180L);
    }

    @Test
    void congSoLieuCuaNhieuBai() {
        Post first = savePost(Platform.FACEBOOK, "fb-1");
        Post second = savePost(Platform.FACEBOOK, "fb-2");
        LocalDate today = LocalDate.now();
        saveMetric(first, 100, today.atTime(9, 0));
        saveMetric(second, 60, today.atTime(9, 0));
        entityManager.flush();

        assertThat(chartDataService.chartData(null, 7).likes()).containsExactly(160L);
    }

    @Test
    void locTheoNenTang() {
        Post facebook = savePost(Platform.FACEBOOK, "fb-1");
        Post twitter = savePost(Platform.TWITTER, "tw-1");
        LocalDate today = LocalDate.now();
        saveMetric(facebook, 100, today.atTime(9, 0));
        saveMetric(twitter, 40, today.atTime(9, 0));
        entityManager.flush();

        assertThat(chartDataService.chartData("facebook", 7).likes()).containsExactly(100L);
        assertThat(chartDataService.chartData("twitter", 7).likes()).containsExactly(40L);
        assertThat(chartDataService.chartData(null, 7).likes()).containsExactly(140L);
    }

    @Test
    void boQuaSoLieuNgoaiKhoangThoiGian() {
        Post post = savePost(Platform.FACEBOOK, "fb-1");
        saveMetric(post, 999, LocalDate.now().minusDays(30).atTime(9, 0));
        saveMetric(post, 100, LocalDate.now().atTime(9, 0));
        entityManager.flush();

        assertThat(chartDataService.chartData(null, 7).likes()).containsExactly(100L);
        assertThat(chartDataService.chartData(null, 60).likes()).containsExactly(999L, 100L);
    }

    @Test
    void chuaCoSoLieuThiTraVeDanhSachRong() {
        ChartDataResponse data = chartDataService.chartData(null, 7);

        assertThat(data.isEmpty()).isTrue();
        assertThat(data.labels()).isEmpty();
        assertThat(data.generatedAt()).isNotNull();
    }

    @Test
    void kemTheoSoLieuTongHopTheoNenTang() {
        savePost(Platform.FACEBOOK, "fb-1");
        entityManager.flush();
        statisticsService.refresh();

        assertThat(chartDataService.chartData(null, 7).platforms())
            .hasSize(Platform.values().length)
            .anySatisfy(summary -> {
                assertThat(summary.platform()).isEqualTo("facebook");
                assertThat(summary.postCount()).isEqualTo(1);
            });
    }

    @Test
    void khongTruyenDaysThiDungMacDinh() {
        Post post = savePost(Platform.FACEBOOK, "fb-1");
        saveMetric(post, 100, LocalDate.now().atTime(9, 0));
        saveMetric(post, 50, LocalDate.now().minusDays(ChartDataService.DEFAULT_DAYS).atTime(9, 0));
        entityManager.flush();

        assertThat(chartDataService.chartData(null, null).likes()).containsExactly(100L);
    }

    @Test
    void daysVuotTranThiBaoLoi() {
        assertThatThrownBy(() -> chartDataService.chartData(null, ChartDataService.MAX_DAYS + 1))
            .isInstanceOf(InvalidRequestParameterException.class)
            .hasMessageContaining(String.valueOf(ChartDataService.MAX_DAYS));
    }

    @Test
    void daysNhoHon1ThiBaoLoi() {
        assertThatThrownBy(() -> chartDataService.chartData(null, 0))
            .isInstanceOf(InvalidRequestParameterException.class);
    }

    @Test
    void nenTangKhongHopLe() {
        assertThatThrownBy(() -> chartDataService.chartData("instagram", 7))
            .isInstanceOf(InvalidRequestParameterException.class)
            .hasMessageContaining("instagram");
    }
}
