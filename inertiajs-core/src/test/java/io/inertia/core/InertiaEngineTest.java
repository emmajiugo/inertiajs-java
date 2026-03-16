package io.inertia.core;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.inertia.core.props.InertiaProps;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InertiaEngineTest {

    private InertiaEngine engine;
    private final ObjectMapper mapper = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    @BeforeEach
    void setUp() {
        InertiaConfig config = InertiaConfig.builder()
                .version("1.0.0")
                .templateResolver(pageJson ->
                        "<html><body><div id=\"app\" data-page=\"" + pageJson + "\"></div></body></html>")
                .build();
        engine = new InertiaEngine(config);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parsePageProps(String json) throws IOException {
        Map<String, Object> page = mapper.readValue(json, MAP_TYPE);
        return (Map<String, Object>) page.get("props");
    }

    private Map<String, Object> parsePage(String json) throws IOException {
        return mapper.readValue(json, MAP_TYPE);
    }

    // ── Rendering ────────────────────────────────────────────────────

    @Nested
    class Rendering {

        @Test
        void returnsJsonWhenInertiaHeaderPresent() throws IOException {
            var req = new StubInertiaRequest().asInertia();
            var res = new StubInertiaResponse();

            engine.render(req, res, "Users/Index", Map.of("users", "data"));

            assertThat(res.getStatus()).isEqualTo(200);
            assertThat(res.getContentType()).isEqualTo("application/json");
            assertThat(res.getHeader("X-Inertia")).isEqualTo("true");

            var page = parsePage(res.getBody());
            assertThat(page.get("component")).isEqualTo("Users/Index");
            assertThat(page.get("url")).isEqualTo("/test");
            assertThat(page.get("version")).isEqualTo("1.0.0");
            assertThat(parsePageProps(res.getBody())).containsKey("users");
        }

        @Test
        void returnsHtmlWhenNoInertiaHeader() throws IOException {
            var req = new StubInertiaRequest();
            var res = new StubInertiaResponse();

            engine.render(req, res, "Users/Index", Map.of("users", "data"));

            assertThat(res.getStatus()).isEqualTo(200);
            assertThat(res.getContentType()).isEqualTo("text/html; charset=utf-8");
            assertThat(res.getBody()).contains("<html>");
            assertThat(res.getBody()).contains("data-page=");
        }

        @Test
        void alwaysSetsVaryHeader() throws IOException {
            var req = new StubInertiaRequest().asInertia();
            var res = new StubInertiaResponse();
            engine.render(req, res, "Test", Map.of());
            assertThat(res.getHeader("Vary")).isEqualTo("X-Inertia");

            var req2 = new StubInertiaRequest();
            var res2 = new StubInertiaResponse();
            engine.render(req2, res2, "Test", Map.of());
            assertThat(res2.getHeader("Vary")).isEqualTo("X-Inertia");
        }

        @Test
        void usesRequestPathAsUrl() throws IOException {
            var req = new StubInertiaRequest().asInertia().withRequestPath("/events/42");
            var res = new StubInertiaResponse();

            engine.render(req, res, "Events/Show", Map.of());

            var page = parsePage(res.getBody());
            assertThat(page.get("url")).isEqualTo("/events/42");
        }
    }

    // ── Shared Props ─────────────────────────────────────────────────

    @Nested
    class SharedProps {

        @Test
        void mergesSharedPropsWithPageProps() throws IOException {
            engine.addSharedPropsResolver(req -> Map.of("appName", "TestApp"));

            var req = new StubInertiaRequest().asInertia();
            var res = new StubInertiaResponse();

            engine.render(req, res, "Test", Map.of("title", "Hello"));

            var props = parsePageProps(res.getBody());
            assertThat(props).containsEntry("appName", "TestApp");
            assertThat(props).containsEntry("title", "Hello");
        }

        @Test
        void pagePropsOverrideSharedProps() throws IOException {
            engine.addSharedPropsResolver(req -> Map.of("key", "shared"));

            var req = new StubInertiaRequest().asInertia();
            var res = new StubInertiaResponse();

            engine.render(req, res, "Test", Map.of("key", "page"));

            var props = parsePageProps(res.getBody());
            assertThat(props).containsEntry("key", "page");
        }

        @SuppressWarnings("unchecked")
        @Test
        void alwaysIncludesErrorsProp() throws IOException {
            var req = new StubInertiaRequest().asInertia();
            var res = new StubInertiaResponse();

            engine.render(req, res, "Test", Map.of());

            var props = parsePageProps(res.getBody());
            assertThat(props).containsKey("errors");
            assertThat((Map<String, Object>) props.get("errors")).isEmpty();
        }
    }

    // ── Version Mismatch ─────────────────────────────────────────────

    @Nested
    class VersionMismatch {

        @Test
        void detectsMismatchOnGetRequests() {
            var req = new StubInertiaRequest().asInertia()
                    .withHeader("X-Inertia-Version", "old-version");
            assertThat(engine.isVersionMismatch(req)).isTrue();
        }

        @Test
        void noMismatchWhenVersionsMatch() {
            var req = new StubInertiaRequest().asInertia()
                    .withHeader("X-Inertia-Version", "1.0.0");
            assertThat(engine.isVersionMismatch(req)).isFalse();
        }

        @Test
        void noMismatchForNonGetRequests() {
            var req = new StubInertiaRequest().asInertia()
                    .withMethod("POST")
                    .withHeader("X-Inertia-Version", "old-version");
            assertThat(engine.isVersionMismatch(req)).isFalse();
        }

        @Test
        void noMismatchForNonInertiaRequests() {
            var req = new StubInertiaRequest()
                    .withHeader("X-Inertia-Version", "old-version");
            assertThat(engine.isVersionMismatch(req)).isFalse();
        }

        @Test
        void forceVersionMismatchReturns409() {
            var req = new StubInertiaRequest()
                    .withRequestUrl("http://localhost/events");
            var res = new StubInertiaResponse();

            engine.forceVersionMismatchResponse(req, res);

            assertThat(res.getStatus()).isEqualTo(409);
            assertThat(res.getHeader("X-Inertia-Location")).isEqualTo("http://localhost/events");
        }
    }

    // ── Redirect Upgrade ─────────────────────────────────────────────

    @Nested
    class RedirectUpgrade {

        @Test
        void upgradesFor302OnPut() {
            var req = new StubInertiaRequest().asInertia().withMethod("PUT");
            assertThat(engine.needsRedirectUpgrade(req, 302)).isTrue();
        }

        @Test
        void upgradesFor302OnPatch() {
            var req = new StubInertiaRequest().asInertia().withMethod("PATCH");
            assertThat(engine.needsRedirectUpgrade(req, 302)).isTrue();
        }

        @Test
        void upgradesFor302OnDelete() {
            var req = new StubInertiaRequest().asInertia().withMethod("DELETE");
            assertThat(engine.needsRedirectUpgrade(req, 302)).isTrue();
        }

        @Test
        void doesNotUpgradeForGet() {
            var req = new StubInertiaRequest().asInertia().withMethod("GET");
            assertThat(engine.needsRedirectUpgrade(req, 302)).isFalse();
        }

        @Test
        void doesNotUpgradeForNon302() {
            var req = new StubInertiaRequest().asInertia().withMethod("PUT");
            assertThat(engine.needsRedirectUpgrade(req, 301)).isFalse();
        }

        @Test
        void doesNotUpgradeForNonInertiaRequests() {
            var req = new StubInertiaRequest().withMethod("PUT");
            assertThat(engine.needsRedirectUpgrade(req, 302)).isFalse();
        }
    }

    // ── External Redirect (location) ─────────────────────────────────

    @Nested
    class ExternalRedirect {

        @Test
        void locationReturns409WithHeader() {
            var res = new StubInertiaResponse();
            engine.location(res, "https://external.com/page");
            assertThat(res.getStatus()).isEqualTo(409);
            assertThat(res.getHeader("X-Inertia-Location")).isEqualTo("https://external.com/page");
        }
    }

    // ── Partial Reloads ──────────────────────────────────────────────

    @Nested
    class PartialReloads {

        @Test
        void onlyIncludesRequestedProps() throws IOException {
            var req = new StubInertiaRequest().asInertia()
                    .withHeader("X-Inertia-Partial-Component", "Test")
                    .withHeader("X-Inertia-Partial-Data", "title");
            var res = new StubInertiaResponse();

            Map<String, Object> props = new HashMap<>();
            props.put("title", "Hello");
            props.put("description", "World");

            engine.render(req, res, "Test", props);

            var pageProps = parsePageProps(res.getBody());
            assertThat(pageProps).containsKey("title");
            assertThat(pageProps).doesNotContainKey("description");
        }

        @Test
        void exceptExcludesSpecifiedProps() throws IOException {
            var req = new StubInertiaRequest().asInertia()
                    .withHeader("X-Inertia-Partial-Component", "Test")
                    .withHeader("X-Inertia-Partial-Except", "description");
            var res = new StubInertiaResponse();

            Map<String, Object> props = new HashMap<>();
            props.put("title", "Hello");
            props.put("description", "World");

            engine.render(req, res, "Test", props);

            var pageProps = parsePageProps(res.getBody());
            assertThat(pageProps).containsKey("title");
            assertThat(pageProps).doesNotContainKey("description");
        }

        @Test
        void alwaysPropNeverFilteredByOnly() throws IOException {
            var req = new StubInertiaRequest().asInertia()
                    .withHeader("X-Inertia-Partial-Component", "Test")
                    .withHeader("X-Inertia-Partial-Data", "title");
            var res = new StubInertiaResponse();

            Map<String, Object> props = new HashMap<>();
            props.put("title", "Hello");
            props.put("flash", InertiaProps.always("message"));

            engine.render(req, res, "Test", props);

            var pageProps = parsePageProps(res.getBody());
            assertThat(pageProps).containsKey("title");
            assertThat(pageProps).containsEntry("flash", "message");
        }

        @Test
        void alwaysPropNeverFilteredByExcept() throws IOException {
            var req = new StubInertiaRequest().asInertia()
                    .withHeader("X-Inertia-Partial-Component", "Test")
                    .withHeader("X-Inertia-Partial-Except", "flash");
            var res = new StubInertiaResponse();

            Map<String, Object> props = new HashMap<>();
            props.put("title", "Hello");
            props.put("flash", InertiaProps.always("message"));

            engine.render(req, res, "Test", props);

            var pageProps = parsePageProps(res.getBody());
            assertThat(pageProps).containsEntry("flash", "message");
        }

        @Test
        void optionalPropExcludedByDefault() throws IOException {
            var req = new StubInertiaRequest().asInertia();
            var res = new StubInertiaResponse();

            Map<String, Object> props = new HashMap<>();
            props.put("title", "Hello");
            props.put("expensive", InertiaProps.optional(() -> "computed"));

            engine.render(req, res, "Test", props);

            var pageProps = parsePageProps(res.getBody());
            assertThat(pageProps).containsKey("title");
            assertThat(pageProps).doesNotContainKey("expensive");
        }

        @Test
        void optionalPropIncludedWhenExplicitlyRequested() throws IOException {
            var req = new StubInertiaRequest().asInertia()
                    .withHeader("X-Inertia-Partial-Component", "Test")
                    .withHeader("X-Inertia-Partial-Data", "expensive");
            var res = new StubInertiaResponse();

            Map<String, Object> props = new HashMap<>();
            props.put("title", "Hello");
            props.put("expensive", InertiaProps.optional(() -> "computed"));

            engine.render(req, res, "Test", props);

            var pageProps = parsePageProps(res.getBody());
            assertThat(pageProps).containsEntry("expensive", "computed");
        }

        @Test
        void ignoredWhenComponentMismatch() throws IOException {
            var req = new StubInertiaRequest().asInertia()
                    .withHeader("X-Inertia-Partial-Component", "Other")
                    .withHeader("X-Inertia-Partial-Data", "title");
            var res = new StubInertiaResponse();

            Map<String, Object> props = new HashMap<>();
            props.put("title", "Hello");
            props.put("description", "World");

            engine.render(req, res, "Test", props);

            var pageProps = parsePageProps(res.getBody());
            assertThat(pageProps).containsKey("title");
            assertThat(pageProps).containsKey("description");
        }

        @Test
        void errorsAlwaysIncludedInPartialReload() throws IOException {
            var req = new StubInertiaRequest().asInertia()
                    .withHeader("X-Inertia-Partial-Component", "Test")
                    .withHeader("X-Inertia-Partial-Data", "title");
            var res = new StubInertiaResponse();

            engine.render(req, res, "Test", Map.of("title", "Hello"));

            var pageProps = parsePageProps(res.getBody());
            assertThat(pageProps).containsKey("errors");
        }
    }

    // ── Lazy Props ───────────────────────────────────────────────────

    @Nested
    class LazyPropsTests {

        @Test
        void lazyPropIsResolvedInFullRender() throws IOException {
            var req = new StubInertiaRequest().asInertia();
            var res = new StubInertiaResponse();

            Map<String, Object> props = new HashMap<>();
            props.put("data", InertiaProps.lazy(() -> "lazy-value"));

            engine.render(req, res, "Test", props);

            var pageProps = parsePageProps(res.getBody());
            assertThat(pageProps).containsEntry("data", "lazy-value");
        }
    }
}
