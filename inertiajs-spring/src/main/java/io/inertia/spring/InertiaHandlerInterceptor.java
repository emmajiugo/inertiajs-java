package io.inertia.spring;

import io.inertia.core.InertiaEngine;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

public class InertiaHandlerInterceptor implements HandlerInterceptor {

    private final InertiaEngine engine;

    public InertiaHandlerInterceptor(InertiaEngine engine) {
        this.engine = engine;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) {
        response.setHeader("Vary", "X-Inertia");

        var req = new SpringInertiaRequest(request);
        var res = new SpringInertiaResponse(response);

        if (engine.isVersionMismatch(req)) {
            engine.forceVersionMismatchResponse(req, res);
            return false;
        }

        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response,
                           Object handler, ModelAndView modelAndView) {
        var req = new SpringInertiaRequest(request);
        if (engine.needsRedirectUpgrade(req, response.getStatus())) {
            response.setStatus(303);
        }
    }
}
