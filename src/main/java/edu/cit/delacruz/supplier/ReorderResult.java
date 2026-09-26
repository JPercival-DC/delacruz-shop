package edu.cit.delacruz.supplier;

/**
 * SupplierGateway's own outcome type for a reorder request. Order and
 * Inventory only ever see this - never a SupplierOrder entity, a
 * PoNumber, or a LegacySupply status code.
 */
public record ReorderResult(Long id, String productId, int units, SupplierOrderStatus status) {
}
