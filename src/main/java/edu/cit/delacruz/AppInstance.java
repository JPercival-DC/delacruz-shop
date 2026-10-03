package edu.cit.delacruz;

import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * One random UUID generated when the app starts, sent as X-Client-Instance
 * on every call to both Tiangge and LegacySupply (Lab 4 Task 1: "send the
 * same header on your LegacySupply calls too, so both systems can tell
 * which running copy made them"). Lives at the application root rather
 * than inside either module, since it's a cross-cutting technical concern,
 * not a domain concept either module owns.
 */
@Component
public class AppInstance {

    private static final Logger log = LoggerFactory.getLogger(AppInstance.class);

    private final UUID id = UUID.randomUUID();
    private final Instant startedAt = Instant.now();

    public AppInstance() {
        log.info("Instance starting: {}", id);
    }

    public UUID id() {
        return id;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public long uptimeSeconds() {
        return Instant.now().getEpochSecond() - startedAt.getEpochSecond();
    }
}
