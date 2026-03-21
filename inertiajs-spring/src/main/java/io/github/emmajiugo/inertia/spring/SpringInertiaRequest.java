package io.github.emmajiugo.inertia.spring;

import io.github.emmajiugo.inertia.core.InertiaRequest;
import jakarta.servlet.http.HttpServletRequest;

public class SpringInertiaRequest implements InertiaRequest {

    private final HttpServletRequest req;

    public SpringInertiaRequest(HttpServletRequest req) {
        this.req = req;
    }

    @Override
    public String getHeader(String name) {
        return req.getHeader(name);
    }

    @Override
    public String getMethod() {
        return req.getMethod();
    }

    @Override
    public String getRequestUrl() {
        String url = req.getRequestURL().toString();
        String query = req.getQueryString();
        return query != null ? url + "?" + query : url;
    }

    @Override
    public String getRequestPath() {
        return req.getRequestURI();
    }
}
