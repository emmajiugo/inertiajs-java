package io.github.emmajiugo.inertia.core;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Routes HTML rendering through SSR or CSR based on configuration.
 *
 * <p>When no {@link SsrClient} is configured (null), this gateway always
 * delegates directly to the wrapped {@link TemplateResolver}, regardless
 * of the {@code ssrEnabled} flag. This means SSR has zero impact on
 * existing apps that don't set up an SSR client — the gateway acts as
 * a transparent pass-through to the template resolver.
 *
 * <p>Thread-safe: this class is stateless.
 */
public final class SsrGateway {

    private static final Logger logger = Logger.getLogger(SsrGateway.class.getName());
    private static final String HEAD_PLACEHOLDER = "@inertiaHead";
    private static final String BODY_PLACEHOLDER = "@inertia";

    private final TemplateResolver templateResolver;
    private final SsrClient ssrClient;
    private final boolean failOnError;

    public SsrGateway(TemplateResolver templateResolver,
                      SsrClient ssrClient,
                      boolean failOnError) {
        this.templateResolver = templateResolver;
        this.ssrClient = ssrClient;
        this.failOnError = failOnError;
    }

    /**
     * Resolve the page JSON to an HTML string, using SSR if enabled and available.
     *
     * @param pageJson   the serialized Inertia page object
     * @param ssrEnabled whether SSR should be attempted for this request
     * @return the full HTML response
     * @throws IOException if SSR fails and {@code failOnError} is true,
     *                     or if the template resolver fails
     */
    public String resolve(String pageJson, boolean ssrEnabled) throws IOException {
        if (ssrClient != null && ssrEnabled) {
            try {
                SsrResponse ssr = ssrClient.render(pageJson);
                return buildSsrHtml(ssr);
            } catch (IOException e) {
                if (failOnError) throw e;
                logger.log(Level.WARNING, "SSR failed, falling back to CSR", e);
            }
        }
        return templateResolver.resolve(pageJson);
    }

    private String buildSsrHtml(SsrResponse ssr) {
        String template = templateResolver.getRawTemplate();
        String head = String.join("\n", ssr.head());
        return template
                .replace(HEAD_PLACEHOLDER, head)
                .replace(BODY_PLACEHOLDER, ssr.body());
    }
}
