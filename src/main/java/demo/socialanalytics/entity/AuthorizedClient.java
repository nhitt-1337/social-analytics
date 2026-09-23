package demo.socialanalytics.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

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

    @Column(name = "registration_id", nullable = false, length = 100)
    @Setter
    private String registrationId;

    @Column(name = "principal_name", nullable = false, length = 200)
    @Setter
    private String principalName;

    @Column(name = "access_token_type", nullable = false, length = 20)
    @Setter
    private String accessTokenType;

    @Column(name = "access_token_value", nullable = false, length = 2000)
    @Setter
    private String accessTokenValue;

    @Column(name = "access_token_issued_at")
    @Setter
    private Instant accessTokenIssuedAt;

    @Column(name = "access_token_expires_at")
    @Setter
    private Instant accessTokenExpiresAt;

    @Column(name = "access_token_scopes", length = 1000)
    @Setter
    private String accessTokenScopes;

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
