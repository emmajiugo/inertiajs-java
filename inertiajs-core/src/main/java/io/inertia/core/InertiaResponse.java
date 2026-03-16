package io.inertia.core;

import java.io.IOException;

/**
 * Abstraction over an HTTP response. Framework adapters implement this
 * to wrap their native response type (e.g. HttpServletResponse, Javalin Context).
 */
public interface InertiaResponse {

    void setStatus(int status);

    void setHeader(String name, String value);

    void setContentType(String contentType);

    void writeBody(String body) throws IOException;
}
