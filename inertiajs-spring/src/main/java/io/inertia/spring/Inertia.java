package io.inertia.spring;

import io.inertia.core.InertiaEngine;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.Map;

/**
 * Spring-aware convenience wrapper around {@link InertiaEngine}.
 * Eliminates the need to manually wrap HttpServletRequest/Response in controllers.
 */
public class Inertia {

    static final String ERRORS_SESSION_KEY = "io.inertia.errors";

    private final InertiaEngine engine;

    public Inertia(InertiaEngine engine) {
        this.engine = engine;
    }

    public void render(HttpServletRequest req, HttpServletResponse res,
                       String component, Map<String, Object> props) throws IOException {
        engine.render(
                new SpringInertiaRequest(req),
                new SpringInertiaResponse(res),
                component, props);
    }

    public void render(HttpServletRequest req, HttpServletResponse res,
                       String component) throws IOException {
        render(req, res, component, Map.of());
    }

    public void redirect(HttpServletResponse res, String url) {
        res.setStatus(303);
        res.setHeader("Location", url);
    }

    /**
     * Redirect back with validation errors. Stores errors in the session
     * so they are available on the next request via shared props.
     */
    public void redirectWithErrors(HttpServletRequest req, HttpServletResponse res,
                                   String url, Map<String, String> errors) {
        HttpSession session = req.getSession();
        session.setAttribute(ERRORS_SESSION_KEY, errors);
        res.setStatus(303);
        res.setHeader("Location", url);
    }

    public void location(HttpServletResponse res, String url) {
        engine.location(new SpringInertiaResponse(res), url);
    }

    public InertiaEngine getEngine() {
        return engine;
    }
}