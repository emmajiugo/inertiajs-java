package io.inertia.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClasspathTemplateResolverTest {

    @Test
    void replacesPlaceholderWithDiv() {
        var resolver = new ClasspathTemplateResolver("templates/test.html");

        String html = resolver.resolve("{\"component\":\"Test\"}");

        assertThat(html).contains("<div id=\"app\" data-page=\"");
        assertThat(html).contains("</div>");
        assertThat(html).contains("<html>");
    }

    @Test
    void escapesHtmlInPageJson() {
        var resolver = new ClasspathTemplateResolver("templates/test.html");

        String html = resolver.resolve("{\"title\":\"<script>alert('xss')</script>\"}");

        assertThat(html).doesNotContain("<script>");
        assertThat(html).contains("&lt;script&gt;");
        assertThat(html).contains("&quot;");
    }

    @Test
    void throwsForMissingTemplate() {
        assertThatThrownBy(() -> new ClasspathTemplateResolver("nonexistent.html"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void stripsInertiaHeadPlaceholderInCsrResolve() {
        var resolver = new ClasspathTemplateResolver("templates/test-ssr.html");

        String html = resolver.resolve("{\"component\":\"Test\"}");

        assertThat(html).doesNotContain("@inertiaHead");
        assertThat(html).contains("<div id=\"app\" data-page=\"");
    }

    @Test
    void getRawTemplateReturnsTemplateWithPlaceholdersIntact() {
        var resolver = new ClasspathTemplateResolver("templates/test-ssr.html");

        String raw = resolver.getRawTemplate();

        assertThat(raw).contains("@inertiaHead");
        assertThat(raw).contains("@inertia");
    }

    @Test
    void getRawTemplateThrowsForDefaultInterface() {
        // A custom TemplateResolver that doesn't override getRawTemplate()
        TemplateResolver custom = pageJson -> "<html>" + pageJson + "</html>";

        assertThatThrownBy(custom::getRawTemplate)
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
