package io.github.emmajiugo.inertia.core;

@FunctionalInterface
public interface TemplateResolver {

    String resolve(String pageJson);

    /**
     * Returns the raw template string before any placeholder substitution.
     * Used by SSR to perform its own placeholder replacements.
     *
     * <p>Default implementation throws {@link UnsupportedOperationException}.
     * {@link ClasspathTemplateResolver} overrides this.
     */
    default String getRawTemplate() {
        throw new UnsupportedOperationException(
                "This TemplateResolver does not support raw template access");
    }
}
