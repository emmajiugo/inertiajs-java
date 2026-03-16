package io.inertia.javalin;

import java.util.Map;

/**
 * ThreadLocal holder for validation errors, bridging Javalin's session
 * to the framework-agnostic SharedPropsResolver.
 */
final class InertiaErrorsHolder {

    private static final ThreadLocal<Map<String, String>> ERRORS = new ThreadLocal<>();

    private InertiaErrorsHolder() {}

    static void set(Map<String, String> errors) {
        ERRORS.set(errors);
    }

    static Map<String, String> getAndClear() {
        Map<String, String> errors = ERRORS.get();
        ERRORS.remove();
        return errors;
    }
}
