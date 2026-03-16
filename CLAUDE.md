# Inertia.js Java Adapter

## Architecture

Port+adapter pattern. All protocol logic lives in `inertiajs-core` (zero framework dependencies, just Jackson). Framework adapters (`inertiajs-spring`, `inertiajs-javalin`) are thin wrappers that implement `InertiaRequest` and `InertiaResponse` interfaces.

## Module Structure

- `inertiajs-core` — Protocol engine, page object, prop types, template resolution
- `inertiajs-spring` — Spring Boot adapter with auto-configuration
- `inertiajs-javalin` — Javalin adapter with plugin + middleware
- `examples/example-spring` — Vue 3 + Spring Boot demo app
- `examples/example-javalin` — Vue 3 + Javalin demo app

## Key Patterns

- `InertiaEngine` is the central class. It handles render (JSON/HTML), version mismatch, redirect upgrade, shared props merging, and partial reload filtering.
- Props can be wrapped in `LazyProp`, `AlwaysProp`, or `OptionalProp` to control evaluation and partial reload filtering behavior.
- Spring adapter uses `@AutoConfiguration` — adding `inertiajs-spring` to classpath auto-registers all beans.
- Javalin adapter uses `InertiaPlugin` — register middleware via `plugin.registerMiddleware(app)`.
- Validation errors flow via session: `inertia.redirectWithErrors(req/ctx, res, url, errorMap)`.
- Two templates: `app.html` (production, references built assets) and `app-dev.html` (dev, references Vite dev server). Switched via Spring profile `dev` or `DEV=true` env var (Javalin).

## Build & Test

```bash
./gradlew build          # compile + test all modules
./gradlew :inertiajs-core:test   # test core only
./gradlew :inertiajs-spring:test # test spring adapter only
```

## Running the Example Apps

### Spring Boot
```bash
./gradlew :examples:example-spring:dev
```
Open http://localhost:5173

### Javalin
```bash
# Terminal 1
DEV=true ./gradlew :examples:example-javalin:run

# Terminal 2
cd examples/example-javalin/frontend && npm install && npm run dev
```
Open http://localhost:5174

## Production / Docker

```bash
# Production build
./gradlew :examples:example-spring:buildProd
java -jar examples/example-spring/build/libs/example-spring-0.1.0-SNAPSHOT.jar

# Docker
docker build -f examples/example-spring/Dockerfile -t inertia-example .
docker run -p 8080:8080 inertia-example
```

## Future Features

See [FEATURES.md](FEATURES.md) for non-MVP features planned for incremental addition.
