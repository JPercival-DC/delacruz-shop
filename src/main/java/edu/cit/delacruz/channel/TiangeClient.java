package edu.cit.delacruz.channel;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;

import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

import edu.cit.delacruz.AppInstance;
import edu.cit.delacruz.channel.TiangeJson.CancellationConfirmRequest;
import edu.cit.delacruz.channel.TiangeJson.DecisionRequest;
import edu.cit.delacruz.channel.TiangeJson.ErrorResponse;
import edu.cit.delacruz.channel.TiangeJson.FeedResponse;
import edu.cit.delacruz.channel.TiangeJson.HeartbeatRequest;
import edu.cit.delacruz.channel.TiangeJson.HeartbeatResponse;
import edu.cit.delacruz.channel.TiangeJson.ListingRequest;
import edu.cit.delacruz.channel.TiangeJson.ResolutionRequest;
import edu.cit.delacruz.channel.TiangeJson.StockEntry;

/**
 * Raw HTTP for the Tiangge seller API: headers, JSON (de)serialization, and
 * the timeout/retry rule the manual asks for ("retry [503s] with a short
 * backoff instead of giving up"; everything else is a bug on our end, not
 * a hiccup). No session to manage, unlike LegacySupply - auth here is a
 * plain header on every call.
 */
@Component
class TiangeClient {

    private static final int MAX_ATTEMPTS = 3;
    private static final long[] BACKOFF_MS = {300, 900};

    private final TiangeProperties properties;
    private final AppInstance appInstance;
    private final ObjectMapper json;
    private final HttpClient http;

    TiangeClient(TiangeProperties properties, AppInstance appInstance, ObjectMapper json) {
        this.properties = properties;
        this.appInstance = appInstance;
        this.json = json;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.timeoutMs()))
                .build();
    }

    HeartbeatResponse heartbeat(HeartbeatRequest body) {
        return call(() -> send("POST", "instances/heartbeat", body, HeartbeatResponse.class));
    }

    void publishListings(List<ListingRequest> listings) {
        call(() -> send("PUT", "listings", listings, Object.class));
    }

    void publishStock(List<StockEntry> entries) {
        call(() -> send("PUT", "stock", entries, Object.class));
    }

    FeedResponse pollFeed(long after, int limit) {
        return call(() -> send("GET", "feed?after=" + after + "&limit=" + limit, null, FeedResponse.class));
    }

    void decide(String orderId, String decision, String shopOrderId, String reason) {
        call(() -> send("POST", "orders/" + orderId + "/decision",
                new DecisionRequest(decision, shopOrderId, reason), Object.class));
    }

    void resolve(String orderId, String status) {
        call(() -> send("POST", "orders/" + orderId + "/resolution", new ResolutionRequest(status), Object.class));
    }

    void confirmCancellation(String orderId, boolean restocked) {
        call(() -> send("POST", "orders/" + orderId + "/cancellation",
                new CancellationConfirmRequest(restocked), Object.class));
    }

    private <T> T call(Callable<T> attempt) {
        RuntimeException last = null;
        for (int i = 1; i <= MAX_ATTEMPTS; i++) {
            try {
                return attempt.call();
            } catch (PermanentException e) {
                throw e; // our own bug - retrying won't fix it
            } catch (Exception e) {
                last = (e instanceof RuntimeException re) ? re : new TransientException(e.getMessage(), e);
                if (i < MAX_ATTEMPTS) {
                    sleep(BACKOFF_MS[Math.min(i - 1, BACKOFF_MS.length - 1)]);
                }
            }
        }
        throw last;
    }

    private <T> T send(String method, String path, Object body, Class<T> responseType) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(properties.baseUri().resolve(path))
                .timeout(Duration.ofMillis(properties.timeoutMs()))
                .header("X-Client-Id", properties.clientId())
                .header("Authorization", "Bearer " + properties.apiKey())
                .header("X-Client-Instance", appInstance.id().toString());

        if (body != null) {
            builder.header("Content-Type", "application/json")
                    .method(method, BodyPublishers.ofString(json.writeValueAsString(body)));
        } else {
            builder.method(method, BodyPublishers.noBody());
        }

        HttpResponse<String> response = http.send(builder.build(), BodyHandlers.ofString());
        int status = response.statusCode();

        if (status >= 200 && status < 300) {
            if (responseType == Object.class || response.body() == null || response.body().isBlank()) {
                return null;
            }
            return json.readValue(response.body(), responseType);
        }

        String errorCode = "http_" + status;
        String message = response.body();
        try {
            ErrorResponse error = json.readValue(response.body(), ErrorResponse.class);
            errorCode = error.error();
            message = error.message();
        } catch (Exception ignoredParseFailure) {
            // Body wasn't the documented {error, message} shape - fall back to the raw text above.
        }

        if (status == 503 || "unavailable".equals(errorCode)) {
            throw new TransientException(errorCode + ": " + message);
        }
        // decision_conflict, not_backordered, not_cancelled, unknown_*_sku,
        // invalid_request, invalid_credentials, order_not_found... all of
        // these mean something's wrong with what WE sent or how we're
        // tracking state, not a flaky server - retrying won't help.
        throw new PermanentException(errorCode, message);
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TransientException("Interrupted during backoff", e);
        }
    }

    static class TransientException extends RuntimeException {
        TransientException(String message) {
            super(message);
        }

        TransientException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    static class PermanentException extends RuntimeException {
        PermanentException(String code, String message) {
            super(code + ": " + message);
        }
    }
}
