package io.github.emmajiugo.inertia.core;

import java.util.HashMap;
import java.util.Map;

public class StubInertiaRequest implements InertiaRequest {

    private final Map<String, String> headers = new HashMap<>();
    private String method = "GET";
    private String requestUrl = "http://localhost/test";
    private String requestPath = "/test";

    @Override public String getHeader(String name) { return headers.get(name); }
    @Override public String getMethod() { return method; }
    @Override public String getRequestUrl() { return requestUrl; }
    @Override public String getRequestPath() { return requestPath; }

    public StubInertiaRequest withHeader(String name, String value) {
        headers.put(name, value);
        return this;
    }

    public StubInertiaRequest withMethod(String method) {
        this.method = method;
        return this;
    }

    public StubInertiaRequest withRequestUrl(String url) {
        this.requestUrl = url;
        return this;
    }

    public StubInertiaRequest withRequestPath(String path) {
        this.requestPath = path;
        return this;
    }

    public StubInertiaRequest asInertia() {
        return withHeader("X-Inertia", "true");
    }
}
