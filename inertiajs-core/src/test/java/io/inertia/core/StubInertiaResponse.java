package io.inertia.core;

import java.util.LinkedHashMap;
import java.util.Map;

public class StubInertiaResponse implements InertiaResponse {

    private int status;
    private final Map<String, String> headers = new LinkedHashMap<>();
    private String contentType;
    private String body;

    @Override public void setStatus(int status) { this.status = status; }
    @Override public void setHeader(String name, String value) { headers.put(name, value); }
    @Override public void setContentType(String contentType) { this.contentType = contentType; }
    @Override public void writeBody(String body) { this.body = body; }

    public int getStatus() { return status; }
    public String getHeader(String name) { return headers.get(name); }
    public String getContentType() { return contentType; }
    public String getBody() { return body; }
}
