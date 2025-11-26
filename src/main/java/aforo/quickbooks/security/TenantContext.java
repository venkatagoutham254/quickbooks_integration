package aforo.quickbooks.security;

import lombok.extern.slf4j.Slf4j;

/**
 * Thread-local storage for current organization (tenant) ID.
 * Automatically populated from JWT token during request processing.
 */
@Slf4j
public class TenantContext {

    private static final ThreadLocal<Long> CURRENT_ORGANIZATION_ID = new ThreadLocal<>();

    /**
     * Set the current organization ID for this thread/request
     */
    public static void setOrganizationId(Long organizationId) {
        log.debug("Setting organization ID: {}", organizationId);
        CURRENT_ORGANIZATION_ID.set(organizationId);
    }

    /**
     * Get the current organization ID for this thread/request
     * 
     * @return Organization ID or null if not set
     */
    public static Long getOrganizationId() {
        return CURRENT_ORGANIZATION_ID.get();
    }

    /**
     * Get the current organization ID, throwing exception if not set
     * 
     * @return Organization ID
     * @throws IllegalStateException if organization ID not set
     */
    public static Long require() {
        Long organizationId = CURRENT_ORGANIZATION_ID.get();
        if (organizationId == null) {
            throw new IllegalStateException("Organization ID not found in request context");
        }
        return organizationId;
    }

    /**
     * Clear the organization ID for this thread
     * Should be called after request processing completes
     */
    public static void clear() {
        CURRENT_ORGANIZATION_ID.remove();
    }

    /**
     * Check if organization ID is set
     */
    public static boolean isSet() {
        return CURRENT_ORGANIZATION_ID.get() != null;
    }
}
