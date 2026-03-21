# Future Features

## Lower Priority / Larger Effort

- **Quarkus adapter** (`inertiajs-quarkus`) — Wraps Quarkus `RoutingContext`. Uses `ContainerRequestFilter`.
- **Micronaut adapter** (`inertiajs-micronaut`) — Wraps Micronaut's `HttpRequest`/`MutableHttpResponse`.

## Completed

- **Deferred props** — Props excluded from initial render, loaded via follow-up partial reload. Supports grouping for parallel loading. (`DeferredProp`, `InertiaProps.defer()`)
- **Merge props** — Props that append/prepend/deep-merge with existing client-side data during navigation. Supports `matchOn` for deduplication. (`MergeProp`, `InertiaProps.merge()`, `prepend()`, `deepMerge()`)
- **Vite integration** — Auto-detect asset version from Vite manifest hash. (`ViteManifestVersionResolver`)
- **Once props** — Props resolved once and cached client-side. Server checks `X-Inertia-Except-Once-Props` header and skips already-loaded props. Supports custom keys and expiration. (`OnceProp`, `InertiaProps.once()`)
- **History encryption** — `encryptHistory` and `clearHistory` fields on page object, set via `RenderOptions`.
- **Precognition validation** — Real-time form validation. Detects `Precognition: true` header, returns 204 (success) or 422 (errors). (`Precognition` helper, `inertia.isPrecognitionRequest()`, `inertia.precognitionRespond()`)
- **Spring Boot DevTools** — Template re-reading in dev mode via `inertia.cache-templates=false`. (`ClasspathTemplateResolver` cache flag)
- **SSR (Server-Side Rendering)** — Render Vue/React components on the server for SEO and initial load performance. Framework-agnostic, follows official Inertia SSR protocol (HTTP to persistent Node.js SSR server). Configurable fallback (graceful CSR by default or fail-hard). Global default + per-render override via `RenderOptions.ssr()`. (`SsrClient`, `HttpSsrClient`, `SsrGateway`)
- **Project scaffolding CLI** (`npx create-inertiajs-java`) — Interactive initializer that scaffolds the frontend directory and templates inside an existing Java project. Supports Vue/React/Svelte and Spring Boot/Javalin.
- **Javalin adapter** (`inertiajs-javalin`) — Wraps Javalin 7's `Context`. Plugin with before/after middleware.

## Further Inertia JS
InertiaJs docs (https://inertiajs.com/docs/llms.txt)
