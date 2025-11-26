package aforo.quickbooks.repository;

import aforo.quickbooks.entity.QuickBooksMapping;
import aforo.quickbooks.entity.QuickBooksMapping.EntityType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for QuickBooks entity mappings.
 */
@Repository
public interface QuickBooksMappingRepository extends JpaRepository<QuickBooksMapping, Long> {

    Optional<QuickBooksMapping> findByOrganizationIdAndEntityTypeAndAforoId(
            Long organizationId, EntityType entityType, String aforoId);

    Optional<QuickBooksMapping> findByOrganizationIdAndEntityTypeAndQuickbooksId(
            Long organizationId, EntityType entityType, String quickbooksId);

    List<QuickBooksMapping> findByOrganizationIdAndEntityType(
            Long organizationId, EntityType entityType);

    List<QuickBooksMapping> findByOrganizationId(Long organizationId);

    boolean existsByOrganizationIdAndEntityTypeAndAforoId(
            Long organizationId, EntityType entityType, String aforoId);

    void deleteByOrganizationIdAndEntityTypeAndAforoId(
            Long organizationId, EntityType entityType, String aforoId);
}
