package io.github.emmajiugo.inertia.core;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.emmajiugo.inertia.core.props.InertiaProps;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    // ── Deferred Props ───────────────────────────────────────────────

    @Nested
    class DeferredPropsTests {

        @SuppressWarnings("unchecked")
        private Map<String, List<String>> parseDeferredProps(String json) throws IOException {
            Map<String, Object> page = mapper.readValue(json, MAP_TYPE);
            return (Map<String, List<String>>) page.get("deferredProps");
        }

        @Test
        void deferredPropExcludedFromInitialRender() throws IOException {
            var req = new StubInertiaRequest().asInertia();
            var res = new StubInertiaResponse();

            Map<String, Object> props = new HashMap<>();
            props.put("title", "Hello");
            props.put("comments", InertiaProps.defer(() -> List.of("c1", "c2")));

            engine.render(req, res, "Test", props);

            var pageProps = parsePageProps(res.getBody());
            assertThat(pageProps).containsKey("title");
            assertThat(pageProps).doesNotContainKey("comments");
        }

        @Test
        void deferredPropAppearsInDeferredPropsField() throws IOException {
            var req = new StubInertiaRequest().asInertia();
            var res = new StubInertiaResponse();

            Map<String, Object> props = new HashMap<>();
            props.put("title", "Hello");
            props.put("comments", InertiaProps.defer(() -> List.of("c1", "c2")));

            engine.render(req, res, "Test", props);

            var deferred = parseDeferredProps(res.getBody());
            assertThat(deferred).isNotNull();
            assertThat(deferred).containsKey("default");
            assertThat(deferred.get("default")).containsExactly("comments");
        }

        @Test
        void deferredPropsGroupedCorrectly() throws IOException {
            var req = new StubInertiaRequest().asInertia();
            var res = new StubInertiaResponse();

            Map<String, Object> props = new HashMap<>();
            props.put("title", "Hello");
            props.put("comments", InertiaProps.defer(() -> "c"));
            props.put("analytics", InertiaProps.defer(() -> "a"));
            props.put("sidebar", InertiaProps.defer(() -> "s", "sidebar"));

            engine.render(req, res, "Test", props);

            var deferred = parseDeferredProps(res.getBody());
            assertThat(deferred).containsKey("default");
            assertThat(deferred.get("default")).containsExactlyInAnyOrder("comments", "analytics");
            assertThat(deferred).containsKey("sidebar");
            assertThat(deferred.get("sidebar")).containsExactly("sidebar");
        }

        @Test
        void deferredPropsNotInPageObjectWhenNoneExist() throws IOException {
            var req = new StubInertiaRequest().asInertia();
            var res = new StubInertiaResponse();

            engine.render(req, res, "Test", Map.of("title", "Hello"));

            var deferred = parseDeferredProps(res.getBody());
            assertThat(deferred).isNull();
        }

        @Test
        void deferredPropResolvedViaPartialReload() throws IOException {
            var req = new StubInertiaRequest().asInertia()
                    .withHeader("X-Inertia-Partial-Component", "Test")
                    .withHeader("X-Inertia-Partial-Data", "comments");
            var res = new StubInertiaResponse();

            Map<String, Object> props = new HashMap<>();
            props.put("title", "Hello");
            props.put("comments", InertiaProps.defer(() -> List.of("c1", "c2")));

            engine.render(req, res, "Test", props);

            var pageProps = parsePageProps(res.getBody());
            assertThat(pageProps).containsKey("comments");
            @SuppressWarnings("unchecked")
            var comments = (List<String>) pageProps.get("comments");
            assertThat(comments).containsExactly("c1", "c2");
        }

        @Test
        void deferredPropsFieldNotIncludedInPartialReload() throws IOException {
            var req = new StubInertiaRequest().asInertia()
                    .withHeader("X-Inertia-Partial-Component", "Test")
                    .withHeader("X-Inertia-Partial-Data", "comments");
            var res = new StubInertiaResponse();

            Map<String, Object> props = new HashMap<>();
            props.put("title", "Hello");
            props.put("comments", InertiaProps.defer(() -> List.of("c1", "c2")));

            engine.render(req, res, "Test", props);

            var deferred = parseDeferredProps(res.getBody());
            assertThat(deferred).isNull();
        }

        @Test
        void deferredPropSupplierNotCalledOnInitialRender() throws IOException {
            var req = new StubInertiaRequest().asInertia();
            var res = new StubInertiaResponse();

            boolean[] called = {false};
            Map<String, Object> props = new HashMap<>();
            props.put("title", "Hello");
            props.put("expensive", InertiaProps.defer(() -> { called[0] = true; return "data"; }));

            engine.render(req, res, "Test", props);

            assertThat(called[0]).isFalse();
        }

        @Test
        void nestedDeferredPropUseDotNotation() throws IOException {
            var req = new StubInertiaRequest().asInertia();
            var res = new StubInertiaResponse();

            Map<String, Object> inner = new HashMap<>();
            inner.put("permissions", InertiaProps.defer(() -> List.of("read")));
            inner.put("name", "Alice");

            Map<String, Object> props = new HashMap<>();
            props.put("user", inner);

            engine.render(req, res, "Test", props);

            var deferred = parseDeferredProps(res.getBody());
            assertThat(deferred).containsKey("default");
            assertThat(deferred.get("default")).containsExactly("user.permissions");
        }
    }

    // ── Merge Props ──────────────────────────────────────────────────

    @Nested
    class MergePropsTests {

        @SuppressWarnings("unchecked")
        private List<String> parseField(String json, String field) throws IOException {
            Map<String, Object> page = mapper.readValue(json, MAP_TYPE);
            return (List<String>) page.get(field);
        }

        @SuppressWarnings("unchecked")
        private Map<String, String> parseMatchPropsOn(String json) throws IOException {
            Map<String, Object> page = mapper.readValue(json, MAP_TYPE);
            return (Map<String, String>) page.get("matchPropsOn");
        }

        @Test
        void mergePropValueIsResolved() throws IOException {
            var req = new StubInertiaRequest().asInertia();
            var res = new StubInertiaResponse();

            Map<String, Object> props = new HashMap<>();
            props.put("items", InertiaProps.merge(() -> List.of("a", "b")));

            engine.render(req, res, "Test", props);

            var pageProps = parsePageProps(res.getBody());
            assertThat(pageProps).containsKey("items");
        }

        @Test
        void mergePropAppearsInMergePropsField() throws IOException {
            var req = new StubInertiaRequest().asInertia();
            var res = new StubInertiaResponse();

            Map<String, Object> props = new HashMap<>();
            props.put("items", InertiaProps.merge(() -> List.of("a", "b")));

            engine.render(req, res, "Test", props);

            var mergeProps = parseField(res.getBody(), "mergeProps");
            assertThat(mergeProps).containsExactly("items");
        }

        @Test
        void prependPropAppearsInPrependPropsField() throws IOException {
            var req = new StubInertiaRequest().asInertia();
            var res = new StubInertiaResponse();

            Map<String, Object> props = new HashMap<>();
            props.put("items", InertiaProps.prepend(() -> List.of("a", "b")));

            engine.render(req, res, "Test", props);

            var prependProps = parseField(res.getBody(), "prependProps");
            assertThat(prependProps).containsExactly("items");
        }

        @Test
        void deepMergePropAppearsInDeepMergePropsField() throws IOException {
            var req = new StubInertiaRequest().asInertia();
            var res = new StubInertiaResponse();

            Map<String, Object> props = new HashMap<>();
            props.put("data", InertiaProps.deepMerge(() -> Map.of("key", "value")));

            engine.render(req, res, "Test", props);

            var deepMergeProps = parseField(res.getBody(), "deepMergeProps");
            assertThat(deepMergeProps).containsExactly("data");
        }

        @Test
        void matchOnAppearsInMatchPropsOnField() throws IOException {
            var req = new StubInertiaRequest().asInertia();
            var res = new StubInertiaResponse();

            Map<String, Object> props = new HashMap<>();
            props.put("items", InertiaProps.merge(() -> List.of()).matchOn("id"));

            engine.render(req, res, "Test", props);

            var matchOn = parseMatchPropsOn(res.getBody());
            assertThat(matchOn).containsEntry("items", "id");
        }

        @Test
        void noMergeFieldsWhenNoMergeProps() throws IOException {
            var req = new StubInertiaRequest().asInertia();
            var res = new StubInertiaResponse();

            engine.render(req, res, "Test", Map.of("title", "Hello"));

            var mergeProps = parseField(res.getBody(), "mergeProps");
            var prependProps = parseField(res.getBody(), "prependProps");
            var deepMergeProps = parseField(res.getBody(), "deepMergeProps");
            assertThat(mergeProps).isNull();
            assertThat(prependProps).isNull();
            assertThat(deepMergeProps).isNull();
        }

        @Test
        void multipleMergeStrategiesTogether() throws IOException {
            var req = new StubInertiaRequest().asInertia();
            var res = new StubInertiaResponse();

            Map<String, Object> props = new HashMap<>();
            props.put("feed", InertiaProps.merge(() -> List.of("post1")));
            props.put("notifications", InertiaProps.prepend(() -> List.of("n1")));
            props.put("settings", InertiaProps.deepMerge(() -> Map.of("theme", "dark")));

            engine.render(req, res, "Test", props);

            assertThat(parseField(res.getBody(), "mergeProps")).containsExactly("feed");
            assertThat(parseField(res.getBody(), "prependProps")).containsExactly("notifications");
            assertThat(parseField(res.getBody(), "deepMergeProps")).containsExactly("settings");
        }
    }

    // ── Once Props ───────────────────────────────────────────────────

    @Nested
    class OncePropsTests {

        @SuppressWarnings("unchecked")
        private Map<String, Map<String, Object>> parseOnceProps(String json) throws IOException {
            Map<String, Object> page = mapper.readValue(json, MAP_TYPE);
            return (Map<String, Map<String, Object>>) page.get("onceProps");
        }

        @Test
        void oncePropIsResolvedOnFirstRequest() throws IOException {
            var req = new StubInertiaRequest().asInertia();
            var res = new StubInertiaResponse();

            Map<String, Object> props = new HashMap<>();
            props.put("plans", InertiaProps.once(() -> List.of("free", "pro")));

            engine.render(req, res, "Test", props);

            var pageProps = parsePageProps(res.getBody());
            assertThat(pageProps).containsKey("plans");
        }

        @Test
        void oncePropAppearsInOncePropsField() throws IOException {
            var req = new StubInertiaRequest().asInertia();
            var res = new StubInertiaResponse();

            Map<String, Object> props = new HashMap<>();
            props.put("plans", InertiaProps.once(() -> List.of("free", "pro")));

            engine.render(req, res, "Test", props);

            var onceProps = parseOnceProps(res.getBody());
            assertThat(onceProps).containsKey("plans");
            assertThat(onceProps.get("plans").get("prop")).isEqualTo("plans");
            assertThat(onceProps.get("plans").get("expiresAt")).isNull();
        }

        @Test
        void oncePropSkippedWhenInExceptHeader() throws IOException {
            var req = new StubInertiaRequest().asInertia()
                    .withHeader("X-Inertia-Except-Once-Props", "plans");
            var res = new StubInertiaResponse();

            Map<String, Object> props = new HashMap<>();
            props.put("title", "Hello");
            props.put("plans", InertiaProps.once(() -> List.of("free", "pro")));

            engine.render(req, res, "Test", props);

            var pageProps = parsePageProps(res.getBody());
            assertThat(pageProps).containsKey("title");
            assertThat(pageProps).doesNotContainKey("plans");
        }

        @Test
        void oncePropStillInOncePropsMapWhenSkipped() throws IOException {
            var req = new StubInertiaRequest().asInertia()
                    .withHeader("X-Inertia-Except-Once-Props", "plans");
            var res = new StubInertiaResponse();

            Map<String, Object> props = new HashMap<>();
            props.put("plans", InertiaProps.once(() -> List.of("free", "pro")));

            engine.render(req, res, "Test", props);

            var onceProps = parseOnceProps(res.getBody());
            assertThat(onceProps).containsKey("plans");
        }

        @Test
        void oncePropSupplierNotCalledWhenSkipped() throws IOException {
            var req = new StubInertiaRequest().asInertia()
                    .withHeader("X-Inertia-Except-Once-Props", "plans");
            var res = new StubInertiaResponse();

            boolean[] called = {false};
            Map<String, Object> props = new HashMap<>();
            props.put("plans", InertiaProps.once(() -> { called[0] = true; return "data"; }));

            engine.render(req, res, "Test", props);

            assertThat(called[0]).isFalse();
        }

        @Test
        void oncePropWithCustomKey() throws IOException {
            var req = new StubInertiaRequest().asInertia();
            var res = new StubInertiaResponse();

            Map<String, Object> props = new HashMap<>();
            props.put("plans", InertiaProps.once(() -> List.of("free")).as("global_plans"));

            engine.render(req, res, "Test", props);

            var onceProps = parseOnceProps(res.getBody());
            assertThat(onceProps).containsKey("global_plans");
            assertThat(onceProps.get("global_plans").get("prop")).isEqualTo("plans");
        }

        @Test
        void oncePropWithExpiration() throws IOException {
            var req = new StubInertiaRequest().asInertia();
            var res = new StubInertiaResponse();

            Map<String, Object> props = new HashMap<>();
            props.put("plans", InertiaProps.once(() -> List.of("free"))
                    .until(java.time.Instant.ofEpochMilli(1700000000000L)));

            engine.render(req, res, "Test", props);

            var onceProps = parseOnceProps(res.getBody());
            assertThat(onceProps.get("plans").get("expiresAt")).isEqualTo(1700000000000L);
        }

        @Test
        void noOncePropsFieldWhenNoneExist() throws IOException {
            var req = new StubInertiaRequest().asInertia();
            var res = new StubInertiaResponse();

            engine.render(req, res, "Test", Map.of("title", "Hello"));

            var onceProps = parseOnceProps(res.getBody());
            assertThat(onceProps).isNull();
        }
    }

    // ── History Encryption ───────────────────────────────────────────

    @Nested
    class HistoryEncryptionTests {

        @Test
        void encryptHistoryIncludedWhenSet() throws IOException {
            var req = new StubInertiaRequest().asInertia();
            var res = new StubInertiaResponse();

            engine.render(req, res, "Test", Map.of(),
                    RenderOptions.builder().encryptHistory(true).build());

            var page = parsePage(res.getBody());
            assertThat(page.get("encryptHistory")).isEqualTo(true);
        }

        @Test
        void clearHistoryIncludedWhenSet() throws IOException {
            var req = new StubInertiaRequest().asInertia();
            var res = new StubInertiaResponse();

            engine.render(req, res, "Test", Map.of(),
                    RenderOptions.builder().clearHistory(true).build());

            var page = parsePage(res.getBody());
            assertThat(page.get("clearHistory")).isEqualTo(true);
        }

        @Test
        void historyFieldsOmittedByDefault() throws IOException {
            var req = new StubInertiaRequest().asInertia();
            var res = new StubInertiaResponse();

            engine.render(req, res, "Test", Map.of());

            var page = parsePage(res.getBody());
            assertThat(page).doesNotContainKey("encryptHistory");
            assertThat(page).doesNotContainKey("clearHistory");
        }
    }

    // ── Preserve Fragment ────────────────────────────────────────────

    @Nested
    class PreserveFragmentTests {

        @Test
        void preserveFragmentIncludedWhenSet() throws IOException {
            var req = new StubInertiaRequest().asInertia();
            var res = new StubInertiaResponse();

            engine.render(req, res, "Test", Map.of(),
                    RenderOptions.builder().preserveFragment(true).build());

            var page = parsePage(res.getBody());
            assertThat(page.get("preserveFragment")).isEqualTo(true);
        }

        @Test
        void preserveFragmentOmittedByDefault() throws IOException {
            var req = new StubInertiaRequest().asInertia();
            var res = new StubInertiaResponse();

            engine.render(req, res, "Test", Map.of());

            var page = parsePage(res.getBody());
            assertThat(page).doesNotContainKey("preserveFragment");
        }
    }

    // ── Header Trimming ───────────────────────────────────────────────

    @Nested
    class HeaderTrimming {

        @Test
        void partialDataHeaderWithSpaces() throws IOException {
            var req = new StubInertiaRequest().asInertia()
                    .withHeader("X-Inertia-Partial-Component", "Test")
                    .withHeader("X-Inertia-Partial-Data", "title, description");
            var res = new StubInertiaResponse();

            Map<String, Object> props = new HashMap<>();
            props.put("title", "Hello");
            props.put("description", "World");
            props.put("extra", "Ignored");

            engine.render(req, res, "Test", props);

            var pageProps = parsePageProps(res.getBody());
            assertThat(pageProps).containsKey("title");
            assertThat(pageProps).containsKey("description");
            assertThat(pageProps).doesNotContainKey("extra");
        }

        @Test
        void partialExceptHeaderWithSpaces() throws IOException {
            var req = new StubInertiaRequest().asInertia()
                    .withHeader("X-Inertia-Partial-Component", "Test")
                    .withHeader("X-Inertia-Partial-Except", "description, extra");
            var res = new StubInertiaResponse();

            Map<String, Object> props = new HashMap<>();
            props.put("title", "Hello");
            props.put("description", "World");
            props.put("extra", "Ignored");

            engine.render(req, res, "Test", props);

            var pageProps = parsePageProps(res.getBody());
            assertThat(pageProps).containsKey("title");
            assertThat(pageProps).doesNotContainKey("description");
            assertThat(pageProps).doesNotContainKey("extra");
        }

        @Test
        void exceptOncePropsHeaderWithSpaces() throws IOException {
            var req = new StubInertiaRequest().asInertia()
                    .withHeader("X-Inertia-Except-Once-Props", "plans, sidebar");
            var res = new StubInertiaResponse();

            Map<String, Object> props = new HashMap<>();
            props.put("title", "Hello");
            props.put("plans", InertiaProps.once(() -> List.of("free", "pro")));
            props.put("sidebar", InertiaProps.once(() -> List.of("nav")));

            engine.render(req, res, "Test", props);

            var pageProps = parsePageProps(res.getBody());
            assertThat(pageProps).containsKey("title");
            assertThat(pageProps).doesNotContainKey("plans");
            assertThat(pageProps).doesNotContainKey("sidebar");
        }
    }

    // ── Null Validation ───────────────────────────────────────────────

    @Nested
    class NullValidation {

        @Test
        void throwsOnNullRequest() {
            var res = new StubInertiaResponse();
            assertThatThrownBy(() -> engine.render(null, res, "Test", Map.of()))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("req");
        }

        @Test
        void throwsOnNullResponse() {
            var req = new StubInertiaRequest().asInertia();
            assertThatThrownBy(() -> engine.render(req, null, "Test", Map.of()))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("res");
        }

        @Test
        void throwsOnNullComponent() {
            var req = new StubInertiaRequest().asInertia();
            var res = new StubInertiaResponse();
            assertThatThrownBy(() -> engine.render(req, res, null, Map.of()))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("component");
        }

        @Test
        void handlesNullPropsGracefully() throws IOException {
            var req = new StubInertiaRequest().asInertia();
            var res = new StubInertiaResponse();

            engine.render(req, res, "Test", null);

            assertThat(res.getStatus()).isEqualTo(200);
            var pageProps = parsePageProps(res.getBody());
            assertThat(pageProps).containsKey("errors");
        }
    }

    // ── SSR Rendering ─────────────────────────────────────────────────

    @Nested
    class SsrRendering {

        private final SsrClient successSsrClient = pageJson -> new SsrResponse(
                List.of("<title>SSR</title>"),
                "<div id=\"app\" data-page='" + pageJson + "'><h1>SSR Content</h1></div>"
        );

        private TemplateResolver ssrTemplate() {
            return new TemplateResolver() {
                @Override
                public String resolve(String pageJson) {
                    return "<html><head></head><body><div id=\"app\" data-page=\""
                            + pageJson + "\"></div></body></html>";
                }

                @Override
                public String getRawTemplate() {
                    return "<html><head>@inertiaHead</head><body>@inertia</body></html>";
                }
            };
        }

        @Test
        void rendersWithSsrWhenConfigured() throws IOException {
            InertiaConfig config = InertiaConfig.builder()
                    .version("1.0.0")
                    .templateResolver(ssrTemplate())
                    .ssrClient(successSsrClient)
                    .ssrEnabled(true)
                    .build();
            InertiaEngine ssrEngine = new InertiaEngine(config);

            var req = new StubInertiaRequest();
            var res = new StubInertiaResponse();

            ssrEngine.render(req, res, "Home", Map.of("name", "World"));

            assertThat(res.getBody()).contains("<title>SSR</title>");
            assertThat(res.getBody()).contains("SSR Content");
        }

        @Test
        void skipsSsrWhenDisabledViaRenderOptions() throws IOException {
            InertiaConfig config = InertiaConfig.builder()
                    .version("1.0.0")
                    .templateResolver(ssrTemplate())
                    .ssrClient(successSsrClient)
                    .ssrEnabled(true)
                    .build();
            InertiaEngine ssrEngine = new InertiaEngine(config);

            var req = new StubInertiaRequest();
            var res = new StubInertiaResponse();

            ssrEngine.render(req, res, "Home", Map.of(),
                    RenderOptions.builder().ssr(false).build());

            assertThat(res.getBody()).doesNotContain("SSR Content");
            assertThat(res.getBody()).contains("<div id=\"app\" data-page=\"");
        }

        @Test
        void neverCallsSsrForJsonRequests() throws IOException {
            SsrClient shouldNotBeCalled = pageJson -> {
                throw new AssertionError("SSR should not be called for JSON requests");
            };

            InertiaConfig config = InertiaConfig.builder()
                    .version("1.0.0")
                    .templateResolver(pageJson -> "<html>" + pageJson + "</html>")
                    .ssrClient(shouldNotBeCalled)
                    .ssrEnabled(true)
                    .build();
            InertiaEngine ssrEngine = new InertiaEngine(config);

            var req = new StubInertiaRequest().asInertia();
            var res = new StubInertiaResponse();

            ssrEngine.render(req, res, "Home", Map.of());

            assertThat(res.getContentType()).isEqualTo("application/json");
        }

        @Test
        void rendersWithoutSsrWhenNoClientConfigured() throws IOException {
            // Default engine from setUp — no SSR client
            var req = new StubInertiaRequest();
            var res = new StubInertiaResponse();

            engine.render(req, res, "Home", Map.of("name", "World"));

            assertThat(res.getBody()).contains("<div id=\"app\" data-page=\"");
        }
    }

    // ── Prefetch Detection ───────────────────────────────────────────

    @Nested
    class PrefetchDetection {

        @Test
        void detectsPrefetchRequest() {
            var req = new StubInertiaRequest().asInertia()
                    .withHeader("Purpose", "prefetch");
            assertThat(engine.isPrefetchRequest(req)).isTrue();
        }

        @Test
        void nonPrefetchRequest() {
            var req = new StubInertiaRequest().asInertia();
            assertThat(engine.isPrefetchRequest(req)).isFalse();
        }
    }

    // ── Fragment Redirect ────────────────────────────────────────────

    @Nested
    class FragmentRedirect {

        @Test
        void redirectWithFragmentReturns409WithInertiaRedirectHeader() {
            var res = new StubInertiaResponse();
            engine.redirectWithFragment(res, "/page#section");
            assertThat(res.getStatus()).isEqualTo(409);
            assertThat(res.getHeader("X-Inertia-Redirect")).isEqualTo("/page#section");
        }

        @Test
        void redirectWithFragmentDoesNotSetLocationHeader() {
            var res = new StubInertiaResponse();
            engine.redirectWithFragment(res, "/page#section");
            assertThat(res.getHeader("X-Inertia-Location")).isNull();
        }
    }

    // ── Reset Props ─────────────────────────────────────────────────

    @Nested
    class ResetProps {

        @SuppressWarnings("unchecked")
        private List<String> parseField(String json, String field) throws IOException {
            Map<String, Object> page = mapper.readValue(json, MAP_TYPE);
            return (List<String>) page.get(field);
        }

        @Test
        void resetHeaderExcludesKeysFromMergeMetadata() throws IOException {
            var req = new StubInertiaRequest().asInertia()
                    .withHeader("X-Inertia-Reset", "items");
            var res = new StubInertiaResponse();

            Map<String, Object> props = new HashMap<>();
            props.put("items", InertiaProps.merge(() -> List.of("a", "b")));
            props.put("feed", InertiaProps.merge(() -> List.of("post1")));

            engine.render(req, res, "Test", props);

            var mergeProps = parseField(res.getBody(), "mergeProps");
            assertThat(mergeProps).containsExactly("feed");
            assertThat(mergeProps).doesNotContain("items");
        }

        @Test
        void resetHeaderWithNoPropsMeansAllMergeMetadataIncluded() throws IOException {
            var req = new StubInertiaRequest().asInertia();
            var res = new StubInertiaResponse();

            Map<String, Object> props = new HashMap<>();
            props.put("items", InertiaProps.merge(() -> List.of("a")));

            engine.render(req, res, "Test", props);

            var mergeProps = parseField(res.getBody(), "mergeProps");
            assertThat(mergeProps).containsExactly("items");
        }
    }

    // ── Infinite Scroll Merge Intent ─────────────────────────────────

    @Nested
    class InfiniteScrollMergeIntent {

        @SuppressWarnings("unchecked")
        private List<String> parseField(String json, String field) throws IOException {
            Map<String, Object> page = mapper.readValue(json, MAP_TYPE);
            return (List<String>) page.get(field);
        }

        @Test
        void appendIntentOverridesPrependStrategy() throws IOException {
            var req = new StubInertiaRequest().asInertia()
                    .withHeader("X-Inertia-Infinite-Scroll-Merge-Intent", "append");
            var res = new StubInertiaResponse();

            Map<String, Object> props = new HashMap<>();
            props.put("items", InertiaProps.prepend(() -> List.of("a")));

            engine.render(req, res, "Test", props);

            assertThat(parseField(res.getBody(), "mergeProps")).containsExactly("items");
            assertThat(parseField(res.getBody(), "prependProps")).isNull();
        }

        @Test
        void prependIntentOverridesAppendStrategy() throws IOException {
            var req = new StubInertiaRequest().asInertia()
                    .withHeader("X-Inertia-Infinite-Scroll-Merge-Intent", "prepend");
            var res = new StubInertiaResponse();

            Map<String, Object> props = new HashMap<>();
            props.put("items", InertiaProps.merge(() -> List.of("a")));

            engine.render(req, res, "Test", props);

            assertThat(parseField(res.getBody(), "prependProps")).containsExactly("items");
            assertThat(parseField(res.getBody(), "mergeProps")).isNull();
        }

        @Test
        void noIntentHeaderPreservesOriginalStrategy() throws IOException {
            var req = new StubInertiaRequest().asInertia();
            var res = new StubInertiaResponse();

            Map<String, Object> props = new HashMap<>();
            props.put("items", InertiaProps.prepend(() -> List.of("a")));

            engine.render(req, res, "Test", props);

            assertThat(parseField(res.getBody(), "prependProps")).containsExactly("items");
            assertThat(parseField(res.getBody(), "mergeProps")).isNull();
        }
    }

    // ── Error Bags ──────────────────────────────────────────────────

    @Nested
    class ErrorBags {

        @Test
        void selectsErrorBagByHeader() throws IOException {
            engine.addSharedPropsResolver(req -> {
                Map<String, Object> errors = new HashMap<>();
                errors.put("default", Map.of("name", "Name is required"));
                errors.put("login", Map.of("email", "Invalid email"));
                return Map.of("errors", errors);
            });

            var req = new StubInertiaRequest().asInertia()
                    .withHeader("X-Inertia-Error-Bag", "login");
            var res = new StubInertiaResponse();

            engine.render(req, res, "Test", Map.of());

            var props = parsePageProps(res.getBody());
            @SuppressWarnings("unchecked")
            var errors = (Map<String, Object>) props.get("errors");
            assertThat(errors).containsEntry("email", "Invalid email");
            assertThat(errors).doesNotContainKey("name");
        }

        @Test
        void selectsDefaultBagWhenNoHeader() throws IOException {
            engine.addSharedPropsResolver(req -> {
                Map<String, Object> errors = new HashMap<>();
                errors.put("default", Map.of("name", "Name is required"));
                errors.put("login", Map.of("email", "Invalid email"));
                return Map.of("errors", errors);
            });

            var req = new StubInertiaRequest().asInertia();
            var res = new StubInertiaResponse();

            engine.render(req, res, "Test", Map.of());

            var props = parsePageProps(res.getBody());
            @SuppressWarnings("unchecked")
            var errors = (Map<String, Object>) props.get("errors");
            assertThat(errors).containsEntry("name", "Name is required");
            assertThat(errors).doesNotContainKey("email");
        }

        @Test
        void returnsEmptyErrorsWhenBagNotFound() throws IOException {
            engine.addSharedPropsResolver(req -> {
                Map<String, Object> errors = new HashMap<>();
                errors.put("default", Map.of("name", "required"));
                return Map.of("errors", errors);
            });

            var req = new StubInertiaRequest().asInertia()
                    .withHeader("X-Inertia-Error-Bag", "nonexistent");
            var res = new StubInertiaResponse();

            engine.render(req, res, "Test", Map.of());

            var props = parsePageProps(res.getBody());
            @SuppressWarnings("unchecked")
            var errors = (Map<String, Object>) props.get("errors");
            assertThat(errors).isEmpty();
        }

        @Test
        void flatErrorsMapPassesThroughWhenNotNested() throws IOException {
            engine.addSharedPropsResolver(req -> Map.of("errors", Map.of("name", "required")));

            var req = new StubInertiaRequest().asInertia();
            var res = new StubInertiaResponse();

            engine.render(req, res, "Test", Map.of());

            var props = parsePageProps(res.getBody());
            @SuppressWarnings("unchecked")
            var errors = (Map<String, Object>) props.get("errors");
            assertThat(errors).containsEntry("name", "required");
        }
    }

    // ── Shared Props Tracking ────────────────────────────────────────

    @Nested
    class SharedPropsTracking {

        @SuppressWarnings("unchecked")
        private List<String> parseSharedProps(String json) throws IOException {
            Map<String, Object> page = mapper.readValue(json, MAP_TYPE);
            return (List<String>) page.get("sharedProps");
        }

        @Test
        void sharedPropsFieldListsSharedResolverKeys() throws IOException {
            engine.addSharedPropsResolver(req -> Map.of("appName", "TestApp", "auth", Map.of()));

            var req = new StubInertiaRequest().asInertia();
            var res = new StubInertiaResponse();

            engine.render(req, res, "Test", Map.of("title", "Hello"));

            var shared = parseSharedProps(res.getBody());
            assertThat(shared).containsExactlyInAnyOrder("appName", "auth");
        }

        @Test
        void sharedPropsFieldOmittedWhenNoSharedResolvers() throws IOException {
            var req = new StubInertiaRequest().asInertia();
            var res = new StubInertiaResponse();

            engine.render(req, res, "Test", Map.of("title", "Hello"));

            var shared = parseSharedProps(res.getBody());
            assertThat(shared).isNull();
        }

        @Test
        void sharedPropsDoesNotIncludePagePropKeys() throws IOException {
            engine.addSharedPropsResolver(req -> Map.of("appName", "TestApp"));

            var req = new StubInertiaRequest().asInertia();
            var res = new StubInertiaResponse();

            engine.render(req, res, "Test", Map.of("title", "Hello"));

            var shared = parseSharedProps(res.getBody());
            assertThat(shared).containsExactly("appName");
            assertThat(shared).doesNotContain("title");
        }
    }

    // ── Nested Props Integration ─────────────────────────────────────

    @Nested
    class NestedPropsIntegration {

        @SuppressWarnings("unchecked")
        private Map<String, List<String>> parseDeferredProps(String json) throws IOException {
            Map<String, Object> page = mapper.readValue(json, MAP_TYPE);
            return (Map<String, List<String>>) page.get("deferredProps");
        }

        @SuppressWarnings("unchecked")
        private List<String> parseField(String json, String field) throws IOException {
            Map<String, Object> page = mapper.readValue(json, MAP_TYPE);
            return (List<String>) page.get(field);
        }

        @Test
        void nestedDeferredPropExcludedFromInitialRender() throws IOException {
            var req = new StubInertiaRequest().asInertia();
            var res = new StubInertiaResponse();

            Map<String, Object> inner = new HashMap<>();
            inner.put("permissions", InertiaProps.defer(() -> List.of("read")));
            inner.put("name", "Alice");

            Map<String, Object> props = new HashMap<>();
            props.put("user", inner);

            engine.render(req, res, "Test", props);

            var pageProps = parsePageProps(res.getBody());
            @SuppressWarnings("unchecked")
            var user = (Map<String, Object>) pageProps.get("user");
            assertThat(user).containsKey("name");
            assertThat(user).doesNotContainKey("permissions");

            var deferred = parseDeferredProps(res.getBody());
            assertThat(deferred.get("default")).containsExactly("user.permissions");
        }

        @Test
        void nestedDeferredPropResolvedViaPartialReloadWithDotNotation() throws IOException {
            var req = new StubInertiaRequest().asInertia()
                    .withHeader("X-Inertia-Partial-Component", "Test")
                    .withHeader("X-Inertia-Partial-Data", "user.permissions");
            var res = new StubInertiaResponse();

            Map<String, Object> inner = new HashMap<>();
            inner.put("permissions", InertiaProps.defer(() -> List.of("read", "write")));
            inner.put("name", "Alice");

            Map<String, Object> props = new HashMap<>();
            props.put("user", inner);

            engine.render(req, res, "Test", props);

            var pageProps = parsePageProps(res.getBody());
            @SuppressWarnings("unchecked")
            var user = (Map<String, Object>) pageProps.get("user");
            assertThat(user).containsKey("permissions");
            assertThat(user).doesNotContainKey("name");
        }

        @Test
        void nestedMergePropUsesDotNotation() throws IOException {
            var req = new StubInertiaRequest().asInertia();
            var res = new StubInertiaResponse();

            Map<String, Object> inner = new HashMap<>();
            inner.put("items", InertiaProps.merge(() -> List.of("a", "b")));

            Map<String, Object> props = new HashMap<>();
            props.put("page", inner);

            engine.render(req, res, "Test", props);

            var mergeProps = parseField(res.getBody(), "mergeProps");
            assertThat(mergeProps).containsExactly("page.items");
        }

        @SuppressWarnings("unchecked")
        @Test
        void nestedOptionalPropExcludedByDefault() throws IOException {
            var req = new StubInertiaRequest().asInertia();
            var res = new StubInertiaResponse();

            Map<String, Object> inner = new HashMap<>();
            inner.put("details", InertiaProps.optional(() -> "expensive"));
            inner.put("name", "Alice");

            Map<String, Object> props = new HashMap<>();
            props.put("user", inner);

            engine.render(req, res, "Test", props);

            var pageProps = parsePageProps(res.getBody());
            var user = (Map<String, Object>) pageProps.get("user");
            assertThat(user).containsKey("name");
            assertThat(user).doesNotContainKey("details");
        }
    }
}
