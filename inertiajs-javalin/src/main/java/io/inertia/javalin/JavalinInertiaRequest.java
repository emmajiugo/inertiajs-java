package io.inertia.javalin;

import io.inertia.core.InertiaRequest;
import io.javalin.http.Context;

public class JavalinInertiaRequest implements InertiaRequest {

    private final Context ctx;

    public JavalinInertiaRequest(Context ctx) {
        this.ctx = ctx;
    }

    @Override
    public String getHeader(String name) {
        return ctx.header(name);
    }

    @Override
    public String getMethod() {
        return ctx.method().name();
    }

    @Override
    public String getRequestUrl() {
        return ctx.fullUrl();
    }

    @Override
    public String getRequestPath() {
        return ctx.path();
    }
}
