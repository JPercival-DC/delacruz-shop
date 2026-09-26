package edu.cit.delacruz.supplier;

/**
 * Our own vocabulary for a purchase order's lifecycle, never LegacySupply's
 * StatusCode. PENDING is entirely local (not yet sent); the rest map from
 * LegacySupply's 10/20/30/40. NEEDS_REVIEW covers a status code we didn't
 * expect - see INTEGRATION.md, Part E.
 */
public enum SupplierOrderStatus {
    PENDING,
    PLACED,
    PREPARING,
    SHIPPED,
    DELIVERED,
    FAILED,
    NEEDS_REVIEW
}
