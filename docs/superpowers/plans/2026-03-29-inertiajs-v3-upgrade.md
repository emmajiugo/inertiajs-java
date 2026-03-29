# Inertia.js v3 Upgrade Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Upgrade the inertiajs-java adapter to full Inertia.js v3 protocol parity, hard-removing all v2-only features.

**Architecture:** Incremental in-place changes on `main`. Start with removals, then core additions (PageObject, RenderOptions, PropResolver), then engine integration (new headers, error bags, shared props tracking), then adapter updates (Spring, Javalin). TDD throughout.

**Tech Stack:** Java 17+, Jackson 2.21, JUnit 5, AssertJ, Spring Boot 4.x, Javalin 7.x

---

## File Structure

### Created
- `inertiajs-core/src/main/java/io/github/emmajiugo/inertia/core/PropResolver.java` — Recursive prop resolution, metadata building, dot-notation partial reload filtering
- `inertiajs-core/src/test/java/io/github/emmajiugo/inertia/core/PropResolverTest.java` — Unit tests for PropResolver

### Modified
- `inertiajs-core/src/main/java/io/github/emmajiugo/inertia/core/props/Prop.java` — Remove LazyProp from sealed permits
- `inertiajs-core/src/main/java/io/github/emmajiugo/inertia/core/props/InertiaProps.java` — Remove `lazy()` factory
- `inertiajs-core/src/main/java/io/github/emmajiugo/inertia/core/PageObject.java` — Add preserveFragment, scrollProps, sharedProps fields
- `inertiajs-core/src/main/java/io/github/emmajiugo/inertia/core/RenderOptions.java` — Add preserveFragment, scrollProps
- `inertiajs-core/src/main/java/io/github/emmajiugo/inertia/core/InertiaEngine.java` — Delegate to PropResolver, new headers, error bags, shared props tracking, redirectWithFragment
- `inertiajs-core/src/test/java/io/github/emmajiugo/inertia/core/InertiaEngineTest.java` — Remove LazyProp tests, add v3 feature tests
- `inertiajs-spring/src/main/java/io/github/emmajiugo/inertia/spring/Inertia.java` — Error bag overload, redirectWithFragment
- `inertiajs-spring/src/main/java/io/github/emmajiugo/inertia/spring/InertiaValidationSharedProps.java` — Nested error bag map
- `inertiajs-javalin/src/main/java/io/github/emmajiugo/inertia/javalin/Inertia.java` — Error bag overload, redirectWithFragment
- `inertiajs-javalin/src/main/java/io/github/emmajiugo/inertia/javalin/InertiaPlugin.java` — Nested error bag in session holder
- `inertiajs-javalin/src/main/java/io/github/emmajiugo/inertia/javalin/InertiaSessionHolder.java` — Change errors type to nested map

### Deleted
- `inertiajs-core/src/main/java/io/github/emmajiugo/inertia/core/props/LazyProp.java`

---

### Task 1: Remove LazyProp

**Files:**
- Delete: `inertiajs-core/src/main/java/io/github/emmajiugo/inertia/core/props/LazyProp.java`
- Modify: `inertiajs-core/src/main/java/io/github/emmajiugo/inertia/core/props/Prop.java`
- Modify: `inertiajs-core/src/main/java/io/github/emmajiugo/inertia/core/props/InertiaProps.java`
- Modify: `inertiajs-core/src/test/java/io/github/emmajiugo/inertia/core/InertiaEngineTest.java`

- [ ] **Step 1: Remove LazyProp from sealed Prop interface**

In `inertiajs-core/src/main/java/io/github/emmajiugo/inertia/core/props/Prop.java`, change:

```java
public sealed interface Prop<T> extends Resolvable<T> permits LazyProp, AlwaysProp, OptionalProp, DeferredProp {
}
```

to:

```java
public sealed interface Prop<T> extends Resolvable<T> permits AlwaysProp, OptionalProp, DeferredProp {
}
```

- [ ] **Step 2: Remove `lazy()` from InertiaProps**

In `inertiajs-core/src/main/java/io/github/emmajiugo/inertia/core/props/InertiaProps.java`, delete lines 12-14:

```java
    public static <T> LazyProp<T> lazy(Supplier<T> supplier) {
        return new LazyProp<>(supplier);
    }
```

Also remove the unused `LazyProp` import if present.

- [ ] **Step 3: Delete LazyProp.java**

Delete the file: `inertiajs-core/src/main/java/io/github/emmajiugo/inertia/core/props/LazyProp.java`

- [ ] **Step 4: Remove LazyProp test from InertiaEngineTest**

In `inertiajs-core/src/test/java/io/github/emmajiugo/inertia/core/InertiaEngineTest.java`, delete the entire `LazyPropsTests` nested class (lines 397-413):

```java
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
```

Also remove the comment line above it: `// ── Lazy Props ───────────────────────────────────────────────────`

- [ ] **Step 5: Run tests to verify nothing else broke**

Run: `./gradlew :inertiajs-core:test`
Expected: All remaining tests PASS. LazyProp was only used in its own test.

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "feat: remove LazyProp (v3 replaces it with OptionalProp)"
```

---

### Task 2: Add New PageObject Fields

**Files:**
- Modify: `inertiajs-core/src/main/java/io/github/emmajiugo/inertia/core/PageObject.java`
- Test: `inertiajs-core/src/test/java/io/github/emmajiugo/inertia/core/InertiaEngineTest.java`

- [ ] **Step 1: Write failing test for preserveFragment**

Add a new nested class in `InertiaEngineTest.java` after the `HistoryEncryptionTests` class:

```java
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
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :inertiajs-core:test --tests "*.InertiaEngineTest\$PreserveFragmentTests"`
Expected: FAIL — `preserveFragment` method does not exist on `RenderOptions.Builder`

- [ ] **Step 3: Add preserveFragment to RenderOptions**

In `inertiajs-core/src/main/java/io/github/emmajiugo/inertia/core/RenderOptions.java`, add the field and builder method.

Change the class to:

```java
public final class RenderOptions {

    private static final RenderOptions EMPTY = new RenderOptions(null, null, null, null, null);

    private final Boolean encryptHistory;
    private final Boolean clearHistory;
    private final Boolean ssr;
    private final Boolean preserveFragment;
    private final Map<String, Object> scrollProps;

    private RenderOptions(Boolean encryptHistory, Boolean clearHistory, Boolean ssr,
                          Boolean preserveFragment, Map<String, Object> scrollProps) {
        this.encryptHistory = encryptHistory;
        this.clearHistory = clearHistory;
        this.ssr = ssr;
        this.preserveFragment = preserveFragment;
        this.scrollProps = scrollProps;
    }

    public Boolean getEncryptHistory() { return encryptHistory; }
    public Boolean getClearHistory() { return clearHistory; }
    public Boolean getSsr() { return ssr; }
    public Boolean getPreserveFragment() { return preserveFragment; }
    public Map<String, Object> getScrollProps() { return scrollProps; }

    public static RenderOptions empty() { return EMPTY; }
    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private Boolean encryptHistory;
        private Boolean clearHistory;
        private Boolean ssr;
        private Boolean preserveFragment;
        private Map<String, Object> scrollProps;

        private Builder() {}

        public Builder encryptHistory(boolean encryptHistory) {
            this.encryptHistory = encryptHistory;
            return this;
        }

        public Builder clearHistory(boolean clearHistory) {
            this.clearHistory = clearHistory;
            return this;
        }

        public Builder ssr(boolean ssr) {
            this.ssr = ssr;
            return this;
        }

        public Builder preserveFragment(boolean preserveFragment) {
            this.preserveFragment = preserveFragment;
            return this;
        }

        public Builder scrollProps(Map<String, Object> scrollProps) {
            this.scrollProps = scrollProps;
            return this;
        }

        public RenderOptions build() {
            return new RenderOptions(encryptHistory, clearHistory, ssr,
                    preserveFragment, scrollProps);
        }
    }
}
```

Add this import at the top:

```java
import java.util.Map;
```

- [ ] **Step 4: Add preserveFragment, scrollProps, sharedProps to PageObject**

In `inertiajs-core/src/main/java/io/github/emmajiugo/inertia/core/PageObject.java`, add three new fields.

Add after `private final Boolean clearHistory;`:

```java
    private final Boolean preserveFragment;
    private final Map<String, Object> scrollProps;
    private final List<String> sharedProps;
```

In the constructor, add after `this.clearHistory = builder.clearHistory;`:

```java
        this.preserveFragment = builder.preserveFragment;
        this.scrollProps = nullIfEmpty(builder.scrollProps);
        this.sharedProps = nullIfEmpty(builder.sharedProps);
```

Add getters after `getClearHistory()`:

```java
    public Boolean getPreserveFragment() { return preserveFragment; }
    public Map<String, Object> getScrollProps() { return scrollProps; }
    public List<String> getSharedProps() { return sharedProps; }
```

In the Builder class, add after `private Boolean clearHistory;`:

```java
        private Boolean preserveFragment;
        private Map<String, Object> scrollProps;
        private List<String> sharedProps;
```

Add builder methods after `clearHistory(Boolean)`:

```java
        public Builder preserveFragment(Boolean preserveFragment) { this.preserveFragment = preserveFragment; return this; }
        public Builder scrollProps(Map<String, Object> scrollProps) { this.scrollProps = scrollProps; return this; }
        public Builder sharedProps(List<String> sharedProps) { this.sharedProps = sharedProps; return this; }
```

- [ ] **Step 5: Wire preserveFragment and scrollProps through InertiaEngine**

In `inertiajs-core/src/main/java/io/github/emmajiugo/inertia/core/InertiaEngine.java`, update the PageObject builder in the `render` method. After `.clearHistory(options.getClearHistory())`, add:

```java
                .preserveFragment(options.getPreserveFragment())
                .scrollProps(options.getScrollProps())
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `./gradlew :inertiajs-core:test`
Expected: ALL PASS including new PreserveFragmentTests

- [ ] **Step 7: Commit**

```bash
git add -A && git commit -m "feat: add preserveFragment, scrollProps, sharedProps to PageObject and RenderOptions"
```

---

### Task 3: Create PropResolver with Nested Prop Resolution

**Files:**
- Create: `inertiajs-core/src/main/java/io/github/emmajiugo/inertia/core/PropResolver.java`
- Create: `inertiajs-core/src/test/java/io/github/emmajiugo/inertia/core/PropResolverTest.java`

- [ ] **Step 1: Write failing tests for recursive resolveProps**

Create `inertiajs-core/src/test/java/io/github/emmajiugo/inertia/core/PropResolverTest.java`:

```java
package io.github.emmajiugo.inertia.core;

import io.github.emmajiugo.inertia.core.props.*;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class PropResolverTest {

    // ── resolveProps ─────────────────────────────────────────────────

    @Nested
    class ResolveProps {

        @Test
        void resolvesTopLevelResolvable() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("name", new AlwaysProp<>(() -> "Alice"));
            props.put("plain", "hello");

            var resolved = PropResolver.resolveProps(props);

            assertThat(resolved).containsEntry("name", "Alice");
            assertThat(resolved).containsEntry("plain", "hello");
        }

        @SuppressWarnings("unchecked")
        @Test
        void resolvesNestedResolvableInsideMap() {
            Map<String, Object> inner = new LinkedHashMap<>();
            inner.put("permissions", new OptionalProp<>(() -> List.of("read", "write")));
            inner.put("name", "Alice");

            Map<String, Object> props = new LinkedHashMap<>();
            props.put("user", inner);

            var resolved = PropResolver.resolveProps(props);
            var user = (Map<String, Object>) resolved.get("user");

            assertThat(user).containsEntry("permissions", List.of("read", "write"));
            assertThat(user).containsEntry("name", "Alice");
        }

        @SuppressWarnings("unchecked")
        @Test
        void resolvesDeeplyNestedResolvable() {
            Map<String, Object> level2 = new LinkedHashMap<>();
            level2.put("deep", new AlwaysProp<>(() -> "value"));

            Map<String, Object> level1 = new LinkedHashMap<>();
            level1.put("nested", level2);

            Map<String, Object> props = new LinkedHashMap<>();
            props.put("top", level1);

            var resolved = PropResolver.resolveProps(props);
            var top = (Map<String, Object>) resolved.get("top");
            var nested = (Map<String, Object>) top.get("nested");

            assertThat(nested).containsEntry("deep", "value");
        }

        @Test
        void handlesEmptyMap() {
            var resolved = PropResolver.resolveProps(Map.of());
            assertThat(resolved).isEmpty();
        }

        @Test
        void passesNonResolvableNonMapValuesThrough() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("count", 42);
            props.put("items", List.of("a", "b"));
            props.put("nothing", null);

            var resolved = PropResolver.resolveProps(props);
            assertThat(resolved).containsEntry("count", 42);
            assertThat(resolved).containsEntry("items", List.of("a", "b"));
            assertThat(resolved).containsEntry("nothing", null);
        }
    }

    // ── buildDeferredPropsMap ────────────────────────────────────────

    @Nested
    class BuildDeferredPropsMap {

        @Test
        void findsTopLevelDeferredProps() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("comments", new DeferredProp<>(() -> "c"));
            props.put("title", "Hello");

            var deferred = PropResolver.buildDeferredPropsMap(props);

            assertThat(deferred).containsKey("default");
            assertThat(deferred.get("default")).containsExactly("comments");
        }

        @Test
        void findsNestedDeferredPropsWithDotNotation() {
            Map<String, Object> inner = new LinkedHashMap<>();
            inner.put("permissions", new DeferredProp<>(() -> List.of("read")));
            inner.put("name", "Alice");

            Map<String, Object> props = new LinkedHashMap<>();
            props.put("user", inner);

            var deferred = PropResolver.buildDeferredPropsMap(props);

            assertThat(deferred).containsKey("default");
            assertThat(deferred.get("default")).containsExactly("user.permissions");
        }

        @Test
        void groupsNestedDeferredProps() {
            Map<String, Object> inner = new LinkedHashMap<>();
            inner.put("roles", new DeferredProp<>(() -> List.of(), "auth"));

            Map<String, Object> props = new LinkedHashMap<>();
            props.put("user", inner);
            props.put("sidebar", new DeferredProp<>(() -> "s", "sidebar"));

            var deferred = PropResolver.buildDeferredPropsMap(props);

            assertThat(deferred.get("auth")).containsExactly("user.roles");
            assertThat(deferred.get("sidebar")).containsExactly("sidebar");
        }

        @Test
        void returnsEmptyMapWhenNoDeferredProps() {
            var deferred = PropResolver.buildDeferredPropsMap(Map.of("title", "Hello"));
            assertThat(deferred).isEmpty();
        }
    }

    // ── buildMergeMetadata ──────────────────────────────────────────

    @Nested
    class BuildMergeMetadata {

        @Test
        void findsTopLevelMergeProps() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("items", MergeProp.append(() -> List.of("a")));

            var metadata = PropResolver.buildMergeMetadata(props);

            assertThat(metadata.mergeProps()).containsExactly("items");
        }

        @Test
        void findsNestedMergePropsWithDotNotation() {
            Map<String, Object> inner = new LinkedHashMap<>();
            inner.put("feed", MergeProp.append(() -> List.of("post1")));
            inner.put("name", "Hello");

            Map<String, Object> props = new LinkedHashMap<>();
            props.put("page", inner);

            var metadata = PropResolver.buildMergeMetadata(props);

            assertThat(metadata.mergeProps()).containsExactly("page.feed");
        }

        @Test
        void distinguishesPrependAndDeepStrategies() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("feed", MergeProp.append(() -> List.of()));
            props.put("notifs", MergeProp.prepend(() -> List.of()));
            props.put("settings", MergeProp.deep(() -> Map.of()));

            var metadata = PropResolver.buildMergeMetadata(props);

            assertThat(metadata.mergeProps()).containsExactly("feed");
            assertThat(metadata.prependProps()).containsExactly("notifs");
            assertThat(metadata.deepMergeProps()).containsExactly("settings");
        }

        @Test
        void collectsMatchOn() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("items", MergeProp.append(() -> List.of()).matchOn("id"));

            var metadata = PropResolver.buildMergeMetadata(props);

            assertThat(metadata.matchPropsOn()).containsEntry("items", "id");
        }

        @Test
        void nestedMatchOnUsesDotNotation() {
            Map<String, Object> inner = new LinkedHashMap<>();
            inner.put("items", MergeProp.append(() -> List.of()).matchOn("id"));

            Map<String, Object> props = new LinkedHashMap<>();
            props.put("page", inner);

            var metadata = PropResolver.buildMergeMetadata(props);

            assertThat(metadata.matchPropsOn()).containsEntry("page.items", "id");
        }
    }

    // ── buildOncePropsMap ───────────────────────────────────────────

    @Nested
    class BuildOncePropsMap {

        @Test
        void findsTopLevelOnceProps() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("plans", new OnceProp<>(() -> List.of("free")));

            var onceMap = PropResolver.buildOncePropsMap(props);

            assertThat(onceMap).containsKey("plans");
            assertThat(onceMap.get("plans").prop()).isEqualTo("plans");
        }

        @Test
        void findsNestedOncePropsWithDotNotation() {
            Map<String, Object> inner = new LinkedHashMap<>();
            inner.put("roles", new OnceProp<>(() -> List.of("admin")));

            Map<String, Object> props = new LinkedHashMap<>();
            props.put("user", inner);

            var onceMap = PropResolver.buildOncePropsMap(props);

            assertThat(onceMap).containsKey("user.roles");
            assertThat(onceMap.get("user.roles").prop()).isEqualTo("user.roles");
        }

        @Test
        void oncePropCustomKeyPreserved() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("plans", new OnceProp<>(() -> List.of("free")).as("global_plans"));

            var onceMap = PropResolver.buildOncePropsMap(props);

            assertThat(onceMap).containsKey("global_plans");
            assertThat(onceMap.get("global_plans").prop()).isEqualTo("plans");
        }
    }

    // ── filterProps with dot-notation ───────────────────────────────

    @Nested
    class FilterProps {

        @Test
        void dotNotationOnlyIncludesNestedKey() {
            Map<String, Object> inner = new LinkedHashMap<>();
            inner.put("permissions", new OptionalProp<>(() -> List.of("read")));
            inner.put("name", "Alice");

            Map<String, Object> props = new LinkedHashMap<>();
            props.put("user", inner);
            props.put("title", "Page");

            var filtered = PropResolver.filterPropsOnly(props, Set.of("user.permissions"));

            assertThat(filtered).containsKey("user");
            @SuppressWarnings("unchecked")
            var user = (Map<String, Object>) filtered.get("user");
            assertThat(user).containsKey("permissions");
            assertThat(user).doesNotContainKey("name");
        }

        @Test
        void dotNotationExceptExcludesNestedKey() {
            Map<String, Object> inner = new LinkedHashMap<>();
            inner.put("permissions", "perms");
            inner.put("name", "Alice");

            Map<String, Object> props = new LinkedHashMap<>();
            props.put("user", inner);
            props.put("title", "Page");

            var filtered = PropResolver.filterPropsExcept(props, Set.of("user.permissions"));

            assertThat(filtered).containsKey("user");
            assertThat(filtered).containsKey("title");
            @SuppressWarnings("unchecked")
            var user = (Map<String, Object>) filtered.get("user");
            assertThat(user).doesNotContainKey("permissions");
            assertThat(user).containsKey("name");
        }

        @Test
        void topLevelOnlyStillWorks() {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("title", "Hello");
            props.put("description", "World");

            var filtered = PropResolver.filterPropsOnly(props, Set.of("title"));

            assertThat(filtered).containsKey("title");
            assertThat(filtered).doesNotContainKey("description");
        }
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :inertiajs-core:test --tests "*.PropResolverTest"`
Expected: FAIL — `PropResolver` class does not exist

- [ ] **Step 3: Implement PropResolver**

Create `inertiajs-core/src/main/java/io/github/emmajiugo/inertia/core/PropResolver.java`:

```java
package io.github.emmajiugo.inertia.core;

import io.github.emmajiugo.inertia.core.props.*;

import java.util.*;

/**
 * Recursive prop resolution engine. Walks nested maps to find prop wrappers
 * at any depth and builds dot-notation metadata for the client.
 */
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

    /**
     * Include only the specified keys. Supports dot-notation (e.g., "user.permissions").
     */
    @SuppressWarnings("unchecked")
    static Map<String, Object> filterPropsOnly(Map<String, Object> props, Set<String> only) {
        Map<String, Object> result = new LinkedHashMap<>();

        // Group dot-notation keys by their top-level prefix
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

    /**
     * Exclude the specified keys. Supports dot-notation (e.g., "user.permissions").
     */
    @SuppressWarnings("unchecked")
    static Map<String, Object> filterPropsExcept(Map<String, Object> props, Set<String> except) {
        Map<String, Object> result = new LinkedHashMap<>();

        // Group dot-notation keys by their top-level prefix
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
                continue; // excluded
            } else if (nestedExcept.containsKey(key) && value instanceof Map<?, ?> map) {
                result.put(key, filterPropsExcept((Map<String, Object>) map, nestedExcept.get(key)));
            } else {
                result.put(key, value);
            }
        }

        return result;
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :inertiajs-core:test --tests "*.PropResolverTest"`
Expected: ALL PASS

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: add PropResolver with recursive nested prop resolution and dot-notation"
```

---

### Task 4: Integrate PropResolver into InertiaEngine

**Files:**
- Modify: `inertiajs-core/src/main/java/io/github/emmajiugo/inertia/core/InertiaEngine.java`
- Test: `inertiajs-core/src/test/java/io/github/emmajiugo/inertia/core/InertiaEngineTest.java`

- [ ] **Step 1: Write failing test for nested deferred props via engine**

Add to `InertiaEngineTest.java`, inside the `DeferredPropsTests` class:

```java
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :inertiajs-core:test --tests "*.InertiaEngineTest\$DeferredPropsTests.nestedDeferredPropUseDotNotation"`
Expected: FAIL — current engine only finds top-level DeferredProp

- [ ] **Step 3: Replace InertiaEngine inline logic with PropResolver delegation**

Replace the entire `InertiaEngine.java` with the PropResolver-delegating version. Here are the specific changes:

**Replace the `render` method body** (the one with RenderOptions, starting at line 48) with:

```java
    public void render(InertiaRequest req, InertiaResponse res,
                       String component, Map<String, Object> props,
                       RenderOptions options) throws IOException {
        Objects.requireNonNull(req, "req must not be null");
        Objects.requireNonNull(res, "res must not be null");
        Objects.requireNonNull(component, "component must not be null");
        Objects.requireNonNull(options, "options must not be null");
        if (props == null) props = Map.of();

        Map<String, Object> mergedProps = mergeSharedProps(req, props);
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
```

**Delete these private methods** that are now in PropResolver:
- `buildOncePropsMap` (lines 127-136)
- `buildDeferredPropsMap` (lines 181-190)
- `buildMergeMetadata` and `MergeMetadata` record (lines 251-277)
- `resolveProps` (lines 281-292)

**Keep** these methods (they have engine-specific logic with headers):
- `mergeSharedProps`
- `isPartialReloadFor`
- `filterProps`
- `parseExceptOnceProps`
- `filterExceptOnceProps`
- `renderJson`, `renderHtml`, `resolveSsr`
- All public protocol methods

**Remove unused imports:**
- `import io.github.emmajiugo.inertia.core.props.DeferredProp;`
- `import io.github.emmajiugo.inertia.core.props.MergeProp;`
- `import io.github.emmajiugo.inertia.core.props.OnceProp;`
- `import io.github.emmajiugo.inertia.core.props.Resolvable;`

(Keep `AlwaysProp`, `OptionalProp`, `Prop` as they're used in `filterProps`.)

- [ ] **Step 4: Run all tests to verify refactor is behavior-preserving**

Run: `./gradlew :inertiajs-core:test`
Expected: ALL PASS — same behavior, different code path

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "refactor: delegate prop resolution from InertiaEngine to PropResolver"
```

---

### Task 5: Add New Request Header Handling

**Files:**
- Modify: `inertiajs-core/src/main/java/io/github/emmajiugo/inertia/core/InertiaEngine.java`
- Test: `inertiajs-core/src/test/java/io/github/emmajiugo/inertia/core/InertiaEngineTest.java`

- [ ] **Step 1: Write failing tests for isPrefetchRequest**

Add a new nested class in `InertiaEngineTest.java`:

```java
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :inertiajs-core:test --tests "*.InertiaEngineTest\$PrefetchDetection"`
Expected: FAIL — `isPrefetchRequest` method does not exist

- [ ] **Step 3: Implement isPrefetchRequest**

In `InertiaEngine.java`, add after the `location` method:

```java
    public boolean isPrefetchRequest(InertiaRequest req) {
        return "prefetch".equals(req.getHeader("Purpose"));
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :inertiajs-core:test --tests "*.InertiaEngineTest\$PrefetchDetection"`
Expected: PASS

- [ ] **Step 5: Write failing tests for redirectWithFragment**

Add a new nested class:

```java
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
```

- [ ] **Step 6: Run test to verify it fails**

Run: `./gradlew :inertiajs-core:test --tests "*.InertiaEngineTest\$FragmentRedirect"`
Expected: FAIL — `redirectWithFragment` method does not exist

- [ ] **Step 7: Implement redirectWithFragment**

In `InertiaEngine.java`, add after the `location` method:

```java
    public void redirectWithFragment(InertiaResponse res, String url) {
        res.setStatus(409);
        res.setHeader("X-Inertia-Redirect", url);
    }
```

- [ ] **Step 8: Run test to verify it passes**

Run: `./gradlew :inertiajs-core:test --tests "*.InertiaEngineTest\$FragmentRedirect"`
Expected: PASS

- [ ] **Step 9: Write failing tests for X-Inertia-Reset**

Add a new nested class:

```java
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
```

- [ ] **Step 10: Run test to verify it fails**

Run: `./gradlew :inertiajs-core:test --tests "*.InertiaEngineTest\$ResetProps"`
Expected: FAIL — reset header not being read

- [ ] **Step 11: Implement X-Inertia-Reset handling**

In `InertiaEngine.java`, in the `render` method, after building merge metadata and before building the PageObject, add logic to strip reset keys from merge metadata:

```java
        // Strip reset keys from merge metadata
        Set<String> resetKeys = parseCommaSeparatedHeader(req, "X-Inertia-Reset");
        PropResolver.MergeMetadata finalMergeMetadata = mergeMetadata;
        if (!resetKeys.isEmpty()) {
            finalMergeMetadata = new PropResolver.MergeMetadata(
                    new ArrayList<>(mergeMetadata.mergeProps()),
                    new ArrayList<>(mergeMetadata.prependProps()),
                    new ArrayList<>(mergeMetadata.deepMergeProps()),
                    new LinkedHashMap<>(mergeMetadata.matchPropsOn()));
            finalMergeMetadata.mergeProps().removeAll(resetKeys);
            finalMergeMetadata.prependProps().removeAll(resetKeys);
            finalMergeMetadata.deepMergeProps().removeAll(resetKeys);
            resetKeys.forEach(finalMergeMetadata.matchPropsOn()::remove);
        }
```

Update the PageObject builder to use `finalMergeMetadata` instead of `mergeMetadata`:

```java
                .mergeProps(finalMergeMetadata.mergeProps())
                .prependProps(finalMergeMetadata.prependProps())
                .deepMergeProps(finalMergeMetadata.deepMergeProps())
                .matchPropsOn(finalMergeMetadata.matchPropsOn())
```

Add the helper method:

```java
    private Set<String> parseCommaSeparatedHeader(InertiaRequest req, String headerName) {
        String header = req.getHeader(headerName);
        if (header == null || header.isBlank()) return Set.of();
        return Arrays.stream(header.split(","))
                .map(String::trim)
                .collect(Collectors.toUnmodifiableSet());
    }
```

Add import for `ArrayList` and `LinkedHashMap` if not already present.

- [ ] **Step 12: Run test to verify it passes**

Run: `./gradlew :inertiajs-core:test --tests "*.InertiaEngineTest\$ResetProps"`
Expected: PASS

- [ ] **Step 13: Write failing tests for X-Inertia-Infinite-Scroll-Merge-Intent**

Add a new nested class:

```java
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
```

- [ ] **Step 14: Run test to verify it fails**

Run: `./gradlew :inertiajs-core:test --tests "*.InertiaEngineTest\$InfiniteScrollMergeIntent"`
Expected: FAIL

- [ ] **Step 15: Implement X-Inertia-Infinite-Scroll-Merge-Intent handling**

In the `render` method in `InertiaEngine.java`, after building merge metadata (and before the reset handling), add:

```java
        // Apply infinite scroll merge intent override
        String mergeIntent = req.getHeader("X-Inertia-Infinite-Scroll-Merge-Intent");
        if (mergeIntent != null && !mergeIntent.isBlank()) {
            mergeMetadata = applyMergeIntentOverride(mergeMetadata, mergeIntent.trim());
        }
```

Add the helper method:

```java
    private PropResolver.MergeMetadata applyMergeIntentOverride(
            PropResolver.MergeMetadata metadata, String intent) {
        // Collect all merge-type keys into a single list
        List<String> allKeys = new ArrayList<>();
        allKeys.addAll(metadata.mergeProps());
        allKeys.addAll(metadata.prependProps());
        // Deep merge props are not affected by infinite scroll intent

        if (allKeys.isEmpty()) return metadata;

        List<String> merge = new ArrayList<>();
        List<String> prepend = new ArrayList<>();

        if ("append".equalsIgnoreCase(intent)) {
            merge.addAll(allKeys);
        } else if ("prepend".equalsIgnoreCase(intent)) {
            prepend.addAll(allKeys);
        } else {
            return metadata; // Unknown intent, no override
        }

        return new PropResolver.MergeMetadata(merge, prepend,
                metadata.deepMergeProps(), metadata.matchPropsOn());
    }
```

- [ ] **Step 16: Run all tests to verify everything passes**

Run: `./gradlew :inertiajs-core:test`
Expected: ALL PASS

- [ ] **Step 17: Commit**

```bash
git add -A && git commit -m "feat: add prefetch detection, fragment redirect, reset props, and merge intent override"
```

---

### Task 6: Add Error Bag Support in Core

**Files:**
- Modify: `inertiajs-core/src/main/java/io/github/emmajiugo/inertia/core/InertiaEngine.java`
- Test: `inertiajs-core/src/test/java/io/github/emmajiugo/inertia/core/InertiaEngineTest.java`

- [ ] **Step 1: Write failing tests for error bag selection**

Add a new nested class in `InertiaEngineTest.java`:

```java
    // ── Error Bags ──────────────────────────────────────────────────

    @Nested
    class ErrorBags {

        @Test
        void selectsErrorBagByHeader() throws IOException {
            // Shared props resolver returns nested error bags
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
            // When errors is a flat map (not bags), treat as-is
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
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :inertiajs-core:test --tests "*.InertiaEngineTest\$ErrorBags"`
Expected: FAIL — error bag logic not implemented

- [ ] **Step 3: Implement error bag selection in mergeSharedProps**

In `InertiaEngine.java`, replace the `mergeSharedProps` method with:

```java
    private Map<String, Object> mergeSharedProps(InertiaRequest req, Map<String, Object> pageProps) {
        Map<String, Object> merged = new LinkedHashMap<>();
        for (SharedPropsResolver resolver : sharedPropsResolvers) {
            merged.putAll(resolver.resolve(req));
        }
        merged.putAll(pageProps);

        // Error bag selection
        resolveErrorBag(req, merged);

        merged.putIfAbsent("errors", Map.of());
        return merged;
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
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :inertiajs-core:test --tests "*.InertiaEngineTest\$ErrorBags"`
Expected: ALL PASS

- [ ] **Step 5: Run all core tests**

Run: `./gradlew :inertiajs-core:test`
Expected: ALL PASS

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "feat: add error bag selection via X-Inertia-Error-Bag header"
```

---

### Task 7: Add Shared Props Tracking

**Files:**
- Modify: `inertiajs-core/src/main/java/io/github/emmajiugo/inertia/core/InertiaEngine.java`
- Test: `inertiajs-core/src/test/java/io/github/emmajiugo/inertia/core/InertiaEngineTest.java`

- [ ] **Step 1: Write failing tests for sharedProps field**

Add a new nested class in `InertiaEngineTest.java`:

```java
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
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :inertiajs-core:test --tests "*.InertiaEngineTest\$SharedPropsTracking"`
Expected: FAIL — `sharedProps` not being populated

- [ ] **Step 3: Implement shared props tracking**

In `InertiaEngine.java`, modify `mergeSharedProps` to return shared key names alongside the merged map. The simplest approach is to change the render method to track shared keys separately.

Replace the `mergeSharedProps` method signature and update the render method:

```java
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

        // "errors" from shared resolvers is tracked as shared
        List<String> sharedKeys = new ArrayList<>(sharedKeySet);

        return new MergedPropsResult(merged, sharedKeys);
    }
```

In the `render` method, update the call from:

```java
        Map<String, Object> mergedProps = mergeSharedProps(req, props);
```

to:

```java
        MergedPropsResult mergedResult = mergeSharedProps(req, props);
        Map<String, Object> mergedProps = mergedResult.props();
        List<String> sharedPropKeys = mergedResult.sharedKeys();
```

And add `.sharedProps(sharedPropKeys)` to the PageObject builder.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :inertiajs-core:test`
Expected: ALL PASS

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: track shared prop keys in PageObject sharedProps field"
```

---

### Task 8: Update Spring Adapter for Error Bags and Fragment Redirect

**Files:**
- Modify: `inertiajs-spring/src/main/java/io/github/emmajiugo/inertia/spring/Inertia.java`
- Modify: `inertiajs-spring/src/main/java/io/github/emmajiugo/inertia/spring/InertiaValidationSharedProps.java`
- Test: `inertiajs-spring/src/test/java/io/github/emmajiugo/inertia/spring/` (existing or new test file)

- [ ] **Step 1: Write failing test for redirectWithErrors with error bag**

Check if there's an existing Spring adapter test file. If not, create one. Add this test (in the appropriate file):

In `InertiaEngineTest.java` pattern, the Spring tests are in `inertiajs-spring/src/test/java/`. Create or add to a test file:

```java
// If you need to create: inertiajs-spring/src/test/java/io/github/emmajiugo/inertia/spring/InertiaSpringTest.java
package io.github.emmajiugo.inertia.spring;

import io.github.emmajiugo.inertia.core.InertiaConfig;
import io.github.emmajiugo.inertia.core.InertiaEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InertiaSpringTest {

    private Inertia inertia;

    @BeforeEach
    void setUp() {
        InertiaConfig config = InertiaConfig.builder()
                .version("1.0.0")
                .templateResolver(pageJson -> "<html>" + pageJson + "</html>")
                .build();
        inertia = new Inertia(new InertiaEngine(config));
    }

    @Nested
    class ErrorBagStorage {

        @SuppressWarnings("unchecked")
        @Test
        void storesErrorsUnderNamedBag() {
            var req = new MockHttpServletRequest();
            var res = new MockHttpServletResponse();

            inertia.redirectWithErrors(req, res, "/form",
                    Map.of("email", "Invalid"), "login");

            var session = req.getSession();
            var storedErrors = (Map<String, Map<String, ?>>) session.getAttribute(Inertia.ERRORS_SESSION_KEY);
            assertThat(storedErrors).containsKey("login");
            assertThat(storedErrors.get("login")).containsEntry("email", "Invalid");
        }

        @SuppressWarnings("unchecked")
        @Test
        void storesErrorsUnderDefaultBagWhenNoBagSpecified() {
            var req = new MockHttpServletRequest();
            var res = new MockHttpServletResponse();

            inertia.redirectWithErrors(req, res, "/form",
                    Map.of("name", "Required"));

            var session = req.getSession();
            var storedErrors = (Map<String, Map<String, ?>>) session.getAttribute(Inertia.ERRORS_SESSION_KEY);
            assertThat(storedErrors).containsKey("default");
            assertThat(storedErrors.get("default")).containsEntry("name", "Required");
        }
    }

    @Nested
    class FragmentRedirectSpring {

        @Test
        void redirectWithFragmentSets409AndHeader() {
            var res = new MockHttpServletResponse();

            inertia.redirectWithFragment(res, "/page#section");

            assertThat(res.getStatus()).isEqualTo(409);
            assertThat(res.getHeader("X-Inertia-Redirect")).isEqualTo("/page#section");
        }
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :inertiajs-spring:test --tests "*.InertiaSpringTest"`
Expected: FAIL — methods don't exist

- [ ] **Step 3: Update Spring Inertia wrapper**

In `inertiajs-spring/src/main/java/io/github/emmajiugo/inertia/spring/Inertia.java`:

Add the new `redirectWithErrors` overload with error bag:

```java
    /**
     * Redirect back with validation errors scoped to a named error bag.
     */
    public void redirectWithErrors(HttpServletRequest req, HttpServletResponse res,
                                   String url, Map<String, ?> errors, String errorBag) {
        HttpSession session = req.getSession();
        @SuppressWarnings("unchecked")
        Map<String, Map<String, ?>> bags = (Map<String, Map<String, ?>>) session.getAttribute(ERRORS_SESSION_KEY);
        if (bags == null) {
            bags = new HashMap<>();
        }
        bags.put(errorBag, errors);
        session.setAttribute(ERRORS_SESSION_KEY, bags);
        res.setStatus(303);
        res.setHeader("Location", url);
    }
```

Update the existing `redirectWithErrors` (no bag) to delegate:

```java
    public void redirectWithErrors(HttpServletRequest req, HttpServletResponse res,
                                   String url, Map<String, ?> errors) {
        redirectWithErrors(req, res, url, errors, "default");
    }
```

Add the `redirectWithFragment` method:

```java
    public void redirectWithFragment(HttpServletResponse res, String url) {
        engine.redirectWithFragment(new SpringInertiaResponse(res), url);
    }
```

- [ ] **Step 4: Update InertiaValidationSharedProps to return bag structure**

In `inertiajs-spring/src/main/java/io/github/emmajiugo/inertia/spring/InertiaValidationSharedProps.java`, the errors reading section should pass through the bag map as-is. The engine's `resolveErrorBag` handles selection. The current code already does `shared.put("errors", errors)` where `errors` is whatever is in the session — so if the session now holds a nested bag map, it passes through automatically.

No changes needed to `InertiaValidationSharedProps` — it already passes the raw session value through. The engine's `resolveErrorBag` method handles both flat and nested structures.

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew :inertiajs-spring:test`
Expected: ALL PASS

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "feat: add error bag support and fragment redirect to Spring adapter"
```

---

### Task 9: Update Javalin Adapter for Error Bags and Fragment Redirect

**Files:**
- Modify: `inertiajs-javalin/src/main/java/io/github/emmajiugo/inertia/javalin/Inertia.java`
- Modify: `inertiajs-javalin/src/main/java/io/github/emmajiugo/inertia/javalin/InertiaSessionHolder.java`
- Modify: `inertiajs-javalin/src/main/java/io/github/emmajiugo/inertia/javalin/InertiaPlugin.java`

- [ ] **Step 1: Update InertiaSessionHolder to use nested error map**

In `inertiajs-javalin/src/main/java/io/github/emmajiugo/inertia/javalin/InertiaSessionHolder.java`, change the ERRORS ThreadLocal type:

```java
final class InertiaSessionHolder {

    private static final ThreadLocal<Map<String, Map<String, ?>>> ERRORS = new ThreadLocal<>();
    private static final ThreadLocal<Map<String, Object>> FLASH = new ThreadLocal<>();

    private InertiaSessionHolder() {}

    static void setErrors(Map<String, Map<String, ?>> errors) { ERRORS.set(errors); }

    static Map<String, Map<String, ?>> getAndClearErrors() {
        Map<String, Map<String, ?>> errors = ERRORS.get();
        ERRORS.remove();
        return errors;
    }

    static void setFlash(Map<String, Object> flash) { FLASH.set(flash); }

    static Map<String, Object> getAndClearFlash() {
        Map<String, Object> flash = FLASH.get();
        FLASH.remove();
        return flash;
    }
}
```

- [ ] **Step 2: Update InertiaPlugin shared props resolver and before handler**

In `inertiajs-javalin/src/main/java/io/github/emmajiugo/inertia/javalin/InertiaPlugin.java`:

Update the shared props resolver to use the new type:

```java
        engine.addSharedPropsResolver(req -> {
            Map<String, Object> shared = new HashMap<>();

            Map<String, Map<String, ?>> errors = InertiaSessionHolder.getAndClearErrors();
            if (errors != null && !errors.isEmpty()) {
                shared.put("errors", errors);
            }

            Map<String, Object> flash = InertiaSessionHolder.getAndClearFlash();
            if (flash != null && !flash.isEmpty()) {
                shared.put("flash", flash);
            }

            return shared;
        });
```

Update the `before` handler errors section:

```java
            // Move session errors to ThreadLocal
            @SuppressWarnings("unchecked")
            Map<String, Map<String, ?>> errors = ctx.sessionAttribute(Inertia.ERRORS_SESSION_KEY);
            if (errors != null) {
                ctx.sessionAttribute(Inertia.ERRORS_SESSION_KEY, null);
                InertiaSessionHolder.setErrors(errors);
            }
```

- [ ] **Step 3: Update Javalin Inertia wrapper**

In `inertiajs-javalin/src/main/java/io/github/emmajiugo/inertia/javalin/Inertia.java`:

Add the error bag overload:

```java
    /**
     * Redirect back with validation errors scoped to a named error bag.
     */
    @SuppressWarnings("unchecked")
    public void redirectWithErrors(Context ctx, String url, Map<String, ?> errors, String errorBag) {
        Map<String, Map<String, ?>> bags = ctx.sessionAttribute(ERRORS_SESSION_KEY);
        if (bags == null) {
            bags = new HashMap<>();
        }
        bags.put(errorBag, errors);
        ctx.sessionAttribute(ERRORS_SESSION_KEY, bags);
        ctx.status(303);
        ctx.header("Location", url);
    }
```

Update the existing `redirectWithErrors` to delegate:

```java
    public void redirectWithErrors(Context ctx, String url, Map<String, ?> errors) {
        redirectWithErrors(ctx, url, errors, "default");
    }
```

Add the `redirectWithFragment` method:

```java
    public void redirectWithFragment(Context ctx, String url) {
        engine.redirectWithFragment(new JavalinInertiaResponse(ctx), url);
    }
```

- [ ] **Step 4: Run all tests**

Run: `./gradlew build`
Expected: ALL PASS across all modules

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: add error bag support and fragment redirect to Javalin adapter"
```

---

### Task 10: Integration Test — Nested Props End-to-End

**Files:**
- Test: `inertiajs-core/src/test/java/io/github/emmajiugo/inertia/core/InertiaEngineTest.java`

- [ ] **Step 1: Write end-to-end nested props test**

Add a new nested class in `InertiaEngineTest.java`:

```java
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
```

- [ ] **Step 2: Run the integration tests**

Run: `./gradlew :inertiajs-core:test --tests "*.InertiaEngineTest\$NestedPropsIntegration"`
Expected: Some tests may fail if `filterProps` doesn't yet use PropResolver for dot-notation. This is expected.

- [ ] **Step 3: Update filterProps in InertiaEngine to use PropResolver for dot-notation**

In `InertiaEngine.java`, update the `filterProps` method to delegate dot-notation handling to `PropResolver`. Replace the current `filterProps` method:

```java
    private Map<String, Object> filterProps(InertiaRequest req, String component,
                                            Map<String, Object> props, boolean isPartialReload) {
        // Not a partial reload — include everything except OptionalProp and DeferredProp (recursively)
        if (!isPartialReload) {
            return filterNonPartial(props);
        }

        // Partial reload with "only" list
        String partialData = req.getHeader("X-Inertia-Partial-Data");
        if (partialData != null && !partialData.isBlank()) {
            Set<String> only = parseCommaSeparatedHeader(req, "X-Inertia-Partial-Data");
            Map<String, Object> filtered = PropResolver.filterPropsOnly(props, only);
            // Always include AlwaysProp and errors
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
            // Restore AlwaysProp and errors that may have been excluded
            for (Map.Entry<String, Object> entry : props.entrySet()) {
                if (entry.getValue() instanceof AlwaysProp<?> || entry.getKey().equals("errors")) {
                    filtered.putIfAbsent(entry.getKey(), entry.getValue());
                }
            }
            // Still exclude OptionalProp at top level
            filtered.entrySet().removeIf(e -> e.getValue() instanceof OptionalProp<?>);
            return filtered;
        }

        // Partial reload with no only/except — include everything
        return new LinkedHashMap<>(props);
    }

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
```

Add imports for `DeferredProp` and `OptionalProp` back if they were removed in Task 4 (they're needed in `filterNonPartial`):

```java
import io.github.emmajiugo.inertia.core.props.DeferredProp;
import io.github.emmajiugo.inertia.core.props.OptionalProp;
```

- [ ] **Step 4: Run all tests**

Run: `./gradlew build`
Expected: ALL PASS across all modules

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: add nested props integration with dot-notation partial reload filtering"
```

---

### Task 11: Final Full Build Verification

**Files:** None (verification only)

- [ ] **Step 1: Run full build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL — all modules compile, all tests pass

- [ ] **Step 2: Verify no remaining LazyProp references**

Run: `grep -r "LazyProp\|InertiaProps\.lazy" --include="*.java" .`
Expected: No matches

- [ ] **Step 3: Verify new v3 features are testable**

Run: `./gradlew :inertiajs-core:test --tests "*.InertiaEngineTest" -i 2>&1 | grep -E "PASS|FAIL|tests"`
Expected: All tests pass, including new nested props, error bags, reset props, merge intent, prefetch, fragment redirect, shared props tracking, preserve fragment tests

- [ ] **Step 4: Commit (if any fixes were needed)**

```bash
git add -A && git commit -m "chore: final v3 upgrade verification"
```
