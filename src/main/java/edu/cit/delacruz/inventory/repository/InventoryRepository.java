package edu.cit.delacruz.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import edu.cit.delacruz.inventory.model.InventoryItem;

public interface InventoryRepository
        extends JpaRepository<InventoryItem, String> {

    /**
     * Atomically decrements stock in one statement - the WHERE clause
     * only matches a row that actually has enough stock, so a concurrent
     * reserve() for the same product can never both read the same stale
     * value and both succeed (the lost-update race the old
     * find-then-check-then-save pattern was exposed to).
     *
     * @return 1 if the row was updated, 0 if the product doesn't exist or
     *         doesn't have enough stock - the caller distinguishes those
     *         with a follow-up read only in that (rarer) case.
     */
    @Modifying(clearAutomatically = true)
    @Query("update InventoryItem i set i.stock = i.stock - :quantity "
            + "where i.productId = :productId and i.stock >= :quantity")
    int decrementStock(@Param("productId") String productId, @Param("quantity") int quantity);

    /**
     * Atomic counterpart to {@link #decrementStock}. Restocks have no
     * lower bound to fail against, but still go through the DB in one
     * statement rather than a separate read-then-write, for the same
     * reason: two concurrent restocks (e.g. a cancellation and a supplier
     * delivery landing at once) must not lose one of them to the same
     * last-write-wins race.
     *
     * @return 1 if the row was updated, 0 if the product doesn't exist.
     */
    @Modifying(clearAutomatically = true)
    @Query("update InventoryItem i set i.stock = i.stock + :quantity where i.productId = :productId")
    int incrementStock(@Param("productId") String productId, @Param("quantity") int quantity);
}
