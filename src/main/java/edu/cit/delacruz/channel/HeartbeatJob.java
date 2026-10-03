package edu.cit.delacruz.channel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import edu.cit.delacruz.AppInstance;
import edu.cit.delacruz.channel.TiangeJson.HeartbeatRequest;

@Component
class HeartbeatJob {

    private static final Logger log = LoggerFactory.getLogger(HeartbeatJob.class);

    private final TiangeClient client;
    private final TiangeProperties properties;
    private final AppInstance appInstance;

    HeartbeatJob(TiangeClient client, TiangeProperties properties, AppInstance appInstance) {
        this.client = client;
        this.properties = properties;
        this.appInstance = appInstance;
    }

    // initialDelay = the interval itself, so StartupRunner's own first
    // heartbeat (sent synchronously, before any scheduled job starts
    // ticking) is always the one Tiangge sees first.
    @Scheduled(fixedDelayString = "${app.channel.heartbeat-interval-ms:30000}",
            initialDelayString = "${app.channel.heartbeat-interval-ms:30000}")
    void sendHeartbeat() {
        try {
            client.heartbeat(new HeartbeatRequest(
                    properties.appName(), appInstance.startedAt().toString(), appInstance.uptimeSeconds()));
        } catch (RuntimeException e) {
            log.warn("Tiangge heartbeat failed, will retry next tick: {}", e.getMessage());
        }
    }
}
