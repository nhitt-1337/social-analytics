package demo.socialanalytics.repository;

import demo.socialanalytics.entity.SocialMetric;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
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

    // Export báo cáo: lấy chỉ số của nhiều bài trong MỘT truy vấn rồi chọn bản mới nhất ở tầng
    // service, thay vì gọi findFirstByPostId... cho từng bài (N+1).
    // Sắp giảm dần nên bản ghi đầu tiên của mỗi post chính là lần đo gần nhất.
    @Query("select m from SocialMetric m where m.post.id in :postIds "
        + "order by m.post.id, m.collectedAt desc, m.id desc")
    List<SocialMetric> findByPostIdInOrderByCollectedAtDesc(@Param("postIds") Collection<Long> postIds);
}
