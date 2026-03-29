package io.github.emmajiugo.inertia.core;

import io.github.emmajiugo.inertia.core.props.AlwaysProp;
import io.github.emmajiugo.inertia.core.props.DeferredProp;
import io.github.emmajiugo.inertia.core.props.OptionalProp;
import io.github.emmajiugo.inertia.core.props.Prop;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import java.util.LinkedHashSet;

public class InertiaEngine {

    private final InertiaConfig config;
    private final SsrGateway ssrGateway;
    private final List<SharedPropsResolver> sharedPropsResolvers = new CopyOnWriteArrayList<>();

    public InertiaEngine(InertiaConfig config) {
        this.config = Objects.requireNonNull(config);
        this.ssrGateway = new SsrGateway(
                config.getTemplateResolver(),
                config.getSsrClient(),
                config.isSsrFailOnError()
        );
    }

    public InertiaConfig getConfig() {
        return config;
    }

    public void addSharedPropsResolver(SharedPropsResolver resolver) {
        sharedPropsResolvers.add(resolver);
    }

    // ── Main render entry point ──────────────────────────────────────

    public void render(InertiaRequest req, InertiaResponse res,
                       String component, Map<String, Object> props) throws IOException {
        render(req, res, component, props, RenderOptions.empty());
    }

    public void render(InertiaRequest req, InertiaResponse res,
                       String component, Map<String, Object> props,
                       RenderOptions options) throws IOException {
        Objects.requireNonNull(req, "req must not be null");
        Objects.requireNonNull(res, "res must not be null");
        Objects.requireNonNull(component, "component must not be null");
        Objects.requireNonNull(options, "options must not be null");
        if (props == null) props = Map.of();

        MergedPropsResult mergedResult = mergeSharedProps(req, props);
        Map<String, Object> mergedProps = mergedResult.props();
        List<String> sharedPropKeys = mergedResult.sharedKeys();
        boolean isPartialReload = isPartialReloadFor(req, component);

        // Build deferred props map (only on initial render, not partial reloads)
        Map<String, List<String>> deferredProps = null;
        if (!isPartialReload) {
            deferredProps = PropResolver.buildDeferredPropsMap(mergedProps);
        }

        // Build once props metadata and filter out already-loaded ones
        Map<String, PageObject.OncePropsEntry> oncePropsMap = PropResolver.buildOncePropsMap(mergedProps);
        Set<String> exceptOnceProps = parseExceptOnceProps(req);
        filterExceptOnceProps(mergedProps, oncePropsMap, exceptOnceProps);

        Map<String, Object> filteredProps = filterProps(req, component, mergedProps, isPartialReload);
        PropResolver.MergeMetadata mergeMetadata = PropResolver.buildMergeMetadata(filteredProps);
        Map<String, Object> resolvedProps = PropResolver.resolveProps(filteredProps);

        // Apply infinite scroll merge intent override
        String mergeIntent = req.getHeader("X-Inertia-Infinite-Scroll-Merge-Intent");
        if (mergeIntent != null && !mergeIntent.isBlank()) {
            mergeMetadata = applyMergeIntentOverride(mergeMetadata, mergeIntent.trim());
        }

        // Strip reset keys from merge metadata
        Set<String> resetKeys = parseCommaSeparatedHeader(req, "X-Inertia-Reset");
        if (!resetKeys.isEmpty()) {
            List<String> filteredMerge = new ArrayList<>(mergeMetadata.mergeProps());
            List<String> filteredPrepend = new ArrayList<>(mergeMetadata.prependProps());
            List<String> filteredDeep = new ArrayList<>(mergeMetadata.deepMergeProps());
            Map<String, String> filteredMatchOn = new LinkedHashMap<>(mergeMetadata.matchPropsOn());
            filteredMerge.removeAll(resetKeys);
            filteredPrepend.removeAll(resetKeys);
            filteredDeep.removeAll(resetKeys);
            resetKeys.forEach(filteredMatchOn::remove);
            mergeMetadata = new PropResolver.MergeMetadata(filteredMerge, filteredPrepend, filteredDeep, filteredMatchOn);
        }

        PageObject page = PageObject.builder()
                .component(component)
                .props(resolvedProps)
                .url(req.getRequestPath())
                .version(config.getVersion())
                .deferredProps(deferredProps)
                .mergeProps(mergeMetadata.mergeProps())
                .prependProps(mergeMetadata.prependProps())
                .deepMergeProps(mergeMetadata.deepMergeProps())
                .matchPropsOn(mergeMetadata.matchPropsOn())
                .onceProps(oncePropsMap)
                .sharedProps(sharedPropKeys)
                .encryptHistory(options.getEncryptHistory())
                .clearHistory(options.getClearHistory())
                .preserveFragment(options.getPreserveFragment())
                .scrollProps(options.getScrollProps())
                .build();

        if (isInertiaRequest(req)) {
            renderJson(res, page);
        } else {
            renderHtml(res, page, options);
        }
    }

    // ── Protocol checks (for use in middleware/interceptors) ─────────

    public boolean isInertiaRequest(InertiaRequest req) {
        return "true".equals(req.getHeader("X-Inertia"));
    }

    public boolean isVersionMismatch(InertiaRequest req) {
        if (!isInertiaRequest(req)) return false;
        if (!"GET".equalsIgnoreCase(req.getMethod())) return false;
        String clientVersion = req.getHeader("X-Inertia-Version");
        return clientVersion != null && !clientVersion.equals(config.getVersion());
    }

    public void forceVersionMismatchResponse(InertiaRequest req, InertiaResponse res) {
        res.setStatus(409);
        res.setHeader("X-Inertia-Location", req.getRequestUrl());
    }

    public boolean needsRedirectUpgrade(InertiaRequest req, int statusCode) {
        if (!isInertiaRequest(req)) return false;
        if (statusCode != 302) return false;
        String method = req.getMethod().toUpperCase();
        return method.equals("PUT") || method.equals("PATCH") || method.equals("DELETE");
    }

    public void location(InertiaResponse res, String url) {
        res.setStatus(409);
        res.setHeader("X-Inertia-Location", url);
    }

    public boolean isPrefetchRequest(InertiaRequest req) {
        return "prefetch".equals(req.getHeader("Purpose"));
    }

    public void redirectWithFragment(InertiaResponse res, String url) {
        res.setStatus(409);
        res.setHeader("X-Inertia-Redirect", url);
    }

    // ── Internal: once props handling ──────────────────────────────────

    private Set<String> parseExceptOnceProps(InertiaRequest req) {
        String header = req.getHeader("X-Inertia-Except-Once-Props");
        if (header == null || header.isBlank()) return Set.of();
        return Arrays.stream(header.split(","))
                .map(String::trim)
                .collect(Collectors.toUnmodifiableSet());
    }

    private void filterExceptOnceProps(Map<String, Object> props,
                                       Map<String, PageObject.OncePropsEntry> oncePropsMap,
                                       Set<String> exceptKeys) {
        if (exceptKeys.isEmpty()) return;
        for (String exceptKey : exceptKeys) {
            PageObject.OncePropsEntry entry = oncePropsMap.get(exceptKey);
            if (entry != null) {
                // Remove from props (skip resolving) but keep in oncePropsMap
                // so client knows it's still a once prop
                props.remove(entry.prop());
            }
        }
    }

    // ── Internal: merge shared props with page props ─────────────────

    private record MergedPropsResult(Map<String, Object> props, List<String> sharedKeys) {}

    private MergedPropsResult mergeSharedProps(InertiaRequest req, Map<String, Object> pageProps) {
        Map<String, Object> merged = new LinkedHashMap<>();
        Set<String> sharedKeySet = new LinkedHashSet<>();

        for (SharedPropsResolver resolver : sharedPropsResolvers) {
            Map<String, Object> resolved = resolver.resolve(req);
            sharedKeySet.addAll(resolved.keySet());
            merged.putAll(resolved);
        }

        // Page props override shared props; remove overridden keys from shared tracking
        sharedKeySet.removeAll(pageProps.keySet());
        merged.putAll(pageProps);

        // Error bag selection
        resolveErrorBag(req, merged);

        merged.putIfAbsent("errors", Map.of());

        List<String> sharedKeys = new ArrayList<>(sharedKeySet);
        return new MergedPropsResult(merged, sharedKeys);
    }

    @SuppressWarnings("unchecked")
    private void resolveErrorBag(InertiaRequest req, Map<String, Object> merged) {
        Object errorsObj = merged.get("errors");
        if (!(errorsObj instanceof Map<?, ?> errorsMap)) return;

        // Check if this is a nested bag structure (all values are Maps)
        boolean isBagStructure = !errorsMap.isEmpty() && errorsMap.values().stream()
                .allMatch(v -> v instanceof Map);
        if (!isBagStructure) return;

        String bag = req.getHeader("X-Inertia-Error-Bag");
        if (bag == null || bag.isBlank()) {
            bag = "default";
        }

        Object selectedBag = ((Map<String, Object>) errorsMap).get(bag.trim());
        if (selectedBag instanceof Map<?, ?>) {
            merged.put("errors", selectedBag);
        } else {
            merged.put("errors", Map.of());
        }
    }

    // ── Internal: check if this is a partial reload for this component ─

    private boolean isPartialReloadFor(InertiaRequest req, String component) {
        String partialComponent = req.getHeader("X-Inertia-Partial-Component");
        return partialComponent != null && partialComponent.equals(component);
    }

    // ── Internal: filter props for partial reloads ───────────────────

    @SuppressWarnings("unchecked")
    private Map<String, Object> filterNonPartial(Map<String, Object> props) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : props.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof OptionalProp<?> || value instanceof DeferredProp<?>) {
                continue;
            }
            if (value instanceof Map<?, ?> map) {
                result.put(entry.getKey(), filterNonPartial((Map<String, Object>) map));
            } else {
                result.put(entry.getKey(), value);
            }
        }
        return result;
    }

    private Map<String, Object> filterProps(InertiaRequest req, String component,
                                            Map<String, Object> props, boolean isPartialReload) {
        // Not a partial reload — include everything except OptionalProp and DeferredProp (recursively)
        if (!isPartialReload) {
            return filterNonPartial(props);
        }

        // Partial reload with "only" list (also used by client to fetch deferred props)
        String partialData = req.getHeader("X-Inertia-Partial-Data");
        if (partialData != null && !partialData.isBlank()) {
            Set<String> only = parseCommaSeparatedHeader(req, "X-Inertia-Partial-Data");
            Map<String, Object> filtered = PropResolver.filterPropsOnly(props, only);
            for (Map.Entry<String, Object> entry : props.entrySet()) {
                if (entry.getValue() instanceof AlwaysProp<?> || entry.getKey().equals("errors")) {
                    filtered.putIfAbsent(entry.getKey(), entry.getValue());
                }
            }
            return filtered;
        }

        // Partial reload with "except" list
        String partialExcept = req.getHeader("X-Inertia-Partial-Except");
        if (partialExcept != null && !partialExcept.isBlank()) {
            Set<String> except = parseCommaSeparatedHeader(req, "X-Inertia-Partial-Except");
            Map<String, Object> filtered = PropResolver.filterPropsExcept(props, except);
            for (Map.Entry<String, Object> entry : props.entrySet()) {
                if (entry.getValue() instanceof AlwaysProp<?> || entry.getKey().equals("errors")) {
                    filtered.putIfAbsent(entry.getKey(), entry.getValue());
                }
            }
            filtered.entrySet().removeIf(e -> e.getValue() instanceof OptionalProp<?>);
            return filtered;
        }

        // Partial reload with no only/except — include everything including OptionalProp
        return new LinkedHashMap<>(props);
    }

    // ── Internal: merge intent override ─────────────────────────────────

    private PropResolver.MergeMetadata applyMergeIntentOverride(
            PropResolver.MergeMetadata metadata, String intent) {
        List<String> allKeys = new ArrayList<>();
        allKeys.addAll(metadata.mergeProps());
        allKeys.addAll(metadata.prependProps());

        if (allKeys.isEmpty()) return metadata;

        List<String> merge = new ArrayList<>();
        List<String> prepend = new ArrayList<>();

        if ("append".equalsIgnoreCase(intent)) {
            merge.addAll(allKeys);
        } else if ("prepend".equalsIgnoreCase(intent)) {
            prepend.addAll(allKeys);
        } else {
            return metadata;
        }

        return new PropResolver.MergeMetadata(merge, prepend,
                metadata.deepMergeProps(), metadata.matchPropsOn());
    }

    private Set<String> parseCommaSeparatedHeader(InertiaRequest req, String headerName) {
        String header = req.getHeader(headerName);
        if (header == null || header.isBlank()) return Set.of();
        return Arrays.stream(header.split(","))
                .map(String::trim)
                .collect(Collectors.toUnmodifiableSet());
    }

    // ── Internal: render responses ───────────────────────────────────

    private void renderJson(InertiaResponse res, PageObject page) throws IOException {
        res.setStatus(200);
        res.setHeader("X-Inertia", "true");
        res.setHeader("Vary", "X-Inertia");
        res.setContentType("application/json");
        res.writeBody(config.getJsonSerializer().serialize(page));
    }

    private boolean resolveSsr(RenderOptions options) {
        if (options.getSsr() != null) {
            return options.getSsr();
        }
        return config.isSsrEnabled() && config.getSsrClient() != null;
    }

    private void renderHtml(InertiaResponse res, PageObject page,
                            RenderOptions options) throws IOException {
        String pageJson = config.getJsonSerializer().serialize(page);
        boolean useSsr = resolveSsr(options);
        String html = ssrGateway.resolve(pageJson, useSsr);
        res.setStatus(200);
        res.setHeader("Vary", "X-Inertia");
        res.setContentType("text/html; charset=utf-8");
        res.writeBody(html);
    }
}
