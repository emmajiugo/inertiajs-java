package io.github.emmajiugo.inertia.spring;

import io.github.emmajiugo.inertia.core.InertiaEngine;
import io.github.emmajiugo.inertia.core.Precognition;
import io.github.emmajiugo.inertia.core.RenderOptions;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Spring-aware convenience wrapper around {@link InertiaEngine}.
 * Eliminates the need to manually wrap HttpServletRequest/Response in controllers.
 */
public class Inertia {

    static final String ERRORS_SESSION_KEY = "io.inertia.errors";
    static final String FLASH_SESSION_KEY = "io.inertia.flash";

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
     *
     * <p>Accepts {@code Map<String, String>} (single message per field) or
     * {@code Map<String, List<String>>} (multiple messages per field).
     */
    public void redirectWithErrors(HttpServletRequest req, HttpServletResponse res,
                                   String url, Map<String, ?> errors) {
        HttpSession session = req.getSession();
        session.setAttribute(ERRORS_SESSION_KEY, errors);
        res.setStatus(303);
        res.setHeader("Location", url);
    }

    /**
     * Flash a key-value pair to the session. Available as {@code page.props.flash}
     * on the next request, then automatically cleared.
     */
    @SuppressWarnings("unchecked")
    public void flash(HttpServletRequest req, String key, Object value) {
        HttpSession session = req.getSession();
        Map<String, Object> flash = (Map<String, Object>) session.getAttribute(FLASH_SESSION_KEY);
        if (flash == null) {
            flash = new HashMap<>();
        }
        flash.put(key, value);
        session.setAttribute(FLASH_SESSION_KEY, flash);
    }

    /**
     * Flash multiple key-value pairs to the session.
     */
    public void flash(HttpServletRequest req, Map<String, Object> data) {
        data.forEach((key, value) -> flash(req, key, value));
    }

    public void location(HttpServletResponse res, String url) {
        engine.location(new SpringInertiaResponse(res), url);
    }

    public void render(HttpServletRequest req, HttpServletResponse res,
                       String component, Map<String, Object> props,
                       RenderOptions options) throws IOException {
        engine.render(
                new SpringInertiaRequest(req),
                new SpringInertiaResponse(res),
                component, props, options);
    }

    public boolean isPrecognitionRequest(HttpServletRequest req) {
        return Precognition.isPrecognitionRequest(new SpringInertiaRequest(req));
    }

    public void precognitionRespond(HttpServletResponse res,
                                    Map<String, ?> errors) throws IOException {
        Precognition.respond(new SpringInertiaResponse(res), errors,
                engine.getConfig().getJsonSerializer());
    }

    public InertiaEngine getEngine() {
        return engine;
    }
}