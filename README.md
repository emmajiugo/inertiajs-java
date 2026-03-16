# Inertia.js Java Adapter

A server-side adapter for [Inertia.js](https://inertiajs.com) v2, enabling you to build modern single-page apps using Vue, React, or Svelte with a Java backend — no API required.

## Quick Start (Spring Boot)

### 1. Add the dependency

```kotlin
// build.gradle.kts
dependencies {
    implementation("io.inertia:inertiajs-spring:0.1.0-SNAPSHOT")
}
```

### 2. Create root templates

**Production template** — `src/main/resources/templates/app.html`

```html
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>My App</title>
</head>
<body>
    @inertia
    <script type="module" src="/assets/app.js"></script>
</body>
</html>
```

**Dev template** — `src/main/resources/templates/app-dev.html`

```html
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>My App</title>
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
inertia.version=1.0.0
inertia.template-path=templates/app.html
```

```properties
# application-dev.properties (overrides template for dev mode)
inertia.template-path=templates/app-dev.html
```

### 4. Write controllers

```java
@Controller
public class EventController {

    @Autowired private Inertia inertia;
    @Autowired private EventService eventService;

    @GetMapping("/events")
    public void index(HttpServletRequest req, HttpServletResponse res) throws IOException {
        inertia.render(req, res, "Events/Index", Map.of(
            "events", eventService.findAll()
        ));
    }

    @PostMapping("/events")
    public void store(@RequestBody Map<String, String> body,
                      HttpServletRequest req, HttpServletResponse res) {
        String title = body.get("title");
        if (title == null || title.isBlank()) {
            inertia.redirectWithErrors(req, res, "/events/create",
                Map.of("title", "Title is required"));
            return;
        }
        eventService.create(title);
        inertia.redirect(res, "/events");
    }
}
```

### 5. Set up the frontend

```ts
// app.ts
import { createApp, h } from 'vue'
import { createInertiaApp } from '@inertiajs/vue3'

createInertiaApp({
  resolve: (name) => {
    const pages = import.meta.glob('./pages/**/*.vue', { eager: true })
    return pages[`./pages/${name}.vue`]
  },
  setup({ el, App, props, plugin }) {
    createApp({ render: () => h(App, props) })
      .use(plugin)
      .mount(el)
  },
})
```

## Features

- Full Inertia v2 protocol support (JSON/HTML responses, asset versioning, redirect upgrade)
- Partial reloads with prop filtering (`only` / `except`)
- Prop types: `LazyProp`, `AlwaysProp`, `OptionalProp`
- Shared props (auth, flash data)
- Validation errors via session flash
- External redirects (`inertia.location()`)
- Spring Boot auto-configuration (zero config)
- Pluggable JSON serialization

## Shared Props

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

## Prop Types

```java
import static io.inertia.core.props.InertiaProps.*;

inertia.render(req, res, "Dashboard", Map.of(
    "stats",       lazy(() -> statsService.compute()),     // evaluated lazily
    "permissions", optional(() -> permService.getAll()),   // only when requested
    "flash",       always(flashMessage)                    // never filtered
));
```

## Running the Example

### Dev mode (single command)

```bash
./gradlew :examples:example-spring:dev
```

This starts both Springboot (with `dev` profile) and Vite dev server. Open http://localhost:5173.

### Dev mode (two terminals)

```bash
# Terminal 1 — backend
./gradlew :examples:example-spring:bootRun --args='--spring.profiles.active=dev'

# Terminal 2 — frontend
cd examples/example-spring/frontend
npm install
npm run dev
```

Open http://localhost:5173

### Production build

```bash
./gradlew :examples:example-spring:buildProd
java -jar examples/example-spring/build/libs/example-spring-0.1.0-SNAPSHOT.jar
```

Open http://localhost:8080

### Docker

```bash
# Build from project root
docker build -f examples/example-spring/Dockerfile -t inertia-example .

# Run
docker run -p 8080:8080 inertia-example
```

Open http://localhost:8080

## License

MIT
