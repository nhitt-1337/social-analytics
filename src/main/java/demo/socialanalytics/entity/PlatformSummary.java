package demo.socialanalytics.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

// Thống kê tổng hợp theo nền tảng, tính sẵn để dashboard khỏi phải COUNT lại mỗi lần mở.
@Getter
@Entity
@Table(
    name = "platform_summaries",
    uniqueConstraints = @UniqueConstraint(name = "uq_platform_summary", columnNames = "platform")
)
public class PlatformSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Setter
    private Platform platform;

    @Column(name = "post_count", nullable = false)
    @Setter
    private long postCount;

    // Số tài khoản đang có bài trên nền tảng này.
    @Column(name = "account_count", nullable = false)
    @Setter
    private long accountCount;

    @Column(name = "updated_at", nullable = false)
    @Setter
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void touch() {
        updatedAt = LocalDateTime.now();
    }
}
