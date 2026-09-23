package demo.socialanalytics.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

// Token OAuth2 của một người dùng với một nhà cung cấp.
@Getter
@Entity
@Table(
    name = "oauth2_authorized_clients",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_authorized_client",
        columnNames = {"registration_id", "principal_name"}),
    indexes = @Index(name = "idx_authorized_client_principal", columnList = "principal_name")
)
public class AuthorizedClient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Khớp với registrationId trong application.yaml: "facebook", "x".
    @Column(name = "registration_id", nullable = false, length = 100)
    @Setter
    private String registrationId;

    // Tên định danh người dùng do OAuth2User trả về.
    @Column(name = "principal_name", nullable = false, length = 200)
    @Setter
    private String principalName;

    @Column(name = "access_token_type", nullable = false, length = 20)
    @Setter
    private String accessTokenType;

    // Token là dữ liệu nhạy cảm: không bao giờ đưa vào DTO trả ra ngoài.
    @Column(name = "access_token_value", nullable = false, length = 2000)
    @Setter
    private String accessTokenValue;

    @Column(name = "access_token_issued_at")
    @Setter
    private Instant accessTokenIssuedAt;

    @Column(name = "access_token_expires_at")
    @Setter
    private Instant accessTokenExpiresAt;

    // Danh sách scope, ngăn cách bằng dấu phẩy.
    @Column(name = "access_token_scopes", length = 1000)
    @Setter
    private String accessTokenScopes;

    // Chỉ có khi nhà cung cấp cấp refresh token (X cấp khi xin scope offline.access).
    @Column(name = "refresh_token_value", length = 2000)
    @Setter
    private String refreshTokenValue;

    @Column(name = "refresh_token_issued_at")
    @Setter
    private Instant refreshTokenIssuedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    protected void touch() {
        updatedAt = Instant.now();
    }
}
