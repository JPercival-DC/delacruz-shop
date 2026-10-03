package edu.cit.delacruz.channel;

import java.net.URI;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class TiangeProperties {

    private final URI baseUri;
    private final String clientId;
    private final String apiKey;
    private final String appName;
    private final int timeoutMs;
    private final List<String> listedProductIds;

    TiangeProperties(
            @Value("${app.channel.base-url}") String baseUrl,
            @Value("${app.channel.client-id}") String clientId,
            @Value("${app.channel.api-key}") String apiKey,
            @Value("${app.channel.app-name}") String appName,
            @Value("${app.channel.timeout-ms:3000}") int timeoutMs,
            @Value("${app.channel.listed-products}") String listedProductsCsv
    ) {
        this.baseUri = URI.create(baseUrl.endsWith("/") ? baseUrl : baseUrl + "/");
        this.clientId = clientId;
        this.apiKey = apiKey;
        this.appName = appName;
        this.timeoutMs = timeoutMs;
        this.listedProductIds = List.of(listedProductsCsv.split(","));
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

    String appName() {
        return appName;
    }

    int timeoutMs() {
        return timeoutMs;
    }

    List<String> listedProductIds() {
        return listedProductIds;
    }
}
