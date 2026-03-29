package io.github.emmajiugo.inertia.core;

import io.github.emmajiugo.inertia.core.props.*;

import java.util.*;

final class PropResolver {

    private PropResolver() {}

    // ── Resolve all Resolvable wrappers recursively ─────────────────

    static Map<String, Object> resolveProps(Map<String, Object> props) {
        Map<String, Object> resolved = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : props.entrySet()) {
            resolved.put(entry.getKey(), resolveValue(entry.getValue()));
        }
        return resolved;
    }

    @SuppressWarnings("unchecked")
    private static Object resolveValue(Object value) {
        if (value instanceof Resolvable<?> resolvable) {
            return resolvable.resolve();
        }
        if (value instanceof Map<?, ?> map) {
            return resolveProps((Map<String, Object>) map);
        }
        return value;
    }

    // ── Build deferred props map with dot-notation keys ─────────────

    static Map<String, List<String>> buildDeferredPropsMap(Map<String, Object> props) {
        Map<String, List<String>> groups = new LinkedHashMap<>();
        collectDeferredProps(props, "", groups);
        return groups;
    }

    @SuppressWarnings("unchecked")
    private static void collectDeferredProps(Map<String, Object> props, String prefix,
                                              Map<String, List<String>> groups) {
        for (Map.Entry<String, Object> entry : props.entrySet()) {
            String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            Object value = entry.getValue();

            if (value instanceof DeferredProp<?> deferred) {
                groups.computeIfAbsent(deferred.group(), k -> new ArrayList<>()).add(key);
            } else if (value instanceof Map<?, ?> map) {
                collectDeferredProps((Map<String, Object>) map, key, groups);
            }
        }
    }

    // ── Build merge metadata with dot-notation keys ─────────────────

    record MergeMetadata(
            List<String> mergeProps,
            List<String> prependProps,
            List<String> deepMergeProps,
            Map<String, String> matchPropsOn) {}

    static MergeMetadata buildMergeMetadata(Map<String, Object> props) {
        List<String> merge = new ArrayList<>();
        List<String> prepend = new ArrayList<>();
        List<String> deep = new ArrayList<>();
        Map<String, String> matchOn = new LinkedHashMap<>();
        collectMergeMetadata(props, "", merge, prepend, deep, matchOn);
        return new MergeMetadata(merge, prepend, deep, matchOn);
    }

    @SuppressWarnings("unchecked")
    private static void collectMergeMetadata(Map<String, Object> props, String prefix,
                                              List<String> merge, List<String> prepend,
                                              List<String> deep, Map<String, String> matchOn) {
        for (Map.Entry<String, Object> entry : props.entrySet()) {
            String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            Object value = entry.getValue();

            if (value instanceof MergeProp<?> mp) {
                switch (mp.getStrategy()) {
                    case APPEND -> merge.add(key);
                    case PREPEND -> prepend.add(key);
                    case DEEP -> deep.add(key);
                }
                if (mp.getMatchOn() != null) {
                    matchOn.put(key, mp.getMatchOn());
                }
            } else if (value instanceof Map<?, ?> map) {
                collectMergeMetadata((Map<String, Object>) map, key, merge, prepend, deep, matchOn);
            }
        }
    }

    // ── Build once props map with dot-notation keys ─────────────────

    static Map<String, PageObject.OncePropsEntry> buildOncePropsMap(Map<String, Object> props) {
        Map<String, PageObject.OncePropsEntry> map = new LinkedHashMap<>();
        collectOnceProps(props, "", map);
        return map;
    }

    @SuppressWarnings("unchecked")
    private static void collectOnceProps(Map<String, Object> props, String prefix,
                                          Map<String, PageObject.OncePropsEntry> map) {
        for (Map.Entry<String, Object> entry : props.entrySet()) {
            String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            Object value = entry.getValue();

            if (value instanceof OnceProp<?> op) {
                String onceKey = op.getKey() != null ? op.getKey() : key;
                map.put(onceKey, new PageObject.OncePropsEntry(key, op.getExpiresAtMs()));
            } else if (value instanceof Map<?, ?> mapValue) {
                collectOnceProps((Map<String, Object>) mapValue, key, map);
            }
        }
    }

    // ── Filter props for partial reloads (dot-notation aware) ───────

    @SuppressWarnings("unchecked")
    static Map<String, Object> filterPropsOnly(Map<String, Object> props, Set<String> only) {
        Map<String, Object> result = new LinkedHashMap<>();

        Map<String, Set<String>> nestedOnly = new LinkedHashMap<>();
        Set<String> topLevel = new LinkedHashSet<>();

        for (String key : only) {
            int dot = key.indexOf('.');
            if (dot > 0) {
                String top = key.substring(0, dot);
                String rest = key.substring(dot + 1);
                nestedOnly.computeIfAbsent(top, k -> new LinkedHashSet<>()).add(rest);
            } else {
                topLevel.add(key);
            }
        }

        for (Map.Entry<String, Object> entry : props.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (topLevel.contains(key)) {
                result.put(key, value);
            } else if (nestedOnly.containsKey(key) && value instanceof Map<?, ?> map) {
                result.put(key, filterPropsOnly((Map<String, Object>) map, nestedOnly.get(key)));
            }
        }

        return result;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> filterPropsExcept(Map<String, Object> props, Set<String> except) {
        Map<String, Object> result = new LinkedHashMap<>();

        Map<String, Set<String>> nestedExcept = new LinkedHashMap<>();
        Set<String> topLevel = new LinkedHashSet<>();

        for (String key : except) {
            int dot = key.indexOf('.');
            if (dot > 0) {
                String top = key.substring(0, dot);
                String rest = key.substring(dot + 1);
                nestedExcept.computeIfAbsent(top, k -> new LinkedHashSet<>()).add(rest);
            } else {
                topLevel.add(key);
            }
        }

        for (Map.Entry<String, Object> entry : props.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (topLevel.contains(key)) {
                continue;
            } else if (nestedExcept.containsKey(key) && value instanceof Map<?, ?> map) {
                result.put(key, filterPropsExcept((Map<String, Object>) map, nestedExcept.get(key)));
            } else {
                result.put(key, value);
            }
        }

        return result;
    }
}
