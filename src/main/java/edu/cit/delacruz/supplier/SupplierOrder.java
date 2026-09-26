package edu.cit.delacruz.supplier;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * The outbox row for one reorder: written PENDING before any network call,
 * so a restart or a LegacySupply outage can never lose it. Package-private
 * on purpose - Order and Inventory never see this, only ReorderResult.
 */
@Entity
@Table(name = "supplier_orders")
class SupplierOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private String productId;

    @Column(name = "buyer_ref", unique = true)
    private String buyerRef;

    @Column(name = "request_id", nullable = false, unique = true)
    private String requestId;

    @Column(name = "po_number")
    private String poNumber;

    private int cases;
    private int units;

    @Enumerated(EnumType.STRING)
    private SupplierOrderStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected SupplierOrder() {
        // JPA
    }

    SupplierOrder(String productId, String requestId, int cases, int units) {
        this.productId = productId;
        this.requestId = requestId;
        this.cases = cases;
        this.units = units;
        this.status = SupplierOrderStatus.PENDING;
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    Long getId() {
        return id;
    }

    String getProductId() {
        return productId;
    }

    String getBuyerRef() {
        return buyerRef;
    }

    void setBuyerRef(String buyerRef) {
        this.buyerRef = buyerRef;
    }

    String getRequestId() {
        return requestId;
    }

    String getPoNumber() {
        return poNumber;
    }

    void setPoNumber(String poNumber) {
        this.poNumber = poNumber;
    }

    int getCases() {
        return cases;
    }

    int getUnits() {
        return units;
    }

    SupplierOrderStatus getStatus() {
        return status;
    }

    void setStatus(SupplierOrderStatus status) {
        this.status = status;
    }
}
