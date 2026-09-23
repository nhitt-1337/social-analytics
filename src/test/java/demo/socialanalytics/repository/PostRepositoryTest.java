package demo.socialanalytics.repository;

import demo.socialanalytics.entity.Platform;
import demo.socialanalytics.entity.Post;
import demo.socialanalytics.entity.Role;
import demo.socialanalytics.entity.User;
import demo.socialanalytics.repository.projection.PostIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// @DataJpaTest chỉ dựng tầng JPA và tự rollback sau mỗi test
@DataJpaTest
@ActiveProfiles("test")
class PostRepositoryTest {
    @Autowired PostRepository postRepository;
    @Autowired TestEntityManager entityManager;

    User owner;

    @BeforeEach
    void setUp() {
        owner = entityManager.persist(user("admin@example.com"));
    }

    private User user(String email) {
        User user = new User();
        user.setEmail(email);
        user.setFullName("Quản trị viên");
        user.setRole(Role.ADMIN);
        return user;
    }

    private Post post(Platform platform, String externalId, LocalDateTime postedAt) {
        Post post = new Post();
        post.setUser(owner);
        post.setPlatform(platform);
        post.setExternalId(externalId);
        post.setContent("Nội dung " + externalId);
        post.setPostedAt(postedAt);
        return entityManager.persist(post);
    }

    @Test
    void chanLuuTrungCapPlatformVaExternalId() {
        post(Platform.FACEBOOK, "fb-001", null);
        entityManager.flush();

        Post duplicate = new Post();
        duplicate.setUser(owner);
        duplicate.setPlatform(Platform.FACEBOOK);
        duplicate.setExternalId("fb-001");

        assertThatThrownBy(() -> {
            postRepository.save(duplicate);
            entityManager.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void chapNhanCungExternalIdOHaiNenTangKhacNhau() {
        post(Platform.FACEBOOK, "post-1", null);
        post(Platform.TWITTER, "post-1", null);
        entityManager.flush();

        assertThat(postRepository.existsByPlatformAndExternalId(Platform.FACEBOOK, "post-1")).isTrue();
        assertThat(postRepository.existsByPlatformAndExternalId(Platform.TWITTER, "post-1")).isTrue();
    }

    @Test
    void traVeCapDinhDanhCuaCacBaiDaTonTai() {
        post(Platform.FACEBOOK, "fb-001", null);
        post(Platform.TWITTER, "tw-002", null);
        entityManager.flush();

        List<PostIdentity> found = postRepository.findIdentitiesByExternalIdIn(
            Set.of("fb-001", "tw-002", "khong-ton-tai"));

        assertThat(found).containsExactlyInAnyOrder(
            new PostIdentity(Platform.FACEBOOK, "fb-001"),
            new PostIdentity(Platform.TWITTER, "tw-002"));
    }

    @Test
    void traVeRongKhiKhongBaiNaoKhop() {
        post(Platform.FACEBOOK, "fb-001", null);
        entityManager.flush();

        assertThat(postRepository.findIdentitiesByExternalIdIn(Set.of("khac-han"))).isEmpty();
    }

    @Test
    void boQuaMoiBoLocKhiTatCaThamSoLaNull() {
        post(Platform.FACEBOOK, "fb-001", LocalDateTime.of(2026, 1, 15, 8, 0));
        post(Platform.TWITTER, "tw-001", LocalDateTime.of(2026, 2, 15, 8, 0));
        post(Platform.FACEBOOK, "fb-002", null);
        entityManager.flush();

        List<Post> result = postRepository.findForReport(null, null, null, PageRequest.ofSize(100));

        assertThat(result).extracting(Post::getExternalId)
            .containsExactlyInAnyOrder("fb-001", "tw-001", "fb-002");
    }

    @Test
    void locTheoNenTang() {
        post(Platform.FACEBOOK, "fb-001", LocalDateTime.of(2026, 1, 15, 8, 0));
        post(Platform.TWITTER, "tw-001", LocalDateTime.of(2026, 1, 16, 8, 0));
        entityManager.flush();

        List<Post> result = postRepository.findForReport(
            Platform.FACEBOOK, null, null, PageRequest.ofSize(100));

        assertThat(result).extracting(Post::getExternalId).containsExactly("fb-001");
    }

    @Test
    void locTheoKhoangThoiGianDangBai() {
        post(Platform.FACEBOOK, "thang-1", LocalDateTime.of(2026, 1, 15, 8, 0));
        post(Platform.FACEBOOK, "thang-2", LocalDateTime.of(2026, 2, 15, 8, 0));
        post(Platform.FACEBOOK, "thang-3", LocalDateTime.of(2026, 3, 15, 8, 0));
        entityManager.flush();

        List<Post> result = postRepository.findForReport(
            null,
            LocalDateTime.of(2026, 2, 1, 0, 0),
            LocalDateTime.of(2026, 2, 28, 23, 59),
            PageRequest.ofSize(100));

        assertThat(result).extracting(Post::getExternalId).containsExactly("thang-2");
    }

    @Test
    void baiThieuPostedAtBiLoaiKhiCoBoLocThoiGian() {
        post(Platform.FACEBOOK, "co-ngay", LocalDateTime.of(2026, 2, 15, 8, 0));
        post(Platform.FACEBOOK, "khong-ngay", null);
        entityManager.flush();

        List<Post> result = postRepository.findForReport(
            null, LocalDateTime.of(2026, 1, 1, 0, 0), null, PageRequest.ofSize(100));

        assertThat(result).extracting(Post::getExternalId).containsExactly("co-ngay");
    }

    @Test
    void sapGiamDanTheoThoiDiemDangBai() {
        post(Platform.FACEBOOK, "cu", LocalDateTime.of(2026, 1, 1, 8, 0));
        post(Platform.FACEBOOK, "moi", LocalDateTime.of(2026, 3, 1, 8, 0));
        post(Platform.FACEBOOK, "giua", LocalDateTime.of(2026, 2, 1, 8, 0));
        entityManager.flush();

        List<Post> result = postRepository.findForReport(null, null, null, PageRequest.ofSize(100));

        assertThat(result).extracting(Post::getExternalId).startsWith("moi", "giua", "cu");
    }

    @Test
    void chanSoDongTheoPageable() {
        post(Platform.FACEBOOK, "fb-001", LocalDateTime.of(2026, 1, 1, 8, 0));
        post(Platform.FACEBOOK, "fb-002", LocalDateTime.of(2026, 1, 2, 8, 0));
        post(Platform.FACEBOOK, "fb-003", LocalDateTime.of(2026, 1, 3, 8, 0));
        entityManager.flush();

        assertThat(postRepository.findForReport(null, null, null, PageRequest.ofSize(2))).hasSize(2);
    }

    @Test
    void napSanNguoiQuanLyTrongCungTruyVan() {
        post(Platform.FACEBOOK, "fb-001", LocalDateTime.of(2026, 1, 1, 8, 0));
        entityManager.flush();
        entityManager.clear();

        List<Post> result = postRepository.findForReport(null, null, null, PageRequest.ofSize(10));

        assertThat(result).singleElement().satisfies(post ->
            assertThat(org.hibernate.Hibernate.isInitialized(post.getUser())).isTrue());
    }
}
