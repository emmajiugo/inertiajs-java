package io.inertia.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Default TemplateResolver that loads an HTML file from the classpath
 * and replaces the {@code @inertia} placeholder with the Inertia root div.
 *
 * By default, the template is cached at construction time. Pass {@code cache=false}
 * to re-read the file on every request (useful with Spring Boot DevTools).
 */
public final class ClasspathTemplateResolver implements TemplateResolver {

    private static final String PLACEHOLDER = "@inertia";
    private static final String HEAD_PLACEHOLDER = "@inertiaHead";
    private final String classpathLocation;
    private final String cachedTemplate;

    public ClasspathTemplateResolver(String classpathLocation) {
        this(classpathLocation, true);
    }

    public ClasspathTemplateResolver(String classpathLocation, boolean cache) {
        this.classpathLocation = classpathLocation;
        this.cachedTemplate = cache ? loadTemplate(classpathLocation) : null;
    }

    @Override
    public String resolve(String pageJson) {
        String template = cachedTemplate != null ? cachedTemplate : loadTemplate(classpathLocation);
        String div = "<div id=\"app\" data-page=\"" + escapeHtml(pageJson) + "\"></div>";
        return template
                .replace(HEAD_PLACEHOLDER, "")
                .replace(PLACEHOLDER, div);
    }

    @Override
    public String getRawTemplate() {
        return cachedTemplate != null ? cachedTemplate : loadTemplate(classpathLocation);
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
