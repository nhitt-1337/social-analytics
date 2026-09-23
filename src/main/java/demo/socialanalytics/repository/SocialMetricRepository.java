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

    // Dữ liệu cho biểu đồ: tổng tương tác theo TỪNG NGÀY.
    //
    // Không cộng thẳng mọi dòng trong ngày: một bài có thể được crawl nhiều lần mỗi ngày, cộng
    // hết lại thì số liệu phồng lên theo tần suất crawl chứ không phản ánh tương tác thật.
    // Phần JOIN với bảng con chọn ra lần đo CUỐI CÙNG của mỗi bài trong mỗi ngày rồi mới cộng.
    //
    // Dùng native query vì JPQL không viết được bảng con trong mệnh đề FROM.
    // CAST(... AS DATE) thay cho DATE(...) để chạy được cả trên MySQL lẫn H2.
    // Alias là metric_day chứ không phải `day`: `day` là từ khoá dành riêng của H2.
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

    // Export báo cáo: lấy chỉ số của nhiều bài trong MỘT truy vấn rồi chọn bản mới nhất ở tầng
    // service, thay vì gọi findFirstByPostId... cho từng bài (N+1).
    // Sắp giảm dần nên bản ghi đầu tiên của mỗi post chính là lần đo gần nhất.
    @Query("select m from SocialMetric m where m.post.id in :postIds "
        + "order by m.post.id, m.collectedAt desc, m.id desc")
    List<SocialMetric> findByPostIdInOrderByCollectedAtDesc(@Param("postIds") Collection<Long> postIds);
}
