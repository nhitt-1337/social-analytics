package demo.socialanalytics.repository;

import demo.socialanalytics.entity.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class SocialMetricRepositoryTest {

    @Autowired SocialMetricRepository metricRepository;
    @Autowired TestEntityManager entityManager;

    Post firstPost;
    Post secondPost;

    @BeforeEach
    void setUp() {
        User owner = new User();
        owner.setEmail("admin@example.com");
        owner.setFullName("Quản trị viên");
        owner.setRole(Role.ADMIN);
        owner = entityManager.persist(owner);

        firstPost = persistPost(owner, Platform.FACEBOOK, "fb-001");
        secondPost = persistPost(owner, Platform.TWITTER, "tw-001");
    }

    private Post persistPost(User owner, Platform platform, String externalId) {
        Post post = new Post();
        post.setUser(owner);
        post.setPlatform(platform);
        post.setExternalId(externalId);
        return entityManager.persist(post);
    }

    private SocialMetric persistMetric(Post post, int likes, LocalDateTime collectedAt) {
        SocialMetric metric = new SocialMetric();
        metric.setPost(post);
        metric.setLikes(likes);
        metric.setShares(likes / 2);
        metric.setComments(likes / 4);
        metric.setFollowers(1_000);
        metric.setCollectedAt(collectedAt);
        return entityManager.persist(metric);
    }

    @Test
    void layLanDoGanNhatCuaMotBai() {
        persistMetric(firstPost, 100, LocalDateTime.of(2026, 4, 1, 8, 0));
        persistMetric(firstPost, 300, LocalDateTime.of(2026, 4, 3, 8, 0));
        persistMetric(firstPost, 200, LocalDateTime.of(2026, 4, 2, 8, 0));
        entityManager.flush();

        assertThat(metricRepository.findFirstByPostIdOrderByCollectedAtDescIdDesc(firstPost.getId()))
            .get()
            .satisfies(metric -> assertThat(metric.getLikes()).isEqualTo(300));
    }

    // Hai lần đo CÙNG thời điểm -> lấy bản có id lớn hơn (ghi sau) để kết quả luôn xác định.
    @Test
    void cungThoiDiemDoThiUuTienBanGhiSau() {
        LocalDateTime sameMoment = LocalDateTime.of(2026, 4, 1, 8, 0);
        persistMetric(firstPost, 100, sameMoment);
        SocialMetric later = persistMetric(firstPost, 999, sameMoment);
        entityManager.flush();

        assertThat(metricRepository.findFirstByPostIdOrderByCollectedAtDescIdDesc(firstPost.getId()))
            .get()
            .satisfies(metric -> assertThat(metric.getId()).isEqualTo(later.getId()));
    }

    @Test
    void baiChuaCoLanDoNaoThiTraVeRong() {
        assertThat(metricRepository.findFirstByPostIdOrderByCollectedAtDescIdDesc(firstPost.getId()))
            .isEmpty();
    }

    @Test
    void chuoiThoiGianSapTangDanTrongKhoangDaChon() {
        persistMetric(firstPost, 10, LocalDateTime.of(2026, 1, 10, 8, 0));
        persistMetric(firstPost, 20, LocalDateTime.of(2026, 2, 10, 8, 0));
        persistMetric(firstPost, 30, LocalDateTime.of(2026, 3, 10, 8, 0));
        entityManager.flush();

        List<SocialMetric> series = metricRepository
            .findByPostIdAndCollectedAtBetweenOrderByCollectedAtAsc(
                firstPost.getId(),
                LocalDateTime.of(2026, 1, 1, 0, 0),
                LocalDateTime.of(2026, 2, 28, 0, 0));

        assertThat(series).extracting(SocialMetric::getLikes).containsExactly(10, 20);
    }

    @Test
    void chiLayChiSoCuaDungBaiDuocHoi() {
        persistMetric(firstPost, 10, LocalDateTime.of(2026, 4, 1, 8, 0));
        persistMetric(secondPost, 99, LocalDateTime.of(2026, 4, 1, 8, 0));
        entityManager.flush();

        var page = metricRepository.findByPostId(firstPost.getId(),
            PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "collectedAt")));

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent()).extracting(SocialMetric::getLikes).containsExactly(10);
    }

    // Truy vấn gộp dùng cho export: một lần gọi lấy chỉ số của nhiều bài, đã sắp sẵn giảm dần theo
    @Test
    void layChiSoCuaNhieuBaiTrongMotLan() {
        persistMetric(firstPost, 100, LocalDateTime.of(2026, 4, 1, 8, 0));
        persistMetric(firstPost, 300, LocalDateTime.of(2026, 4, 3, 8, 0));
        persistMetric(secondPost, 50, LocalDateTime.of(2026, 4, 2, 8, 0));
        entityManager.flush();

        List<SocialMetric> metrics = metricRepository.findByPostIdInOrderByCollectedAtDesc(
            List.of(firstPost.getId(), secondPost.getId()));

        assertThat(metrics).hasSize(3);
        // Trong nhóm của firstPost, bản 300 (đo ngày 3/4) phải đứng trước bản 100 (ngày 1/4).
        List<SocialMetric> ofFirstPost = metrics.stream()
            .filter(metric -> metric.getPost().getId().equals(firstPost.getId()))
            .toList();
        assertThat(ofFirstPost).extracting(SocialMetric::getLikes).containsExactly(300, 100);
    }

    @Test
    void xoaBaiVietThiXoaLuonLichSuChiSo() {
        persistMetric(firstPost, 100, LocalDateTime.of(2026, 4, 1, 8, 0));
        persistMetric(firstPost, 200, LocalDateTime.of(2026, 4, 2, 8, 0));
        entityManager.flush();
        Long postId = firstPost.getId();
        // Nạp lại bài viết để collection metrics được Hibernate theo dõi — orphanRemoval chỉ xoá con
        entityManager.clear();

        Post reloaded = entityManager.find(Post.class, postId);
        entityManager.getEntityManager().remove(reloaded);
        entityManager.flush();

        assertThat(metricRepository.findByPostIdInOrderByCollectedAtDesc(List.of(postId))).isEmpty();
    }

    @Test
    void tongLuotThichCuaMotBai() {
        persistMetric(firstPost, 100, LocalDateTime.of(2026, 4, 1, 8, 0));
        persistMetric(firstPost, 250, LocalDateTime.of(2026, 4, 2, 8, 0));
        entityManager.flush();

        assertThat(metricRepository.sumLikesByPostId(firstPost.getId())).isEqualTo(350L);
    }

    // coalesce trong câu query đảm bảo trả 0 chứ không phải null khi chưa có lần đo nào.
    @Test
    void tongLuotThichLa0KhiChuaCoLanDoNao() {
        assertThat(metricRepository.sumLikesByPostId(firstPost.getId())).isZero();
    }
}
