package io.github.emmajiugo.inertia.javalin;

import io.github.emmajiugo.inertia.core.InertiaEngine;
import io.github.emmajiugo.inertia.core.Precognition;
import io.github.emmajiugo.inertia.core.RenderOptions;
import io.javalin.http.Context;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Javalin convenience wrapper around {@link InertiaEngine}.
 */
public class Inertia {

    static final String ERRORS_SESSION_KEY = "io.inertia.errors";
    static final String FLASH_SESSION_KEY = "io.inertia.flash";

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

    /**
     * Redirect back with validation errors. Stores errors in the session
     * so they are available on the next request via shared props.
     *
     * <p>Accepts {@code Map<String, String>} (single message per field) or
     * {@code Map<String, List<String>>} (multiple messages per field).
     */
    public void redirectWithErrors(Context ctx, String url, Map<String, ?> errors) {
        ctx.sessionAttribute(ERRORS_SESSION_KEY, errors);
        ctx.status(303);
        ctx.header("Location", url);
    }

    /**
     * Flash a key-value pair. Available as {@code page.props.flash}
     * on the next request, then automatically cleared.
     */
    @SuppressWarnings("unchecked")
    public void flash(Context ctx, String key, Object value) {
        Map<String, Object> flash = ctx.sessionAttribute(FLASH_SESSION_KEY);
        if (flash == null) {
            flash = new HashMap<>();
        }
        flash.put(key, value);
        ctx.sessionAttribute(FLASH_SESSION_KEY, flash);
    }

    /**
     * Flash multiple key-value pairs.
     */
    public void flash(Context ctx, Map<String, Object> data) {
        data.forEach((key, value) -> flash(ctx, key, value));
    }

    public void location(Context ctx, String url) {
        engine.location(new JavalinInertiaResponse(ctx), url);
    }

    public void render(Context ctx, String component, Map<String, Object> props,
                       RenderOptions options) throws IOException {
        engine.render(
                new JavalinInertiaRequest(ctx),
                new JavalinInertiaResponse(ctx),
                component, props, options);
    }

    public boolean isPrecognitionRequest(Context ctx) {
        return Precognition.isPrecognitionRequest(new JavalinInertiaRequest(ctx));
    }

    public void precognitionRespond(Context ctx, Map<String, String> errors) throws IOException {
        Precognition.respond(new JavalinInertiaResponse(ctx), errors,
                engine.getConfig().getJsonSerializer());
    }

    public InertiaEngine getEngine() {
        return engine;
    }
}
