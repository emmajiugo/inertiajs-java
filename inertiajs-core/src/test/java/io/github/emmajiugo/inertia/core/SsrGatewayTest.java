package io.github.emmajiugo.inertia.core;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SsrGatewayTest {

    // Test template with both placeholders
    private static final String RAW_TEMPLATE =
            "<html><head>@inertiaHead</head><body>@inertia</body></html>";

    // A TemplateResolver that supports getRawTemplate()
    private final TemplateResolver templateResolver = new TemplateResolver() {
        @Override
        public String resolve(String pageJson) {
            return RAW_TEMPLATE
                    .replace("@inertiaHead", "")
                    .replace("@inertia",
                            "<div id=\"app\" data-page=\"" + pageJson + "\"></div>");
        }

        @Override
        public String getRawTemplate() {
            return RAW_TEMPLATE;
        }
    };

    // Stub SsrClient that returns a fixed response
    private final SsrClient successClient = pageJson -> new SsrResponse(
            List.of("<title>SSR Title</title>", "<meta name=\"desc\" content=\"ssr\">"),
            "<div id=\"app\" data-page=\"" + pageJson + "\"><h1>Server Rendered</h1></div>"
    );

    // Stub SsrClient that always throws
    private final SsrClient failingClient = pageJson -> {
        throw new IOException("SSR server unavailable");
    };

    @Test
    void returnsSsrHtmlWhenEnabled() throws IOException {
        var gateway = new SsrGateway(templateResolver, successClient, false);

        String html = gateway.resolve("{\"component\":\"Test\"}", true);

        assertThat(html).contains("<title>SSR Title</title>");
        assertThat(html).contains("<meta name=\"desc\" content=\"ssr\">");
        assertThat(html).contains("<h1>Server Rendered</h1>");
        assertThat(html).doesNotContain("@inertia");
        assertThat(html).doesNotContain("@inertiaHead");
    }

    @Test
    void fallsBackToCsrOnFailureWhenFailOnErrorFalse() throws IOException {
        var gateway = new SsrGateway(templateResolver, failingClient, false);

        String html = gateway.resolve("{\"component\":\"Test\"}", true);

        // Should have CSR div, not SSR content
        assertThat(html).contains("<div id=\"app\" data-page=\"");
        assertThat(html).doesNotContain("Server Rendered");
        assertThat(html).doesNotContain("@inertiaHead");
    }

    @Test
    void propagatesExceptionWhenFailOnErrorTrue() {
        var gateway = new SsrGateway(templateResolver, failingClient, true);

        assertThatThrownBy(() -> gateway.resolve("{\"component\":\"Test\"}", true))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("SSR server unavailable");
    }

    @Test
    void delegatesToTemplateResolverWhenSsrDisabled() throws IOException {
        // Use successClient but disable SSR — should never call it
        var gateway = new SsrGateway(templateResolver, successClient, false);

        String html = gateway.resolve("{\"component\":\"Test\"}", false);

        assertThat(html).contains("<div id=\"app\" data-page=\"");
        assertThat(html).doesNotContain("SSR Title");
        assertThat(html).doesNotContain("Server Rendered");
    }

    @Test
    void delegatesToTemplateResolverWhenNoSsrClient() throws IOException {
        var gateway = new SsrGateway(templateResolver, null, false);

        String html = gateway.resolve("{\"component\":\"Test\"}", true);

        assertThat(html).contains("<div id=\"app\" data-page=\"");
        assertThat(html).doesNotContain("@inertiaHead");
    }

    @Test
    void handlesTemplateWithoutInertiaHeadPlaceholder() throws IOException {
        // Template without @inertiaHead
        TemplateResolver noHeadResolver = new TemplateResolver() {
            @Override
            public String resolve(String pageJson) {
                return "<html><body><div id=\"app\" data-page=\"" + pageJson + "\"></div></body></html>";
            }

            @Override
            public String getRawTemplate() {
                return "<html><body>@inertia</body></html>";
            }
        };

        var gateway = new SsrGateway(noHeadResolver, successClient, false);

        String html = gateway.resolve("{\"component\":\"Test\"}", true);

        // Body should still be injected
        assertThat(html).contains("<h1>Server Rendered</h1>");
        // No @inertiaHead to replace — that's fine
        assertThat(html).doesNotContain("@inertia");
    }
}
