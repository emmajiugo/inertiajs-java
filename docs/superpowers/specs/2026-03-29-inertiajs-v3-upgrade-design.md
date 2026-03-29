# Inertia.js v3 Upgrade — Design Spec

**Goal:** Upgrade the inertiajs-java adapter from v2 to full v3 protocol parity. This is a breaking change — v2 is no longer supported.

**Approach:** Incremental in-place upgrade on `main`. No new modules. Hard removal of deprecated v2 features.

**Tech Stack:** Java 17+, Jackson, JUnit 5, Spring Boot 4.x, Javalin 7.x

---

## 1. Removals (v2 Features)

### 1.1 LazyProp

- Delete `LazyProp.java`
- Remove from sealed `Prop<T>` interface permits list: `permits AlwaysProp, OptionalProp, DeferredProp`
- Remove `InertiaProps.lazy()` factory method
- Remove all `LazyProp` test cases from `InertiaEngineTest`
- Remove any `LazyProp` imports from `InertiaEngine`

---

## 2. Core Module Changes (`inertiajs-core`)

### 2.1 PageObject — New Fields

| Field | Type | Serialization | Purpose |
|-------|------|---------------|---------|
| `preserveFragment` | `Boolean` | null when not set (NON_NULL) | Preserve URL fragment across redirects |
| `scrollProps` | `Map<String, Object>` | null when empty | Infinite scroll pagination config |
| `sharedProps` | `List<String>` | null when empty | Top-level shared prop key names for client |

Builder gains corresponding setter methods. Same `nullIfEmpty` pattern as existing fields.

### 2.2 RenderOptions — New Fields

- `preserveFragment(boolean)` builder method, flows through to PageObject at render time.
- `scrollProps(Map<String, Object>)` builder method, flows through to PageObject. Allows controllers to specify scroll region configuration for infinite scroll pagination.

### 2.3 PropResolver — New Class

Extract recursive prop logic from `InertiaEngine` into `PropResolver`:

- **`resolveProps(Map<String, Object>)`** — recursively walks maps. Calls `.resolve()` on any `Resolvable<?>`. Non-map, non-Resolvable values pass through unchanged.
- **`buildDeferredPropsMap(Map<String, Object>)`** — recursively finds `DeferredProp` at any depth, returns `Map<String, List<String>>` with dot-notation keys (e.g., `"user.permissions"`).
- **`buildMergeMetadata(Map<String, Object>)`** — recursive for `MergeProp`, dot-notation keys.
- **`buildOncePropsMap(Map<String, Object>)`** — recursive for `OnceProp`, dot-notation keys.
- **`filterProps(...)`** — partial reload filtering supports dot-notation in `X-Inertia-Partial-Data` and `X-Inertia-Partial-Except`. E.g., `"user.permissions"` includes/excludes the `permissions` key inside the `user` map.

`InertiaEngine` delegates to `PropResolver` for all prop resolution.

### 2.4 InertiaEngine — New Header Handling

**Request headers:**

| Header | Behavior |
|--------|----------|
| `X-Inertia-Reset` | Comma-separated keys. Exclude these from merge/prepend/deep metadata (client wants reset, not merge). |
| `X-Inertia-Infinite-Scroll-Merge-Intent` | Value: `append` or `prepend`. Overrides `MergeProp` strategy for all MergeProps in the response. |
| `Purpose` | When value is `prefetch`, marks request as prefetch. Exposed via `isPrefetchRequest(InertiaRequest)` public method. |
| `X-Inertia-Error-Bag` | Selects which error bag to use from the nested errors map. Falls back to full errors when absent. |

**Response methods:**

- `redirectWithFragment(InertiaResponse res, String url)` — sends 409 with `X-Inertia-Redirect` header (not `X-Inertia-Location`). Triggers a standard Inertia visit with fragment preserved.

### 2.5 InertiaEngine — Shared Props Tracking

After merging shared props with page props, record which top-level keys originated from shared resolvers. Populate `sharedProps` field on PageObject with these key names.

### 2.6 InertiaEngine — Error Bag Integration

During `mergeSharedProps`:
- Read `X-Inertia-Error-Bag` header from request
- Shared props resolvers return errors as `Map<String, Map<String, Object>>` (bag name -> errors)
- When `X-Inertia-Error-Bag` is present, select `errors.<bagName>` from the nested map
- When absent, select `errors.default`
- Result placed in `props.errors` as a flat error map (the client sees a flat map regardless of bags)

---

## 3. Spring Adapter Changes (`inertiajs-spring`)

### 3.1 `Inertia` Wrapper

**New methods:**
- `redirectWithErrors(HttpServletRequest, HttpServletResponse, String url, Map<String, Object> errors, String errorBag)` — stores errors nested under the bag name in session
- `redirectWithFragment(HttpServletResponse, String url)` — delegates to `engine.redirectWithFragment()`

**Updated methods:**
- `redirectWithErrors(req, res, url, errors)` — stores under `"default"` bag internally (backward compatible signature)

### 3.2 `InertiaValidationSharedProps`

- Reads `io.inertia.errors` from session as `Map<String, Map<String, Object>>` (bag name -> errors)
- Returns the full bag map as shared prop — engine handles bag selection via `X-Inertia-Error-Bag` header

### 3.3 No Changes Needed

- `InertiaHandlerInterceptor` — version mismatch and redirect upgrade logic unchanged
- `InertiaAutoConfiguration` — bean wiring unchanged

---

## 4. Javalin Adapter Changes (`inertiajs-javalin`)

### 4.1 `Inertia` Wrapper

Same as Spring:
- `redirectWithErrors(Context, String url, Map<String, Object> errors, String errorBag)` — new overload
- `redirectWithErrors(Context, String url, Map<String, Object> errors)` — stores under `"default"` bag
- `redirectWithFragment(Context, String url)` — delegates to engine

### 4.2 `InertiaPlugin` / `InertiaSessionHolder`

- `InertiaSessionHolder` stores `Map<String, Map<String, Object>>` for errors (bag name -> errors)
- Shared props resolver returns the full bag map — engine handles bag selection

### 4.3 No Changes Needed

- Middleware (`before`/`after` handlers) — same version mismatch and redirect upgrade behavior

---

## 5. Testing Strategy

### 5.1 Removals

- Delete all `LazyProp` test cases from `InertiaEngineTest`

### 5.2 New `PropResolverTest`

- Resolves nested `Resolvable` wrappers at arbitrary depth
- Builds dot-notation deferred/merge/once metadata
- Handles mixed maps (some nested props, some plain values)
- Edge cases: empty maps, null values, deeply nested (3+ levels)
- Dot-notation partial reload filtering (`X-Inertia-Partial-Data: user.permissions`)

### 5.3 New `InertiaEngineTest` Groups

- **Reset props** — `X-Inertia-Reset` excludes keys from merge metadata
- **Infinite scroll merge intent** — header overrides MergeProp strategy
- **Prefetch detection** — `Purpose: prefetch` detected via `isPrefetchRequest()`
- **Error bags** — `X-Inertia-Error-Bag` selects correct bag; absent header returns default
- **Fragment redirect** — `redirectWithFragment()` sends 409 + `X-Inertia-Redirect`
- **Shared props tracking** — `sharedProps` lists shared resolver key names
- **Preserve fragment** — flows from RenderOptions to PageObject
- **Nested props** — DeferredProp/OptionalProp/MergeProp/OnceProp nested in maps, dot-notation metadata

### 5.4 Adapter Tests

- **Spring** — `redirectWithErrors` with error bag stores nested map; `InertiaValidationSharedProps` reads nested structure
- **Javalin** — same error bag flow through `InertiaSessionHolder`

### 5.5 Existing Tests

All tests not referencing `LazyProp` should pass unchanged — v3 changes are additive beyond the removal.

---

## 6. Files Changed

### Created
- `inertiajs-core/src/main/java/.../core/PropResolver.java`
- `inertiajs-core/src/test/java/.../core/PropResolverTest.java`

### Modified
- `inertiajs-core/src/main/java/.../core/PageObject.java` — 3 new fields
- `inertiajs-core/src/main/java/.../core/RenderOptions.java` — `preserveFragment`
- `inertiajs-core/src/main/java/.../core/InertiaEngine.java` — delegate to PropResolver, new headers, shared props tracking, error bags, redirectWithFragment
- `inertiajs-core/src/main/java/.../core/props/Prop.java` — remove LazyProp from permits
- `inertiajs-core/src/main/java/.../core/props/InertiaProps.java` — remove `lazy()`
- `inertiajs-core/src/test/java/.../core/InertiaEngineTest.java` — remove LazyProp tests, add v3 tests
- `inertiajs-spring/src/main/java/.../spring/Inertia.java` — new methods
- `inertiajs-spring/src/main/java/.../spring/InertiaValidationSharedProps.java` — nested error map
- `inertiajs-spring/src/test/java/.../spring/` — error bag tests
- `inertiajs-javalin/src/main/java/.../javalin/Inertia.java` — new methods
- `inertiajs-javalin/src/main/java/.../javalin/InertiaPlugin.java` — nested error map in session holder
- `inertiajs-javalin/src/test/java/.../javalin/` — error bag tests

### Deleted
- `inertiajs-core/src/main/java/.../core/props/LazyProp.java`
