package edu.cit.delacruz.supplier;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface SupplierOrderRepository extends JpaRepository<SupplierOrder, Long> {

    List<SupplierOrder> findByStatus(SupplierOrderStatus status, Pageable pageable);

    List<SupplierOrder> findByStatusInAndPoNumberIsNotNull(List<SupplierOrderStatus> statuses, Pageable pageable);

    /**
     * The non-closed SupplierOrder for this product, if any - at most one
     * can exist (see the partial unique index in schema.sql). Returns the
     * row itself, not just whether one exists, so the caller can tell a
     * row that's genuinely open (LegacySupply has acknowledged it) apart
     * from one still PENDING because it was never actually sent.
     */
    Optional<SupplierOrder> findFirstByProductIdAndStatusNotIn(String productId, List<SupplierOrderStatus> statuses);
}