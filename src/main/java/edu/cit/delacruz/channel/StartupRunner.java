package edu.cit.delacruz.channel;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import edu.cit.delacruz.AppInstance;
import edu.cit.delacruz.channel.TiangeJson.HeartbeatRequest;
import edu.cit.delacruz.channel.TiangeJson.ListingRequest;
import edu.cit.delacruz.channel.TiangeJson.StockEntry;
import edu.cit.delacruz.inventory.model.InventoryItem;
import edu.cit.delacruz.inventory.service.InventoryService;
import edu.cit.delacruz.supplier.SupplierGateway;

/**
 * Runs once, after the app context is up but before HeartbeatJob or
 * TiangeFeedPoller take their first tick (both carry an initialDelay for
 * exactly this reason - see their own comments). Order matters here: the
 * manual requires the first heartbeat before any other Tiangge call, and
 * "your shop goes live once a running instance has sent a heartbeat and
 * you have published at least one listing."
 * <p>
 * Failures here are logged, not thrown - a Tiangge hiccup at the exact
 * moment the app boots shouldn't stop the whole app (including the web UI
 * and LegacySupply side) from starting. HeartbeatJob's first scheduled
 * tick will retry the heartbeat; if listings failed to publish, that
 * currently needs a restart to retry, which is a reasonable limitation
 * for how rarely this runs rather than a background job in its own right.
 */
@Component
class StartupRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupRunner.class);

    private final TiangeClient client;
    private final TiangeProperties properties;
    private final AppInstance appInstance;
    private final InventoryService inventoryService;
    private final SupplierGateway supplierGateway;

    StartupRunner(TiangeClient client, TiangeProperties properties, AppInstance appInstance,
            InventoryService inventoryService, SupplierGateway supplierGateway) {
        this.client = client;
        this.properties = properties;
        this.appInstance = appInstance;
        this.inventoryService = inventoryService;
        this.supplierGateway = supplierGateway;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            client.heartbeat(new HeartbeatRequest(properties.appName(), appInstance.startedAt().toString(), 0));
            log.info("Tiangge: first heartbeat sent, instance {}", appInstance.id());
        } catch (RuntimeException e) {
            log.error("Tiangge: initial heartbeat failed, will retry on schedule: {}", e.getMessage());
        }

        List<ListingRequest> listings = properties.listedProductIds().stream()
                .map(this::toListing)
                .filter(listing -> listing != null)
                .toList();
        try {
            client.publishListings(listings);
            log.info("Tiangge: published {} listings", listings.size());
        } catch (RuntimeException e) {
            log.error("Tiangge: publishing listings failed: {}", e.getMessage());
            return; // no point publishing stock for listings Tiangge never saw
        }

        List<StockEntry> stock = properties.listedProductIds().stream()
                .map(this::toStockEntry)
                .filter(entry -> entry != null)
                .toList();
        try {
            client.publishStock(stock);
            log.info("Tiangge: published initial stock for {} products", stock.size());
        } catch (RuntimeException e) {
            log.error("Tiangge: publishing initial stock failed: {}", e.getMessage());
        }
    }

    private ListingRequest toListing(String productId) {
        InventoryItem item = inventoryService.getItem(productId);
        String supplierSku = supplierGateway.supplierSkuFor(productId).orElse(null);
        if (item == null || supplierSku == null) {
            log.warn("Tiangge: skipping listing for {} - no inventory item or no supplier mapping", productId);
            return null;
        }
        return new ListingRequest(productId, item.getName(), supplierSku);
    }

    private StockEntry toStockEntry(String productId) {
        InventoryItem item = inventoryService.getItem(productId);
        return item == null ? null : new StockEntry(productId, item.getStock());
    }
}
