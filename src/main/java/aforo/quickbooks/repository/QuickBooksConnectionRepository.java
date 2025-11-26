package aforo.quickbooks.repository;

import aforo.quickbooks.entity.QuickBooksConnection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository for QuickBooks connection management.
 */
@Repository
public interface QuickBooksConnectionRepository extends JpaRepository<QuickBooksConnection, Long> {

    Optional<QuickBooksConnection> findByOrganizationId(Long organizationId);

    Optional<QuickBooksConnection> findByRealmId(String realmId);

    List<QuickBooksConnection> findByIsActiveTrue();

    @Query("SELECT c FROM QuickBooksConnection c WHERE c.isActive = true AND c.expiresAt < :expiryThreshold")
    List<QuickBooksConnection> findByExpiresAtBefore(Instant expiryThreshold);

    @Query("SELECT c FROM QuickBooksConnection c WHERE c.isActive = true AND c.refreshTokenExpiresAt < :now")
    List<QuickBooksConnection> findByRefreshTokenExpired(Instant now);

    boolean existsByOrganizationId(Long organizationId);
}
