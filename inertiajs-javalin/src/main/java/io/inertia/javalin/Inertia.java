package io.inertia.javalin;

import io.inertia.core.InertiaEngine;
import io.javalin.http.Context;

import java.io.IOException;
import java.util.Map;

/**
 * Javalin convenience wrapper around {@link InertiaEngine}.
 */
public class Inertia {

    static final String ERRORS_SESSION_KEY = "io.inertia.errors";

    private final InertiaEngine engine;

    public Inertia(InertiaEngine engine) {
        this.engine = engine;
    }

    public void render(Context ctx, String component, Map<String, Object> props) throws IOException {
        engine.render(
                new JavalinInertiaRequest(ctx),
                new JavalinInertiaResponse(ctx),
                component, props);
    }

    public void render(Context ctx, String component) throws IOException {
        render(ctx, component, Map.of());
    }

    public void redirect(Context ctx, String url) {
        ctx.status(303);
        ctx.header("Location", url);
    }

    public void redirectWithErrors(Context ctx, String url, Map<String, String> errors) {
        ctx.sessionAttribute(ERRORS_SESSION_KEY, errors);
        ctx.status(303);
        ctx.header("Location", url);
    }

    public void location(Context ctx, String url) {
        engine.location(new JavalinInertiaResponse(ctx), url);
    }

    public InertiaEngine getEngine() {
        return engine;
    }
}
