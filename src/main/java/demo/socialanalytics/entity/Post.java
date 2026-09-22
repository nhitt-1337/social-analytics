package demo.socialanalytics.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// Bài viết trên mạng xã hội cần theo dõi tương tác.
// externalId là id của bài trên chính nền tảng đó; cặp (platform, external_id) duy nhất để
// import Excel nhiều lần không tạo bản ghi trùng.
@Getter
@Entity
@Table(
    name = "posts",
    uniqueConstraints = @UniqueConstraint(name = "uq_post_platform_external",
        columnNames = {"platform", "external_id"}),
    indexes = {
        @Index(name = "idx_post_user", columnList = "user_id"),
        @Index(name = "idx_post_platform", columnList = "platform"),
        @Index(name = "idx_post_posted_at", columnList = "posted_at")
    }
)
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Người quản lý bài viết này trên dashboard.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @Setter
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Setter
    private Platform platform;

    @Column(name = "external_id", nullable = false, length = 100)
    @Setter
    private String externalId;

    @Column(columnDefinition = "text")
    @Setter
    private String content;

    @Column(length = 500)
    @Setter
    private String url;

    // Thời điểm bài được đăng trên nền tảng (khác created_at là lúc nhập vào hệ thống).
    @Column(name = "posted_at")
    @Setter
    private LocalDateTime postedAt;

    // Xoá bài thì xoá luôn lịch sử chỉ số của nó.
    @OneToMany(mappedBy = "post", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SocialMetric> metrics = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public void addMetric(SocialMetric metric) {
        metrics.add(metric);
        metric.setPost(this);
    }

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
