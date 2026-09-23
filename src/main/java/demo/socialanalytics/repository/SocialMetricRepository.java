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

    List<SocialMetric> findByPostIdAndCollectedAtBetweenOrderByCollectedAtAsc(
        Long postId, LocalDateTime from, LocalDateTime to);

    Optional<SocialMetric> findFirstByPostIdOrderByCollectedAtDescIdDesc(Long postId);

    // Alias metric_day vì 'day' là từ khoá của H2
    @Query(value = """
        SELECT CAST(m.collected_at AS DATE)      AS metric_day,
               SUM(m.likes)                      AS likes,
               SUM(m.shares)                     AS shares,
               SUM(m.comments)                   AS comments,
               SUM(m.followers)                  AS followers
        FROM social_metrics m
        JOIN (
            SELECT post_id, CAST(collected_at AS DATE) AS d, MAX(id) AS last_id
            FROM social_metrics
            WHERE collected_at >= :from
            GROUP BY post_id, CAST(collected_at AS DATE)
        ) latest ON m.id = latest.last_id
        JOIN posts p ON p.id = m.post_id
        WHERE (:platform IS NULL OR p.platform = :platform)
        GROUP BY CAST(m.collected_at AS DATE)
        ORDER BY CAST(m.collected_at AS DATE)
        """, nativeQuery = true)
    List<Object[]> sumDailyTotals(
        @Param("from") LocalDateTime from,
        @Param("platform") String platform);

    @Query("select coalesce(sum(m.likes), 0) from SocialMetric m where m.post.id = :postId")
    long sumLikesByPostId(@Param("postId") Long postId);

    // Lấy chỉ số nhiều bài trong một truy vấn, tránh N+1
    @Query("select m from SocialMetric m where m.post.id in :postIds "
        + "order by m.post.id, m.collectedAt desc, m.id desc")
    List<SocialMetric> findByPostIdInOrderByCollectedAtDesc(@Param("postIds") Collection<Long> postIds);
}
