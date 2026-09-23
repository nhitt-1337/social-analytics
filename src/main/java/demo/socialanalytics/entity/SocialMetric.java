package demo.socialanalytics.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

// Một lần đo tương tác của bài viết.
@Getter
@Entity
@Table(
    name = "social_metrics",
    indexes = {
        @Index(name = "idx_metric_post", columnList = "post_id"),
        @Index(name = "idx_metric_collected_at", columnList = "collected_at"),
        @Index(name = "idx_metric_post_collected", columnList = "post_id, collected_at")
    }
)
public class SocialMetric {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    @Setter
    private Post post;

    @Column(nullable = false)
    @Setter
    private Integer likes = 0;

    @Column(nullable = false)
    @Setter
    private Integer shares = 0;

    @Column(nullable = false)
    @Setter
    private Integer comments = 0;

    // Số follower của tài khoản đăng bài tại thời điểm đo.
    @Column(nullable = false)
    @Setter
    private Integer followers = 0;

    // Thời điểm crawl. Mặc định là lúc tạo nếu client không gửi.
    @Column(name = "collected_at", nullable = false)
    @Setter
    private LocalDateTime collectedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (collectedAt == null) {
            collectedAt = createdAt;
        }
    }
}
