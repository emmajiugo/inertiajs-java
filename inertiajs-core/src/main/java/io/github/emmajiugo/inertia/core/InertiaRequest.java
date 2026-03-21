package io.github.emmajiugo.inertia.core;

/**
 * Abstraction over an HTTP request. Framework adapters implement this
 * to wrap their native request type (e.g. HttpServletRequest, Javalin Context).
 */
public interface InertiaRequest {

    String getHeader(String name);

    String getMethod();

    String getRequestUrl();

    String getRequestPath();
}
