package edu.cit.delacruz.channel;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Every JSON shape the Tiangge API uses, in one file - the JSON equivalent
 * of LegacyXml for the supplier module. Nothing outside this class, and
 * TiangeClient, needs to know these exist.
 */
final class TiangeJson {

    private TiangeJson() {
    }

    record ListingRequest(String sellerSku, String title, String supplierSku) {
    }

    record StockEntry(String sellerSku, int available) {
    }

    record HeartbeatRequest(String appName, String startedAt, long uptimeSeconds) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record HeartbeatResponse(String serverTime, int nextHeartbeatSeconds) {
    }

    record DecisionRequest(String decision, String shopOrderId, String reason) {
    }

    record ResolutionRequest(String status) {
    }

    record CancellationConfirmRequest(boolean restocked) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record FeedLine(String sellerSku, int qty) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Buyer(String name, String city) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record FeedEvent(
            long seq,
            String eventId,
            String type,
            String orderId,
            String placedAt,
            String decisionDeadline,
            List<FeedLine> lines,
            Buyer buyer,
            String cancelledAt,
            String confirmDeadline
    ) {
        boolean isOrderPlaced() {
            return "ORDER_PLACED".equals(type);
        }

        boolean isOrderCancelled() {
            return "ORDER_CANCELLED".equals(type);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record FeedResponse(List<FeedEvent> events, long nextCursor) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ErrorResponse(String error, String message) {
    }
}
