package demo.socialanalytics.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Duration;
import java.time.LocalDateTime;

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

    @Column(name = "finished_at")
    @Setter
    private LocalDateTime finishedAt;

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
