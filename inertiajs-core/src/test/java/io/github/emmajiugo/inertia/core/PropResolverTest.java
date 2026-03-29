package io.github.emmajiugo.inertia.core;

import io.github.emmajiugo.inertia.core.props.*;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class PropResolverTest {

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
