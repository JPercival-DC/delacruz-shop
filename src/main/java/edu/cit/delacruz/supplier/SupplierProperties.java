package edu.cit.delacruz.supplier;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Everything the supplier module needs from configuration, in one place.
 * The product -> (SupplierSku, PackSize) mapping comes straight from
 * INTEGRATION.md's Part B table. A live GET /catalog call would need its
 * own slice of the request quota and the mapping doesn't change between
 * runs, so it's config here, not a lookup.
 */
@Component
class SupplierProperties {

    private final URI baseUri;
    private final String clientId;
    private final String apiKey;
    private final int timeoutMs;
    private final int reorderTarget;
    private final Map<String, SkuInfo> items = new HashMap<>();

    SupplierProperties(
            @Value("${app.supplier.base-url}") String baseUrl,
            @Value("${app.supplier.client-id}") String clientId,
            @Value("${app.supplier.api-key}") String apiKey,
            @Value("${app.supplier.timeout-ms:3000}") int timeoutMs,
            @Value("${app.supplier.reorder-target:30}") int reorderTarget,
            @Value("${app.supplier.items}") String itemsCsv
    ) {
        this.baseUri = URI.create(baseUrl.endsWith("/") ? baseUrl : baseUrl + "/");
        this.clientId = clientId;
        this.apiKey = apiKey;
        this.timeoutMs = timeoutMs;
        this.reorderTarget = reorderTarget;
        // "P100:BHD-1152:10,P200:BHD-5972:6,..." - see app.supplier.items
        for (String entry : itemsCsv.split(",")) {
            String[] parts = entry.trim().split(":");
            items.put(parts[0], new SkuInfo(parts[1], Integer.parseInt(parts[2])));
        }
    }

    record SkuInfo(String sku, int packSize) {
    }

    URI baseUri() {
        return baseUri;
    }

    String clientId() {
        return clientId;
    }

    String apiKey() {
        return apiKey;
    }

    int timeoutMs() {
        return timeoutMs;
    }

    int reorderTarget() {
        return reorderTarget;
    }

    SkuInfo lookup(String productId) {
        SkuInfo info = items.get(productId);
        if (info == null) {
            throw new IllegalArgumentException("No LegacySupply mapping configured for product " + productId);
        }
        return info;
    }
}
