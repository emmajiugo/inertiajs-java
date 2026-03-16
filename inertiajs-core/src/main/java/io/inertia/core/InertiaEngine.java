package io.inertia.core;

import io.inertia.core.props.AlwaysProp;
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

    public void addSharedPropsResolver(SharedPropsResolver resolver) {
        sharedPropsResolvers.add(resolver);
    }

    // ── Main render entry point ──────────────────────────────────────

    public void render(InertiaRequest req, InertiaResponse res,
                       String component, Map<String, Object> props) throws IOException {
        Map<String, Object> mergedProps = mergeSharedProps(req, props);
        Map<String, Object> filteredProps = filterProps(req, component, mergedProps);
        Map<String, Object> resolvedProps = resolveProps(filteredProps);

        PageObject page = new PageObject(component, resolvedProps, req.getRequestPath(), config.getVersion());

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

    // ── Internal: filter props for partial reloads ───────────────────

    private Map<String, Object> filterProps(InertiaRequest req, String component,
                                            Map<String, Object> props) {
        String partialComponent = req.getHeader("X-Inertia-Partial-Component");

        // Not a partial reload — include everything except OptionalProp
        if (partialComponent == null || !partialComponent.equals(component)) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : props.entrySet()) {
                if (!(entry.getValue() instanceof OptionalProp<?>)) {
                    result.put(entry.getKey(), entry.getValue());
                }
            }
            return result;
        }

        // Partial reload with "only" list
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

        // Partial reload with no only/except — same as full render but include OptionalProp too
        return new LinkedHashMap<>(props);
    }

    // ── Internal: resolve Prop<T> wrappers to raw values ─────────────

    private Map<String, Object> resolveProps(Map<String, Object> props) {
        Map<String, Object> resolved = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : props.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Prop<?> prop) {
                resolved.put(entry.getKey(), prop.resolve());
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
