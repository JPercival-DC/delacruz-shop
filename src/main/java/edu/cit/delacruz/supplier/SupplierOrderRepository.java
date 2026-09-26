package edu.cit.delacruz.supplier;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface SupplierOrderRepository extends JpaRepository<SupplierOrder, Long> {

    List<SupplierOrder> findByStatus(SupplierOrderStatus status, Pageable pageable);

    List<SupplierOrder> findByStatusInAndPoNumberIsNotNull(List<SupplierOrderStatus> statuses, Pageable pageable);

    boolean existsByProductIdAndStatusNotIn(String productId, List<SupplierOrderStatus> statuses);
}
