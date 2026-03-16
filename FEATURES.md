# Future Features

Features not yet implemented, tracked for incremental addition after the MVP.

## Props

- **Deferred props** — Props loaded after initial render via a follow-up request. Server wraps callback with `Inertia::defer()`, client uses `<Deferred>` component. Supports grouping for parallel loading.
- **Once props** — Props resolved once and cached client-side across navigations. Server checks `X-Inertia-Except-Once-Props` header and omits already-loaded keys.
- **Merge props** — Props that append/prepend to existing client-side data during navigation (for infinite scroll). Page object fields: `mergeProps`, `deepMergeProps`, `prependProps`.
- **Match props on** — Key-based matching during merge operations (`matchPropsOn` field).

## Security

- **History encryption** — Encrypt sensitive data stored in browser history state. Page object fields: `encryptHistory`, `clearHistory`.
- **Precognition validation** — Real-time validation before form submission using `Precognition` header. Returns 204 on success, 422 on error.

## Rendering

- **SSR (Server-Side Rendering)** — Render Vue/React components on the server for SEO and initial load performance.

## Framework Adapters

- **Quarkus adapter** (`inertiajs-quarkus`) — Wraps Quarkus `RoutingContext`. Uses `ContainerRequestFilter`.
- **Micronaut adapter** (`inertiajs-micronaut`) — Wraps Micronaut's `HttpRequest`/`MutableHttpResponse`.

## Developer Experience

- **Project scaffolding CLI** (`npx create-inertia-java`) — Interactive initializer that scaffolds the frontend directory (package.json, vite.config.ts, app.ts, sample page) and root HTML template. Build-tool agnostic (works with Gradle and Maven). Prompts for frontend framework (Vue/React/Svelte). Follows the `create-vite` / `create-next-app` pattern.
- **Named routes** — Generate URLs from route names (like Laravel's `route()` helper).
- **Vite integration** — Auto-detect asset version from Vite manifest hash.
- **Spring Boot DevTools** — Auto-reload template on changes during development.

## Further Inertia JS
InertiaJs docs (https://inertiajs.com/docs/llms.txt)
