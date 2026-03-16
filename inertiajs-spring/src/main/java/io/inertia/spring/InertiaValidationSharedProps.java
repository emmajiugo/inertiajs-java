package io.inertia.spring;

import io.inertia.core.InertiaRequest;
import io.inertia.core.SharedPropsResolver;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.Map;

/**
 * Reads validation errors from the session (stored by
 * {@link Inertia#redirectWithErrors}) and shares them as the "errors" prop.
 * Errors are consumed (removed from session) after being read.
 */
public class InertiaValidationSharedProps implements SharedPropsResolver {

    @Override
    public Map<String, Object> resolve(InertiaRequest request) {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return Map.of();
        }

        HttpServletRequest servletRequest = attrs.getRequest();
        HttpSession session = servletRequest.getSession(false);
        if (session == null) {
            return Map.of();
        }

        Object errors = session.getAttribute(Inertia.ERRORS_SESSION_KEY);
        if (errors instanceof Map<?, ?>) {
            session.removeAttribute(Inertia.ERRORS_SESSION_KEY);
            return Map.of("errors", errors);
        }

        return Map.of();
    }
}