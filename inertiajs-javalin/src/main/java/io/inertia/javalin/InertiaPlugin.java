package io.inertia.javalin;

import io.inertia.core.InertiaEngine;
import io.javalin.config.JavalinConfig;
import java.util.Map;

/**
 * Javalin 7 plugin that registers Inertia middleware (version check + redirect upgrade)
 * and provides the {@link Inertia} convenience wrapper.
 *
 * Usage:
 * <pre>
 * var engine = new InertiaEngine(config);
 * var plugin = new InertiaPlugin(engine);
 *
 * var app = Javalin.create(cfg -> {
 *     plugin.configure(cfg);
 *
 *     cfg.routes.get("/", ctx -> plugin.inertia().render(ctx, "Home"));
 *     cfg.routes.get("/events", ctx -> plugin.inertia().render(ctx, "Events/Index", Map.of(...)));
 * });
 *
 * app.start(8080);
 * </pre>
 */
public class InertiaPlugin {

    private final InertiaEngine engine;
    private final Inertia inertia;

    public InertiaPlugin(InertiaEngine engine) {
        this.engine = engine;
        this.inertia = new Inertia(engine);
    }

    public Inertia inertia() {
        return inertia;
    }

    /**
     * Configures Javalin with Inertia middleware.
     * Call this inside {@code Javalin.create(cfg -> plugin.configure(cfg))}.
     */
    @SuppressWarnings("unchecked")
    public void configure(JavalinConfig config) {
        // Shared props resolver for validation errors via ThreadLocal
        engine.addSharedPropsResolver(req -> {
            Map<String, String> errors = InertiaErrorsHolder.getAndClear();
            if (errors != null && !errors.isEmpty()) {
                return Map.of("errors", errors);
            }
            return Map.of();
        });

        // Before: set Vary header + version check + read session errors
        config.routes.before(ctx -> {
            ctx.header("Vary", "X-Inertia");

            var req = new JavalinInertiaRequest(ctx);
            var res = new JavalinInertiaResponse(ctx);

            if (engine.isVersionMismatch(req)) {
                engine.forceVersionMismatchResponse(req, res);
                ctx.skipRemainingHandlers();
                return;
            }

            // Move session errors to ThreadLocal for the shared props resolver
            Map<String, String> errors = ctx.sessionAttribute(Inertia.ERRORS_SESSION_KEY);
            if (errors != null) {
                ctx.sessionAttribute(Inertia.ERRORS_SESSION_KEY, null);
                InertiaErrorsHolder.set(errors);
            }
        });

        // After: redirect upgrade 302 → 303
        config.routes.after(ctx -> {
            var req = new JavalinInertiaRequest(ctx);
            if (engine.needsRedirectUpgrade(req, ctx.status().getCode())) {
                ctx.status(303);
            }
        });
    }
}
