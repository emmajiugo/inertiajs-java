package io.inertia.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Default {@link SsrClient} that communicates with an Inertia SSR server
 * over HTTP using {@link java.net.http.HttpClient}.
 *
 * <p>Thread-safe: the underlying {@code HttpClient} is created once and reused.
 */
public final class HttpSsrClient implements SsrClient {

    private final HttpClient httpClient;
    private final URI endpoint;
    private final Duration timeout;
    private final ObjectMapper objectMapper;

    public HttpSsrClient(String url, Duration timeout) {
        this(url, timeout, JsonMapper.builder().build());
    }

    public HttpSsrClient(String url, Duration timeout, ObjectMapper objectMapper) {
        this.endpoint = URI.create(url.endsWith("/") ? url + "render" : url + "/render");
        this.timeout = timeout;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .build();
    }

    @Override
    public SsrResponse render(String pageJson) throws IOException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(endpoint)
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(pageJson))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("SSR request interrupted", e);
        }

        if (response.statusCode() != 200) {
            throw new IOException("SSR server returned status " + response.statusCode());
        }

        return parseResponse(response.body());
    }

    private SsrResponse parseResponse(String json) throws IOException {
        JsonNode root = objectMapper.readTree(json);

        List<String> head = new ArrayList<>();
        JsonNode headNode = root.get("head");
        if (headNode != null && headNode.isArray()) {
            for (JsonNode tag : headNode) {
                head.add(tag.asText());
            }
        }

        String body = root.has("body") ? root.get("body").asText() : "";

        return new SsrResponse(List.copyOf(head), body);
    }
}
