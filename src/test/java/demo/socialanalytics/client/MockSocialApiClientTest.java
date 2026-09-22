package demo.socialanalytics.client;

import demo.socialanalytics.config.CrawlProperties;
import demo.socialanalytics.entity.Platform;
import demo.socialanalytics.entity.Post;
import demo.socialanalytics.entity.User;
import demo.socialanalytics.support.TestEntities;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MockSocialApiClientTest {

    private User owner;

    @BeforeEach
    void setUp() {
        owner = TestEntities.user(1L, "admin@example.com");
    }

    private MockSocialApiClient client(long latencyMs, double failureRate) {
        return new MockSocialApiClient(new CrawlProperties(
            true, 8, 100, 300, new CrawlProperties.Mock(latencyMs, failureRate)));
    }

    private Post post(long id, String externalId, LocalDateTime postedAt) {
        Post post = TestEntities.post(id, owner, Platform.FACEBOOK, externalId);
        post.setPostedAt(postedAt);
        return post;
    }

    @Test
    void traVeSoLieuKhongAm() {
        var snapshot = client(0, 0).fetchMetrics(post(1L, "fb-1", LocalDateTime.now().minusDays(1)));

        assertThat(snapshot.likes()).isNotNegative();
        assertThat(snapshot.shares()).isNotNegative();
        assertThat(snapshot.comments()).isNotNegative();
        assertThat(snapshot.followers()).isNotNegative();
    }

    // Số liệu bám theo id bài viết nên gọi lại vẫn ra kết quả tương đương — có vậy biểu đồ
    // tăng trưởng mới có hình dạng hợp lý, thay vì nhảy loạn mỗi lần crawl.
    @Test
    void cungMotBaiThiKetQuaOnDinhQuaCacLanGoi() {
        MockSocialApiClient client = client(0, 0);
        Post post = post(1L, "fb-1", LocalDateTime.now().minusDays(2));

        var first = client.fetchMetrics(post);
        var second = client.fetchMetrics(post);

        assertThat(second.likes()).isEqualTo(first.likes());
        assertThat(second.followers()).isEqualTo(first.followers());
    }

    @Test
    void haiBaiKhacNhauThiSoLieuKhacNhau() {
        MockSocialApiClient client = client(0, 0);
        LocalDateTime postedAt = LocalDateTime.now().minusDays(2);

        var first = client.fetchMetrics(post(1L, "fb-1", postedAt));
        var second = client.fetchMetrics(post(2L, "hoan-toan-khac", postedAt));

        assertThat(second.likes()).isNotEqualTo(first.likes());
    }

    // Bài đăng lâu hơn thì tương tác nhiều hơn.
    @Test
    void baiDangCangLauSoLieuCangCao() {
        MockSocialApiClient client = client(0, 0);

        var moi = client.fetchMetrics(post(1L, "fb-1", LocalDateTime.now().minusHours(1)));
        var cu = client.fetchMetrics(post(1L, "fb-1", LocalDateTime.now().minusDays(30)));

        assertThat(cu.likes()).isGreaterThan(moi.likes());
    }

    @Test
    void tiLeLoi100PhanTramThiLuonNemSocialApiException() {
        MockSocialApiClient client = client(0, 1.0);

        assertThatThrownBy(() -> client.fetchMetrics(post(1L, "fb-1", LocalDateTime.now())))
            .isInstanceOf(SocialApiException.class)
            .hasMessageContaining("fb-1");
    }

    @Test
    void tiLeLoi0ThiKhongBaoGioNem() {
        MockSocialApiClient client = client(0, 0);
        Post post = post(1L, "fb-1", LocalDateTime.now().minusDays(1));

        for (int i = 0; i < 50; i++) {
            assertThat(client.fetchMetrics(post)).isNotNull();
        }
    }

    // Bài chưa có postedAt thì lấy createdAt làm mốc, không được ném NullPointerException.
    @Test
    void baiChuaCoPostedAtVanTinhDuoc() {
        Post post = TestEntities.post(1L, owner, Platform.FACEBOOK, "fb-1");
        ReflectionTestUtils.setField(post, "createdAt", LocalDateTime.now().minusDays(3));

        assertThat(client(0, 0).fetchMetrics(post).likes()).isPositive();
    }
}
