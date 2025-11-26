package aforo.quickbooks.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Entity to map Aforo entities to QuickBooks entities.
 * Maintains bidirectional sync mappings.
 */
@Entity
@Table(name = "quickbooks_mapping",
       uniqueConstraints = @UniqueConstraint(
           columnNames = {"organization_id", "entity_type", "aforo_id"}
       ),
       indexes = {
           @Index(name = "idx_qb_mapping_org_type", columnList = "organization_id, entity_type"),
           @Index(name = "idx_qb_mapping_qb_id", columnList = "quickbooks_id")
       })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuickBooksMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, length = 50)
    private EntityType entityType;

    @Column(name = "aforo_id", nullable = false, length = 100)
    private String aforoId;

    @Column(name = "quickbooks_id", nullable = false, length = 50)
    private String quickbooksId;

    @Column(name = "sync_version", nullable = false)
    private Integer syncVersion = 1;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public enum EntityType {
        CUSTOMER,
        INVOICE,
        PAYMENT,
        PRODUCT,
        ITEM,
        ACCOUNT,
        TAX_CODE
    }
}
