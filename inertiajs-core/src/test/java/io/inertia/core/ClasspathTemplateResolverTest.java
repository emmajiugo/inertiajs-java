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
}
