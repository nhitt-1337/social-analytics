package demo.socialanalytics.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

// Người dùng dashboard.
@Getter
@Entity
@Table(
    name = "users",
    uniqueConstraints = @UniqueConstraint(name = "uq_user_provider", columnNames = {"provider", "provider_id"}),
    indexes = @Index(name = "idx_user_email", columnList = "email")
)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Cho phép null: đăng nhập bằng X/Twitter không trả về email (cần quyền riêng mới có),
    @Column(unique = true, length = 255)
    @Setter
    private String email;

    @Column(name = "full_name", nullable = false, length = 100)
    @Setter
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Setter
    private AuthProvider provider = AuthProvider.LOCAL;

    // Null với tài khoản LOCAL.
    @Column(name = "provider_id", length = 100)
    @Setter
    private String providerId;

    @Column(name = "avatar_url", length = 500)
    @Setter
    private String avatarUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Setter
    private Role role = Role.USER;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

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
