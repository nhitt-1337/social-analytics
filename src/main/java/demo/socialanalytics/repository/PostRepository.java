package demo.socialanalytics.repository;

import demo.socialanalytics.entity.Platform;
import demo.socialanalytics.entity.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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
}
