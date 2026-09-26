package edu.cit.delacruz.supplier;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Function;

import org.springframework.stereotype.Component;

/**
 * Owns everything LegacySupply-specific that isn't the wire format: the
 * session lifecycle, and the timeout/retry rules Part D asks for. Nothing
 * above this class in the supplier module ever sees an HTTP status code.
 */
@Component
class LegacySupplyClient {

    // INTEGRATION.md measured sessions lasting 2m44s-3m39s. Refresh well
    // before the shortest of those so a call rarely races an expiry, and
    // still react to E-AUTH-* below on the rare one that slips through.
    private static final Duration SESSION_MAX_AGE = Duration.ofSeconds(90);
    private static final Set<String> AUTH_RETRY_CODES = Set.of("E-AUTH-02", "E-AUTH-03", "E-AUTH-07");
    private static final Set<String> TRANSIENT_CODES = Set.of("E-SYS-50", "E-SYS-99");
    private static final int MAX_ATTEMPTS = 3;
    private static final long[] BACKOFF_MS = {300, 900};

    private final SupplierProperties properties;
    private final HttpClient http;
    private final Object sessionLock = new Object();
    private volatile String sessionToken;
    private volatile Instant sessionIssuedAt;

    LegacySupplyClient(SupplierProperties properties) {
        this.properties = properties;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.timeoutMs()))
                .build();
    }

    LegacyXml.OrderAck placeOrder(String sku, int qty, String buyerRef, String requestId) {
        return call(() -> {
            HttpRequest request = HttpRequest.newBuilder(properties.baseUri().resolve("purchase-orders"))
                    .timeout(Duration.ofMillis(properties.timeoutMs()))
                    .header("Content-Type", "application/xml")
                    .header("X-LS-Session", sessionToken())
                    .header("X-Request-Id", requestId)
                    .POST(BodyPublishers.ofString(LegacyXml.purchaseOrderRequest(sku, qty, buyerRef)))
                    .build();
            return send(request, LegacyXml::parseOrderAck);
        });
    }

    LegacyXml.OrderStatus getStatus(String poNumber) {
        return call(() -> {
            HttpRequest request = HttpRequest.newBuilder(
                            properties.baseUri().resolve("purchase-orders/" + poNumber))
                    .timeout(Duration.ofMillis(properties.timeoutMs()))
                    .header("X-LS-Session", sessionToken())
                    .GET()
                    .build();
            return send(request, LegacyXml::parseOrderStatus);
        });
    }

    private <T> T call(Callable<T> attempt) {
        RuntimeException last = null;
        for (int i = 1; i <= MAX_ATTEMPTS; i++) {
            try {
                return attempt.call();
            } catch (RateLimitedException | PermanentException e) {
                throw e; // quota errors and our-own-bug errors never get better on retry
            } catch (Exception e) {
                last = (e instanceof RuntimeException re) ? re : new TransientException(e.getMessage(), e);
                if (i < MAX_ATTEMPTS) {
                    sleep(BACKOFF_MS[Math.min(i - 1, BACKOFF_MS.length - 1)]);
                }
            }
        }
        throw last;
    }

    private <T> T send(HttpRequest request, Function<String, T> parser) throws Exception {
        HttpResponse<String> response = http.send(request, BodyHandlers.ofString());
        int status = response.statusCode();
        if (status == 200 || status == 201) {
            return parser.apply(response.body());
        }
        LegacyXml.LsError error = LegacyXml.parseError(response.body());
        if (AUTH_RETRY_CODES.contains(error.code())) {
            synchronized (sessionLock) {
                sessionToken = null; // force a fresh sign-in on the next attempt
            }
            throw new TransientException(error.code() + ": " + error.message());
        }
        if ("E-RATE-03".equals(error.code())) {
            throw new RateLimitedException(error.message());
        }
        if (TRANSIENT_CODES.contains(error.code())) {
            throw new TransientException(error.code() + ": " + error.message());
        }
        // Everything else (bad SKU, bad qty, malformed doc, a reused
        // X-Request-Id whose content changed) is our own bug, not a
        // hiccup - retrying it fails the same way three times over.
        throw new PermanentException(error.code(), error.message());
    }

    private String sessionToken() {
        synchronized (sessionLock) {
            if (sessionToken == null
                    || Duration.between(sessionIssuedAt, Instant.now()).compareTo(SESSION_MAX_AGE) > 0) {
                signIn();
            }
            return sessionToken;
        }
    }

    private void signIn() {
        try {
            HttpRequest request = HttpRequest.newBuilder(properties.baseUri().resolve("auth/token"))
                    .timeout(Duration.ofMillis(properties.timeoutMs()))
                    .header("Content-Type", "application/xml")
                    .POST(BodyPublishers.ofString(
                            LegacyXml.authRequest(properties.clientId(), properties.apiKey())))
                    .build();
            HttpResponse<String> response = http.send(request, BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                LegacyXml.LsError error = LegacyXml.parseError(response.body());
                throw new PermanentException(error.code(), error.message());
            }
            sessionToken = LegacyXml.parseAuthResponse(response.body()).sessionToken();
            // Our own clock at receipt time, not the server's IssuedAt - no
            // need to trust or resync against a remote clock for a value
            // we only ever compare against Instant.now() locally.
            sessionIssuedAt = Instant.now();
        } catch (PermanentException e) {
            throw e;
        } catch (Exception e) {
            throw new TransientException("Sign-in failed: " + e.getMessage(), e);
        }
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

    static class RateLimitedException extends RuntimeException {
        RateLimitedException(String message) {
            super(message);
        }
    }
}
