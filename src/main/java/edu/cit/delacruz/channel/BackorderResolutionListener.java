package edu.cit.delacruz.channel;

import java.util.Comparator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.TransactionalEventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;

import edu.cit.delacruz.inventory.service.InsufficientStockException;
import edu.cit.delacruz.shop.model.Order;
import edu.cit.delacruz.shop.service.OrderService;
import edu.cit.delacruz.supplier.event.ReorderDeliveredEvent;

/**
 * Task 6: "when the delivery arrives, your app reserves the stock and
 * resolves the backorder to ACCEPTED, or to CANCELLED if it still can't be
 * filled." Inventory's own SupplierDeliveryListener reacts to the same
 * event with a plain (synchronous, same-transaction) @EventListener, so it
 * always restocks before this AFTER_COMMIT listener even runs - no
 * explicit ordering needed between the two.
 * <p>
 * Processes waiting backorders for this product oldest-first. One
 * delivery might not cover every waiting order; whichever ones a smaller
 * delivery can't reach simply stay BACKORDERED for the next one, rather
 * than everything failing together.
 */
@Component
class BackorderResolutionListener {

    private static final Logger log = LoggerFactory.getLogger(BackorderResolutionListener.class);

    private final OrderService orderService;
    private final ChannelOrderRepository channelOrderRepository;
    private final TiangeClient client;

    BackorderResolutionListener(OrderService orderService, ChannelOrderRepository channelOrderRepository,
            TiangeClient client) {
        this.orderService = orderService;
        this.channelOrderRepository = channelOrderRepository;
        this.client = client;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReorderDelivered(ReorderDeliveredEvent event) {
        orderService.getAllOrders().stream()
                .filter(order -> OrderService.STATUS_BACKORDERED.equals(order.getStatus()))
                .filter(order -> order.getItems().stream()
                        .anyMatch(item -> item.getProductId().equals(event.getProductId())))
                .sorted(Comparator.comparing(Order::getCreatedAt))
                .forEach(this::resolve);
    }

    private void resolve(Order order) {
        String decision;
        try {
            orderService.fulfillBackorder(order.getOrderId());
            decision = "ACCEPTED";
        } catch (InsufficientStockException notEnoughAfterAll) {
            orderService.cancelBackorder(order.getOrderId());
            decision = "CANCELLED";
        }

        channelOrderRepository.findByShopOrderId(order.getOrderId()).ifPresentOrElse(
                channelOrder -> {
                    channelOrder.setStatus(
                            "ACCEPTED".equals(decision) ? OrderService.STATUS_CONFIRMED : OrderService.STATUS_CANCELLED);
                    channelOrderRepository.save(channelOrder);
                    try {
                        client.resolve(channelOrder.getTiangeOrderId(), decision);
                    } catch (RuntimeException e) {
                        log.warn("Resolving backorder for Tiangge order {} failed: {}",
                                channelOrder.getTiangeOrderId(), e.getMessage());
                    }
                },
                () -> log.warn("Order {} resolved as {} but has no Tiangge mapping - was it ever a Tiangge order?",
                        order.getOrderId(), decision)
        );
    }
}
