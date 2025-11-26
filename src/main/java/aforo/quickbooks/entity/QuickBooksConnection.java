package aforo.quickbooks.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Entity to store QuickBooks OAuth connection details for each organization.
 */
@Entity
@Table(name = "quickbooks_connection", 
       uniqueConstraints = @UniqueConstraint(columnNames = "organization_id"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuickBooksConnection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false, unique = true)
    private Long organizationId;

    @Column(name = "realm_id", nullable = false, length = 50)
    private String realmId;  // QuickBooks Company ID

    @Column(name = "access_token", nullable = false, columnDefinition = "TEXT")
    private String accessToken;

    @Column(name = "refresh_token", nullable = false, columnDefinition = "TEXT")
    private String refreshToken;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "refresh_token_expires_at")
    private Instant refreshTokenExpiresAt;

    @Column(name = "company_name", length = 255)
    private String companyName;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "last_sync_at")
    private Instant lastSyncAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    /**
     * Check if access token is expired or about to expire (within 5 minutes).
     */
    public boolean isTokenExpired() {
        return expiresAt.isBefore(Instant.now().plusSeconds(300));
    }

    /**
     * Check if refresh token is expired.
     */
    public boolean isRefreshTokenExpired() {
        return refreshTokenExpiresAt != null && 
               refreshTokenExpiresAt.isBefore(Instant.now());
    }
}
