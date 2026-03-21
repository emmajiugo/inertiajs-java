package io.github.emmajiugo.inertia.spring;

import io.github.emmajiugo.inertia.core.InertiaRequest;
import io.github.emmajiugo.inertia.core.SharedPropsResolver;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;

/**
 * Reads validation errors and flash data from the session and shares them as props.
 * Both are consumed (removed from session) after being read.
 *
 * <ul>
 *   <li>Errors: available as {@code page.props.errors}</li>
 *   <li>Flash: available as {@code page.props.flash}</li>
 * </ul>
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

        Map<String, Object> shared = new HashMap<>();

        // Validation errors
        Object errors = session.getAttribute(Inertia.ERRORS_SESSION_KEY);
        if (errors instanceof Map<?, ?>) {
            session.removeAttribute(Inertia.ERRORS_SESSION_KEY);
            shared.put("errors", errors);
        }

        // Flash data
        Object flash = session.getAttribute(Inertia.FLASH_SESSION_KEY);
        if (flash instanceof Map<?, ?>) {
            session.removeAttribute(Inertia.FLASH_SESSION_KEY);
            shared.put("flash", flash);
        }

        return shared;
    }
}
