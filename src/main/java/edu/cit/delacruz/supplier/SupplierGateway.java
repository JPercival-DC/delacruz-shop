package edu.cit.delacruz.supplier;

import java.util.Optional;

/**
 * The only entry point into the supplier module. Order and Inventory may
 * depend on this interface and the domain types in this module's root
 * package - nothing else. No XML class, no SupplierSku, no LegacySupply
 * status code is ever allowed to cross this boundary.
 */
public interface SupplierGateway {

    /**
     * Requests enough LegacySupply cases to cover {@code unitsNeeded} of
     * {@code productId}, converting to LegacySupply's unit of measure and
     * rounding up to whole cases. The order is recorded immediately and
     * sent by a background job - this method never calls LegacySupply
     * itself, so a slow or unavailable supplier never blocks the caller.
     *
     * @return the recorded order, or empty (with nothing sent) if a
     *         reorder for this product is already open
     * @throws IllegalArgumentException if productId has no configured
     *                                   LegacySupply mapping, or unitsNeeded
     *                                   is not positive
     */
    Optional<ReorderResult> requestReorder(String productId, int unitsNeeded);
}
