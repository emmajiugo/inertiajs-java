package io.github.emmajiugo.inertia.javalin;

import io.github.emmajiugo.inertia.core.InertiaResponse;
import io.javalin.http.Context;

public class JavalinInertiaResponse implements InertiaResponse {

    private final Context ctx;

    public JavalinInertiaResponse(Context ctx) {
        this.ctx = ctx;
    }

    @Override
    public void setStatus(int status) {
        ctx.status(status);
    }

    @Override
    public void setHeader(String name, String value) {
        ctx.header(name, value);
    }

    @Override
    public void setContentType(String contentType) {
        ctx.contentType(contentType);
    }

    @Override
    public void writeBody(String body) {
        ctx.result(body);
    }
}
