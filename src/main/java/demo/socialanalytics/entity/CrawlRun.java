package demo.socialanalytics.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Duration;
import java.time.LocalDateTime;

// Lịch sử các lần chạy job cập nhật chỉ số.
//
// Lưu xuống DB chứ không giữ trong bộ nhớ vì "Last updated time" phải đúng cả sau khi restart —
// hiện số liệu cũ mà nói là vừa cập nhật thì tệ hơn là nói thẳng "chưa chạy lần nào".
@Getter
@Entity
@Table(
    name = "crawl_runs",
    indexes = @Index(name = "idx_crawl_run_started_at", columnList = "started_at")
)
public class CrawlRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Setter
    private CrawlStatus status = CrawlStatus.RUNNING;

    @Column(name = "started_at", nullable = false)
    @Setter
    private LocalDateTime startedAt;

    // Null khi lần chạy còn đang dở.
    @Column(name = "finished_at")
    @Setter
    private LocalDateTime finishedAt;

    // Số tài khoản được xử lý song song trong lần chạy này.
    @Column(name = "total_accounts", nullable = false)
    @Setter
    private int totalAccounts;

    @Column(name = "total_posts", nullable = false)
    @Setter
    private int totalPosts;

    @Column(name = "succeeded_posts", nullable = false)
    @Setter
    private int succeededPosts;

    @Column(name = "failed_posts", nullable = false)
    @Setter
    private int failedPosts;

    // Lý do thất bại, hoặc tóm tắt khi chạy dở dang. Cắt ngắn để log dài không làm vỡ cột.
    @Column(length = 1000)
    @Setter
    private String message;

    public Duration duration() {
        LocalDateTime end = finishedAt != null ? finishedAt : LocalDateTime.now();
        return Duration.between(startedAt, end);
    }

    @PrePersist
    protected void onCreate() {
        if (startedAt == null) {
            startedAt = LocalDateTime.now();
        }
    }
}
