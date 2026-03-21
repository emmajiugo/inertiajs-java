# Inertia.js Java Adapter

A server-side adapter for [Inertia.js](https://inertiajs.com) v2, enabling you to build modern single-page apps using Vue, React, or Svelte with a Java backend — no API required.

Supports **Spring Boot** and **Javalin** out of the box. The core protocol engine is framework-agnostic, so adding support for other frameworks (Quarkus, Micronaut, etc.) is straightforward.

## Getting Started

The fastest way to add Inertia.js to an existing Java project:

```bash
cd your-java-project
npx create-inertiajs-java
```

This interactive CLI will:
1. Ask you to pick a frontend framework (Vue, React, or Svelte)
2. Ask which backend you're using (Spring Boot or Javalin)
3. Scaffold `frontend/` with package.json, Vite config, app entry point, and a sample page
4. Create `src/main/resources/templates/app.html` and `app-dev.html`
5. Print step-by-step instructions to wire up your backend

You can also run it non-interactively:

```bash
npx create-inertiajs-java --frontend vue --backend spring
```

## Manual Setup (Spring Boot)

### 1. Add the dependency

```kotlin
// build.gradle.kts
dependencies {
    implementation("io.github.emmajiugo:inertiajs-spring:0.1.0-SNAPSHOT")
}
```

```xml
<!-- pom.xml -->
<dependency>
    <groupId>io.github.emmajiugo</groupId>
    <artifactId>inertiajs-spring</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

### 2. Create root templates

**Production** — `src/main/resources/templates/app.html`

```html
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>My App</title>
    @inertiaHead
</head>
<body>
    @inertia
    <script type="module" src="/assets/app.js"></script>
</body>
</html>
```

**Dev** — `src/main/resources/templates/app-dev.html`

```html
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>My App</title>
    @inertiaHead
</head>
<body>
    @inertia
    <script type="module" src="http://localhost:5173/src/app.ts"></script>
</body>
</html>
```

### 3. Configure

```properties
# application.properties
inertia.template-path=templates/app.html
# Version is auto-detected from Vite manifest — no manual config needed
```

```properties
# application-dev.properties
inertia.template-path=templates/app-dev.html
inertia.cache-templates=false
```

### 4. Write a controller

```java
@Controller
public class EventController {

    @Autowired private Inertia inertia;

    @GetMapping("/events")
    public void index(HttpServletRequest req, HttpServletResponse res) throws IOException {
        inertia.render(req, res, "Events/Index", Map.of(
            "events", eventService.findAll()
        ));
    }

    @PostMapping("/events")
    public void store(@RequestBody Map<String, String> body,
                      HttpServletRequest req, HttpServletResponse res) {
        Map<String, String> errors = validate(body);
        if (!errors.isEmpty()) {
            inertia.redirectWithErrors(req, res, "/events/create", errors);
            return;
        }
        eventService.create(body);
        inertia.redirect(res, "/events");
    }
}
```

### 5. Run

```bash
# Terminal 1 — backend
./gradlew bootRun --args='--spring.profiles.active=dev'

# Terminal 2 — frontend
cd frontend && npm install && npm run dev
```

Open http://localhost:5173

## Manual Setup (Javalin)

### 1. Add the dependency

```kotlin
// build.gradle.kts
dependencies {
    implementation("io.github.emmajiugo:inertiajs-javalin:0.1.0-SNAPSHOT")
    implementation("io.javalin:javalin:7.1.0")
}
```

### 2. Set up the engine and routes

```java
InertiaConfig config = InertiaConfig.builder()
    .version("1.0.0")
    .templateResolver(new ClasspathTemplateResolver("templates/app.html"))
    .build();

InertiaEngine engine = new InertiaEngine(config);
InertiaPlugin plugin = new InertiaPlugin(engine);
Inertia inertia = plugin.inertia();

Javalin app = Javalin.create(cfg -> {
    plugin.configure(cfg);

    cfg.routes.get("/events", ctx ->
        inertia.render(ctx, "Events/Index", Map.of("events", eventService.findAll())));

    cfg.routes.post("/events", ctx -> {
        Map<String, String> errors = validate(ctx.bodyAsClass(Map.class));
        if (!errors.isEmpty()) {
            inertia.redirectWithErrors(ctx, "/events/create", errors);
            return;
        }
        eventService.create(ctx.bodyAsClass(Map.class));
        inertia.redirect(ctx, "/events");
    });
});

app.start(8080);
```

## Features

### Core Protocol
- Full Inertia v2 protocol (JSON/HTML responses, asset versioning, 302-to-303 redirect upgrade)
- Partial reloads with prop filtering (`X-Inertia-Partial-Data` / `X-Inertia-Partial-Except`)
- External redirects (`inertia.location()`)
- Spring Boot auto-configuration (zero config)
- Pluggable JSON serialization

### Prop Types

```java
import static io.github.emmajiugo.inertia.core.props.InertiaProps.*;

inertia.render(req, res, "Dashboard", Map.of(
    "stats",       lazy(() -> statsService.compute()),       // evaluated lazily
    "permissions", optional(() -> permService.getAll()),     // only when explicitly requested
    "flash",       always(flashMessage),                     // never filtered out
    "comments",    defer(() -> commentService.findAll()),    // loaded after initial render
    "sidebar",     defer(() -> sidebarData(), "sidebar"),    // deferred, grouped
    "posts",       merge(() -> postService.getPage(page)),   // infinite scroll (append)
    "notifs",      prepend(() -> notifService.latest()),     // prepend to existing
    "settings",    deepMerge(() -> settingsService.all()),   // deep merge nested objects
    "plans",       once(() -> planService.findAll())          // resolved once, cached client-side
));
```

| Type | Behavior |
|------|----------|
| `lazy()` | Evaluated lazily via `Supplier`. Included by default, filtered in partial reloads. |
| `always()` | Always included, never filtered — even in partial reloads. |
| `optional()` | Excluded by default. Only included when explicitly requested via `only`. |
| `defer()` | Excluded from initial render, loaded in a follow-up request. Supports grouping. |
| `merge()` / `prepend()` / `deepMerge()` | Client-side merging for infinite scroll and nested updates. Chain `.matchOn("id")` to deduplicate. |
| `once()` | Resolved once, cached client-side. Chain `.as("key")` for cross-page sharing, `.until(duration)` for expiration. |

### Shared Props

```java
@Component
public class AuthSharedProps implements SharedPropsResolver {
    @Override
    public Map<String, Object> resolve(InertiaRequest request) {
        return Map.of("auth", Map.of("user", Map.of("name", "John")));
    }
}
```

All `SharedPropsResolver` beans are auto-discovered and merged into every response.

### Validation Errors

```java
// Spring
inertia.redirectWithErrors(req, res, "/events/create",
    Map.of("title", "Title is required"));

// Javalin
inertia.redirectWithErrors(ctx, "/events/create",
    Map.of("title", "Title is required"));
```

Errors are stored in the session, consumed on the next request, and available as `form.errors` in your frontend.

### Flash Data

```java
// Spring
inertia.flash(req, "success", "Event created successfully!");
inertia.flash(req, Map.of("success", "Created!", "newId", 42));
inertia.redirect(res, "/events");

// Javalin
inertia.flash(ctx, "success", "Event created successfully!");
inertia.redirect(ctx, "/events");
```

Flash data is available as `page.props.flash` on the next request, then automatically cleared. Not persisted in history state.

```vue
<!-- Vue -->
<div v-if="$page.props.flash?.success">{{ $page.props.flash.success }}</div>
```

### Precognition (Real-Time Validation)

```java
// Spring
if (inertia.isPrecognitionRequest(req)) {
    Map<String, String> errors = validate(body);
    inertia.precognitionRespond(res, errors); // 204 or 422
    return;
}

// Javalin
if (inertia.isPrecognitionRequest(ctx)) {
    inertia.precognitionRespond(ctx, errors);
    return;
}
```

### History Encryption

```java
inertia.render(req, res, "Account/Settings", props,
    RenderOptions.builder().encryptHistory(true).build());
```

### Server-Side Rendering (SSR)

SSR renders your Vue/React components to HTML on the server for better SEO and faster initial page loads. Requires a separate Node.js SSR server (the [official Inertia SSR protocol](https://inertiajs.com/server-side-rendering)).

**Spring Boot** — add to `application.properties`:

```properties
inertia.ssr.url=http://127.0.0.1:13714
# inertia.ssr.timeout=1500          # default 1500ms
# inertia.ssr.fail-on-error=false   # default: graceful fallback to CSR
```

**Javalin** — configure via `InertiaConfig`:

```java
InertiaConfig config = InertiaConfig.builder()
    .templateResolver(new ClasspathTemplateResolver("templates/app.html"))
    .ssrClient(new HttpSsrClient("http://127.0.0.1:13714", Duration.ofMillis(1500)))
    .ssrEnabled(true)
    .build();
```

**Per-render control** — disable SSR for specific pages (e.g., admin dashboards):

```java
inertia.render(req, res, "Admin/Dashboard", props,
    RenderOptions.builder().ssr(false).build());
```

**Template setup** — add `@inertiaHead` to your templates for SSR head injection:

```html
<head>
    <meta charset="UTF-8" />
    @inertiaHead
</head>
<body>
    @inertia
    <script type="module" src="/assets/app.js"></script>
</body>
```

When SSR is not configured or falls back, `@inertiaHead` is simply stripped. You can also provide a custom `SsrClient` bean (Spring) to use a different HTTP client.

### Vite Asset Versioning

Asset versioning is **automatic** for Spring Boot — the adapter reads Vite's `manifest.json` and uses its hash as the version. When you rebuild frontend assets, the version changes automatically, triggering a full reload for clients on stale assets.

No configuration needed. If you want to override:

```properties
# application.properties
inertia.version=custom-version        # explicit version (disables auto-detection)
inertia.manifest-path=static/.vite/manifest.json  # custom manifest location
```

For Javalin, use `ViteManifestVersionResolver` directly:

```java
InertiaConfig.builder()
    .versionSupplier(ViteManifestVersionResolver.lazy("static/.vite/manifest.json"))
```

## Running the Examples

### Spring Boot example

```bash
# Single command (starts both Vite + Spring Boot)
./gradlew :examples:example-spring:dev

# Or two terminals
./gradlew :examples:example-spring:bootRun --args='--spring.profiles.active=dev'
cd examples/example-spring/frontend && npm install && npm run dev
```

Open http://localhost:5173

### Spring Boot SSR example

```bash
# Single command (starts Vite + SSR server + Spring Boot)
./gradlew :examples:example-spring-ssr:dev

# Or three terminals
cd examples/example-spring-ssr/frontend && npm install && node ssr-server.js
cd examples/example-spring-ssr/frontend && npm run dev
./gradlew :examples:example-spring-ssr:bootRun --args='--spring.profiles.active=dev'
```

Open http://localhost:5173 — view page source to confirm SSR (you'll see pre-rendered HTML instead of an empty `<div id="app">`).

### Javalin example

```bash
# Terminal 1
DEV=true ./gradlew :examples:example-javalin:run

# Terminal 2
cd examples/example-javalin/frontend && npm install && npm run dev
```

Open http://localhost:5174

### Production build

```bash
./gradlew :examples:example-spring:buildProd
java -jar examples/example-spring/build/libs/example-spring-0.1.0-SNAPSHOT.jar
```

### Docker

```bash
docker compose up --build
# or
docker build -f examples/example-spring/Dockerfile -t inertia-example .
docker run -p 8080:8080 inertia-example
```

Open http://localhost:8080

## Architecture

```
inertiajs-core          (zero framework deps, just Jackson)
    |           |
inertiajs-spring    inertiajs-javalin
```

All protocol logic lives in `inertiajs-core`. Framework adapters are thin wrappers that implement `InertiaRequest` and `InertiaResponse` interfaces.

## License

MIT
