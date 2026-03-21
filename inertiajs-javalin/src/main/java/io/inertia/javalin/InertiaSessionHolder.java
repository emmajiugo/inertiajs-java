package io.inertia.javalin;

import java.util.Map;

/**
 * ThreadLocal holder for session data (errors + flash), bridging Javalin's session
 * to the framework-agnostic SharedPropsResolver.
 */
final class InertiaSessionHolder {

    private static final ThreadLocal<Map<String, String>> ERRORS = new ThreadLocal<>();
    private static final ThreadLocal<Map<String, Object>> FLASH = new ThreadLocal<>();

    private InertiaSessionHolder() {}

    static void setErrors(Map<String, String> errors) { ERRORS.set(errors); }

    static Map<String, String> getAndClearErrors() {
        Map<String, String> errors = ERRORS.get();
        ERRORS.remove();
        return errors;
    }

    static void setFlash(Map<String, Object> flash) { FLASH.set(flash); }

    static Map<String, Object> getAndClearFlash() {
        Map<String, Object> flash = FLASH.get();
        FLASH.remove();
        return flash;
    }
}
