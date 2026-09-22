package demo.socialanalytics.repository;

import demo.socialanalytics.entity.SocialMetric;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SocialMetricRepository extends JpaRepository<SocialMetric, Long> {

    Page<SocialMetric> findByPostId(Long postId, Pageable pageable);

    // Chuỗi thời gian cho biểu đồ Chart.js ở bước sau.
    List<SocialMetric> findByPostIdAndCollectedAtBetweenOrderByCollectedAtAsc(
        Long postId, LocalDateTime from, LocalDateTime to);

    // Lần đo gần nhất của một bài — số liệu hiển thị trên thẻ tổng quan.
    Optional<SocialMetric> findFirstByPostIdOrderByCollectedAtDescIdDesc(Long postId);

    @Query("select coalesce(sum(m.likes), 0) from SocialMetric m where m.post.id = :postId")
    long sumLikesByPostId(@Param("postId") Long postId);
}
