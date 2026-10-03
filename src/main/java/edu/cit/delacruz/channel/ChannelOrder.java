package edu.cit.delacruz.channel;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * Maps one Tiangge order to the order it became in our own system. The
 * unique constraint on tiangeOrderId (see schema) is what makes "exactly
 * one order per Tiangge order" hold even across a redelivered feed event:
 * seeing the same tiangeOrderId again means re-sending the SAME decision
 * (which the manual says is always safe) instead of creating a second
 * order.
 * <p>
 * status mirrors our own Order.status vocabulary directly (CONFIRMED,
 * REJECTED, BACKORDERED, CANCELLED) - no separate vocabulary to keep in
 * sync, since this table only ever needs to answer "what did we decide,
 * and what's our order's id" for this one Tiangge order.
 */
@Entity
@Table(name = "channel_orders")
class ChannelOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tiangge_order_id", nullable = false, unique = true)
    private String tiangeOrderId;

    @Column(name = "shop_order_id", nullable = false)
    private Long shopOrderId;

    @Column(nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected ChannelOrder() {
        // JPA
    }

    ChannelOrder(String tiangeOrderId, Long shopOrderId, String status) {
        this.tiangeOrderId = tiangeOrderId;
        this.shopOrderId = shopOrderId;
        this.status = status;
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

    String getTiangeOrderId() {
        return tiangeOrderId;
    }

    Long getShopOrderId() {
        return shopOrderId;
    }

    String getStatus() {
        return status;
    }

    void setStatus(String status) {
        this.status = status;
    }
}
