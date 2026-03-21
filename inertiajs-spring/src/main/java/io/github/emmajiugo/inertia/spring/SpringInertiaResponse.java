package io.github.emmajiugo.inertia.spring;

import io.github.emmajiugo.inertia.core.InertiaResponse;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

public class SpringInertiaResponse implements InertiaResponse {

    private final HttpServletResponse res;

    public SpringInertiaResponse(HttpServletResponse res) {
        this.res = res;
    }

    @Override
    public void setStatus(int status) {
        res.setStatus(status);
    }

    @Override
    public void setHeader(String name, String value) {
        res.setHeader(name, value);
    }

    @Override
    public void setContentType(String contentType) {
        res.setContentType(contentType);
    }

    @Override
    public void writeBody(String body) throws IOException {
        res.getWriter().write(body);
    }
}
