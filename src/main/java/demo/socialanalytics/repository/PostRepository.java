package demo.socialanalytics.repository;

import demo.socialanalytics.entity.Platform;
import demo.socialanalytics.entity.Post;
import demo.socialanalytics.repository.projection.PostIdentity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PostRepository extends JpaRepository<Post, Long> {

    // Fetch user kèm theo vì mapper đọc tên người quản lý -> tránh N+1 khi liệt kê.
    @EntityGraph(attributePaths = "user")
    Page<Post> findAll(Pageable pageable);

    @EntityGraph(attributePaths = "user")
    Page<Post> findByPlatform(Platform platform, Pageable pageable);

    @EntityGraph(attributePaths = "user")
    Optional<Post> findWithUserById(Long id);

    // Chặn import trùng: cùng một bài trên cùng một nền tảng chỉ lưu một lần.
    boolean existsByPlatformAndExternalId(Platform platform, String externalId);

    Optional<Post> findByPlatformAndExternalId(Platform platform, String externalId);

    // Import Excel: lấy một lượt các bài đã tồn tại trong số externalId sắp nhập,
    // thay vì hỏi DB cho từng dòng.
    @Query("select new demo.socialanalytics.repository.projection.PostIdentity(p.platform, p.externalId) "
        + "from Post p where p.externalId in :externalIds")
    List<PostIdentity> findIdentitiesByExternalIdIn(@Param("externalIds") Collection<String> externalIds);

    // Export báo cáo: lọc theo nền tảng và khoảng thời gian ĐĂNG BÀI; tham số nào null thì bỏ qua.
    // Bài chưa có postedAt sẽ không lọt vào khi người dùng truyền from/to.
    @Query("""
        select p from Post p join fetch p.user
        where (:platform is null or p.platform = :platform)
          and (:from is null or p.postedAt >= :from)
          and (:to is null or p.postedAt <= :to)
        order by p.postedAt desc, p.id desc
        """)
    List<Post> findForReport(
        @Param("platform") Platform platform,
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to,
        Pageable pageable);
}
