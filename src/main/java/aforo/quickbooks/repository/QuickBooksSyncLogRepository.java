package aforo.quickbooks.repository;

import aforo.quickbooks.entity.QuickBooksMapping.EntityType;
import aforo.quickbooks.entity.QuickBooksSyncLog;
import aforo.quickbooks.entity.QuickBooksSyncLog.SyncStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Repository for QuickBooks sync operation logs.
 */
@Repository
public interface QuickBooksSyncLogRepository extends JpaRepository<QuickBooksSyncLog, Long> {

    Page<QuickBooksSyncLog> findByOrganizationId(Long organizationId, Pageable pageable);

    Page<QuickBooksSyncLog> findByOrganizationIdAndStatus(
            Long organizationId, SyncStatus status, Pageable pageable);

    List<QuickBooksSyncLog> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(
            EntityType entityType, String entityId);

    @Query("SELECT l FROM QuickBooksSyncLog l WHERE l.status = :status AND l.retryCount < 3 " +
           "AND l.createdAt > :since ORDER BY l.createdAt ASC")
    List<QuickBooksSyncLog> findFailedSyncsForRetry(SyncStatus status, Instant since);

    @Query("SELECT l.entityType, COUNT(l), AVG(l.durationMs) FROM QuickBooksSyncLog l " +
           "WHERE l.organizationId = :organizationId AND l.createdAt > :since " +
           "GROUP BY l.entityType")
    List<Object[]> getSyncStatistics(Long organizationId, Instant since);
}
