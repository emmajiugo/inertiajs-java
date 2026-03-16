package io.inertia.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Default TemplateResolver that loads an HTML file from the classpath
 * and replaces the {@code @inertia} placeholder with the Inertia root div.
 */
public final class ClasspathTemplateResolver implements TemplateResolver {

    private static final String PLACEHOLDER = "@inertia";
    private final String template;

    public ClasspathTemplateResolver(String classpathLocation) {
        this.template = loadTemplate(classpathLocation);
    }

    @Override
    public String resolve(String pageJson) {
        String div = "<div id=\"app\" data-page=\"" + escapeHtml(pageJson) + "\"></div>";
        return template.replace(PLACEHOLDER, div);
    }

    private static String escapeHtml(String input) {
        return input
                .replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("'", "&#39;");
    }

    private static String loadTemplate(String location) {
        try (InputStream is = ClasspathTemplateResolver.class.getClassLoader()
                .getResourceAsStream(location)) {
            if (is == null) {
                throw new IllegalArgumentException(
                        "Template not found on classpath: " + location);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load template: " + location, e);
        }
    }
}
