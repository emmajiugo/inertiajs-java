package io.inertia.core;

import io.inertia.core.props.AlwaysProp;
import io.inertia.core.props.DeferredProp;
import io.inertia.core.props.MergeProp;
import io.inertia.core.props.OnceProp;
import io.inertia.core.props.OptionalProp;
import io.inertia.core.props.Prop;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class InertiaEngine {

    private final InertiaConfig config;
    private final List<SharedPropsResolver> sharedPropsResolvers = new CopyOnWriteArrayList<>();

    public InertiaEngine(InertiaConfig config) {
        this.config = Objects.requireNonNull(config);
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
        Map<String, Object> mergedProps = mergeSharedProps(req, props);
        boolean isPartialReload = isPartialReloadFor(req, component);

        // Build deferred props map (only on initial render, not partial reloads)
        Map<String, List<String>> deferredProps = null;
        if (!isPartialReload) {
            deferredProps = buildDeferredPropsMap(mergedProps);
        }

        // Build once props metadata and filter out already-loaded ones
        Map<String, PageObject.OncePropsEntry> oncePropsMap = buildOncePropsMap(mergedProps);
        Set<String> exceptOnceProps = parseExceptOnceProps(req);
        filterExceptOnceProps(mergedProps, oncePropsMap, exceptOnceProps);

        Map<String, Object> filteredProps = filterProps(req, component, mergedProps, isPartialReload);
        MergeMetadata mergeMetadata = buildMergeMetadata(filteredProps);
        Map<String, Object> resolvedProps = resolveProps(filteredProps);

        PageObject page = PageObject.builder()
                .component(component)
                .props(resolvedProps)
                .url(req.getRequestPath())
                .version(config.getVersion())
                .deferredProps(deferredProps)
                .mergeProps(mergeMetadata.mergeProps)
                .prependProps(mergeMetadata.prependProps)
                .deepMergeProps(mergeMetadata.deepMergeProps)
                .matchPropsOn(mergeMetadata.matchPropsOn)
                .onceProps(oncePropsMap)
                .encryptHistory(options.getEncryptHistory())
                .clearHistory(options.getClearHistory())
                .build();

        if (isInertiaRequest(req)) {
            renderJson(res, page);
        } else {
            renderHtml(res, page);
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

    // ── Internal: once props handling ──────────────────────────────────

    private Map<String, PageObject.OncePropsEntry> buildOncePropsMap(Map<String, Object> props) {
        Map<String, PageObject.OncePropsEntry> map = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : props.entrySet()) {
            if (entry.getValue() instanceof OnceProp<?> op) {
                String key = op.getKey() != null ? op.getKey() : entry.getKey();
                map.put(key, new PageObject.OncePropsEntry(entry.getKey(), op.getExpiresAtMs()));
            }
        }
        return map;
    }

    private Set<String> parseExceptOnceProps(InertiaRequest req) {
        String header = req.getHeader("X-Inertia-Except-Once-Props");
        if (header == null || header.isBlank()) return Set.of();
        return Set.of(header.split(","));
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

    private Map<String, Object> mergeSharedProps(InertiaRequest req, Map<String, Object> pageProps) {
        Map<String, Object> merged = new LinkedHashMap<>();
        for (SharedPropsResolver resolver : sharedPropsResolvers) {
            merged.putAll(resolver.resolve(req));
        }
        merged.putAll(pageProps);
        merged.putIfAbsent("errors", Map.of());
        return merged;
    }

    // ── Internal: check if this is a partial reload for this component ─

    private boolean isPartialReloadFor(InertiaRequest req, String component) {
        String partialComponent = req.getHeader("X-Inertia-Partial-Component");
        return partialComponent != null && partialComponent.equals(component);
    }

    // ── Internal: build deferred props group map ─────────────────────

    private Map<String, List<String>> buildDeferredPropsMap(Map<String, Object> props) {
        Map<String, List<String>> groups = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : props.entrySet()) {
            if (entry.getValue() instanceof DeferredProp<?> deferred) {
                groups.computeIfAbsent(deferred.group(), k -> new ArrayList<>())
                        .add(entry.getKey());
            }
        }
        return groups;
    }

    // ── Internal: filter props for partial reloads ───────────────────

    private Map<String, Object> filterProps(InertiaRequest req, String component,
                                            Map<String, Object> props, boolean isPartialReload) {
        // Not a partial reload — include everything except OptionalProp and DeferredProp
        if (!isPartialReload) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : props.entrySet()) {
                Object value = entry.getValue();
                if (value instanceof OptionalProp<?> || value instanceof DeferredProp<?>) {
                    continue;
                }
                result.put(entry.getKey(), value);
            }
            return result;
        }

        // Partial reload with "only" list (also used by client to fetch deferred props)
        String partialData = req.getHeader("X-Inertia-Partial-Data");
        if (partialData != null && !partialData.isBlank()) {
            Set<String> only = Set.of(partialData.split(","));
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : props.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                if (only.contains(key) || value instanceof AlwaysProp<?> || key.equals("errors")) {
                    result.put(key, value);
                }
            }
            return result;
        }

        // Partial reload with "except" list
        String partialExcept = req.getHeader("X-Inertia-Partial-Except");
        if (partialExcept != null && !partialExcept.isBlank()) {
            Set<String> except = Set.of(partialExcept.split(","));
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : props.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                if (value instanceof OptionalProp<?>) continue;
                if (except.contains(key) && !(value instanceof AlwaysProp<?>) && !key.equals("errors")) {
                    continue;
                }
                result.put(key, value);
            }
            return result;
        }

        // Partial reload with no only/except — include everything including OptionalProp
        return new LinkedHashMap<>(props);
    }

    // ── Internal: build merge metadata from MergeProp values ──────────

    private record MergeMetadata(
            List<String> mergeProps,
            List<String> prependProps,
            List<String> deepMergeProps,
            Map<String, String> matchPropsOn) {}

    private MergeMetadata buildMergeMetadata(Map<String, Object> props) {
        List<String> merge = new ArrayList<>();
        List<String> prepend = new ArrayList<>();
        List<String> deep = new ArrayList<>();
        Map<String, String> matchOn = new LinkedHashMap<>();

        for (Map.Entry<String, Object> entry : props.entrySet()) {
            if (entry.getValue() instanceof MergeProp<?> mp) {
                switch (mp.getStrategy()) {
                    case APPEND -> merge.add(entry.getKey());
                    case PREPEND -> prepend.add(entry.getKey());
                    case DEEP -> deep.add(entry.getKey());
                }
                if (mp.getMatchOn() != null) {
                    matchOn.put(entry.getKey(), mp.getMatchOn());
                }
            }
        }

        return new MergeMetadata(merge, prepend, deep, matchOn);
    }

    // ── Internal: resolve Prop<T> wrappers to raw values ─────────────

    private Map<String, Object> resolveProps(Map<String, Object> props) {
        Map<String, Object> resolved = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : props.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Prop<?> prop) {
                resolved.put(entry.getKey(), prop.resolve());
            } else if (value instanceof MergeProp<?> mp) {
                resolved.put(entry.getKey(), mp.resolve());
            } else if (value instanceof OnceProp<?> op) {
                resolved.put(entry.getKey(), op.resolve());
            } else {
                resolved.put(entry.getKey(), value);
            }
        }
        return resolved;
    }

    // ── Internal: render responses ───────────────────────────────────

    private void renderJson(InertiaResponse res, PageObject page) throws IOException {
        res.setStatus(200);
        res.setHeader("X-Inertia", "true");
        res.setHeader("Vary", "X-Inertia");
        res.setContentType("application/json");
        res.writeBody(config.getJsonSerializer().serialize(page));
    }

    private void renderHtml(InertiaResponse res, PageObject page) throws IOException {
        String pageJson = config.getJsonSerializer().serialize(page);
        String html = config.getTemplateResolver().resolve(pageJson);
        res.setStatus(200);
        res.setHeader("Vary", "X-Inertia");
        res.setContentType("text/html; charset=utf-8");
        res.writeBody(html);
    }
}
