package io.github.emmajiugo.inertia.core;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

/**
 * Helper for Precognition (real-time form validation) requests.
 *
 * Precognition allows the client to validate form data before submission.
 * The client sends a request with {@code Precognition: true} header, the server
 * runs validation and responds with 204 (success) or 422 (errors).
 *
 * Usage in a controller:
 * <pre>
 * if (Precognition.isPrecognitionRequest(req)) {
 *     Map&lt;String, String&gt; errors = validate(body);
 *     Precognition.respond(res, errors);
 *     return;
 * }
 * </pre>
 */
public final class Precognition {

    private Precognition() {}

    /**
     * Check if this is a Precognition validation request.
     */
    public static boolean isPrecognitionRequest(InertiaRequest req) {
        return "true".equals(req.getHeader("Precognition"));
    }

    /**
     * Get the list of fields the client wants to validate.
     * Returns null if the header is not present.
     */
    public static String[] getValidateOnly(InertiaRequest req) {
        String header = req.getHeader("Precognition-Validate-Only");
        if (header == null || header.isBlank()) return null;
        return Arrays.stream(header.split(","))
                .map(String::trim)
                .toArray(String[]::new);
    }

    /**
     * Send a Precognition response. If errors is empty, sends 204 (success).
     * Otherwise sends 422 with the errors as JSON.
     */
    public static void respond(InertiaResponse res, Map<String, String> errors,
                               JsonSerializer serializer) throws IOException {
        Objects.requireNonNull(res, "res must not be null");
        Objects.requireNonNull(serializer, "serializer must not be null");
        res.setHeader("Precognition", "true");
        res.setHeader("Vary", "Precognition");

        if (errors == null || errors.isEmpty()) {
            res.setStatus(204);
            res.setHeader("Precognition-Success", "true");
        } else {
            res.setStatus(422);
            res.setContentType("application/json");
            res.writeBody(serializer.serialize(Map.of("errors", errors)));
        }
    }
}
