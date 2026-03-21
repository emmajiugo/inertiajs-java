# SSR (Server-Side Rendering) Design Spec

## Overview

Add server-side rendering support to the Inertia.js Java adapter. SSR renders Vue/React components to HTML on the server before sending them to the browser, improving SEO and initial load performance.

The design follows the official Inertia.js SSR protocol: a separate Node.js process runs an HTTP server that receives the page object and returns rendered HTML. The Java adapter communicates with this process over HTTP.

## Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Frontend framework | Framework-agnostic | Matches port+adapter pattern; SSR server is external |
| Communication | HTTP to persistent SSR server (official pattern) | Proven, consistent with Laravel/Rails adapters |
| Failure handling | Configurable: graceful fallback (default) or fail-hard | Resilient by default, loud when operators want it |
| Scope control | Global default + per-render override | Simple, explicit, fits existing `RenderOptions` |
| Timeout | Configurable with 1500ms default | Rendering times vary by app complexity |
| HTTP client | Core defines interface, ships default JDK implementation | Follows existing port+adapter pattern |

## Architecture

### Core Interfaces & Types

**`SsrClient`** — Interface for communicating with the SSR server:

```java
package io.github.emmajiugo.inertia.core;

import java.io.IOException;

public interface SsrClient {
    SsrResponse render(String pageJson) throws IOException;
}
```

**`SsrResponse`** — What the SSR server returns:

```java
package io.github.emmajiugo.inertia.core;

import java.util.List;

public record SsrResponse(List<String> head, String body) {}
```

- `head` — List of rendered HTML tags (`<title>`, `<meta>`, etc.). Kept as `List<String>` for flexibility; joined at point of use by `SsrGateway`.
- `body` — Rendered component HTML (replaces the `@inertia` placeholder). Per the official Inertia SSR protocol, the SSR server is responsible for including the `data-page` attribute on the root div for client-side hydration.

**`HttpSsrClient`** — Default implementation using `java.net.http.HttpClient`:

```java
package io.github.emmajiugo.inertia.core;

public final class HttpSsrClient implements SsrClient {
    private final HttpClient httpClient;
    private final URI endpoint;
    private final Duration timeout;
    private final ObjectMapper objectMapper;

    public HttpSsrClient(String url, Duration timeout) { ... }
    public HttpSsrClient(String url, Duration timeout, ObjectMapper objectMapper) { ... }

    @Override
    public SsrResponse render(String pageJson) throws IOException { ... }
}
```

- POSTs the page JSON to `{url}/render`
- Parses the response JSON: `{ "head": ["<title>...</title>", ...], "body": "<div>...</div>" }`
- Returns `SsrResponse` with `head` as `List<String>` and `body` as-is
- Uses `java.net.http.HttpClient` — thread-safe, created once in the constructor, reused across all requests. The JDK default connection pooling is sufficient for SSR traffic.
- Uses Jackson `ObjectMapper` directly for parsing the SSR response. This is acceptable because `inertiajs-core` already depends on `jackson-databind` (via `JacksonJsonSerializer`), so no new dependency is introduced. The `JsonSerializer` interface is not used here because it only has a `serialize()` method and adding deserialization would widen the interface for a single use case. The no-arg constructor creates a default `ObjectMapper`; the overloaded constructor accepts a custom one (e.g., the Spring-managed instance).
- Throws `IOException` on non-200 response, connection failure, or timeout

### TemplateResolver Changes

The existing `TemplateResolver` interface gains a default method for raw template access:

```java
@FunctionalInterface
public interface TemplateResolver {
    String resolve(String pageJson);

    /**
     * Returns the raw template string before any placeholder substitution.
     * Used by SSR to perform its own placeholder replacements.
     *
     * Default implementation throws UnsupportedOperationException.
     * ClasspathTemplateResolver overrides this.
     */
    default String getRawTemplate() {
        throw new UnsupportedOperationException(
            "This TemplateResolver does not support raw template access");
    }
}
```

This preserves backwards compatibility — existing custom `TemplateResolver` implementations continue to work (they only need `resolve()`). The `getRawTemplate()` method is only called on the SSR path, so custom resolvers that don't implement it simply can't be used with SSR (which is a reasonable constraint documented via the exception).

**`ClasspathTemplateResolver`** overrides `getRawTemplate()` to return the cached (or freshly loaded) template. It also strips the `@inertiaHead` placeholder in `resolve()`:

```java
@Override
public String resolve(String pageJson) {
    String template = cachedTemplate != null ? cachedTemplate : loadTemplate(classpathLocation);
    String div = "<div id=\"app\" data-page=\"" + escapeHtml(pageJson) + "\"></div>";
    return template
            .replace(HEAD_PLACEHOLDER, "")   // strip @inertiaHead for CSR
            .replace(PLACEHOLDER, div);
}

@Override
public String getRawTemplate() {
    return cachedTemplate != null ? cachedTemplate : loadTemplate(classpathLocation);
}
```

### SsrGateway

The gateway sits between `InertiaEngine` and the template/SSR rendering decision:

```java
package io.github.emmajiugo.inertia.core;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class SsrGateway {
    private static final Logger logger = Logger.getLogger(SsrGateway.class.getName());

    private final TemplateResolver templateResolver;
    private final SsrClient ssrClient;       // null if SSR not configured
    private final boolean failOnError;

    public SsrGateway(TemplateResolver templateResolver,
                      SsrClient ssrClient,
                      boolean failOnError) { ... }

    public String resolve(String pageJson, boolean ssrEnabled) throws IOException {
        if (ssrClient != null && ssrEnabled) {
            try {
                SsrResponse ssr = ssrClient.render(pageJson);
                return buildSsrHtml(ssr);
            } catch (IOException e) {
                if (failOnError) throw e;
                logger.log(Level.WARNING, "SSR failed, falling back to CSR", e);
            }
        }
        return templateResolver.resolve(pageJson);
    }
}
```

**Delegation behavior:** When no `ssrClient` is configured (null), the gateway always delegates directly to the wrapped `TemplateResolver`, regardless of the `ssrEnabled` flag. This means configuring SSR has zero impact on existing apps that don't set up an SSR client — the gateway acts as a transparent pass-through to the template resolver, identical to the pre-SSR behavior.

**SSR HTML construction:** The `buildSsrHtml` method calls `templateResolver.getRawTemplate()` to get the raw template, then:
1. Replaces `@inertiaHead` with the SSR head content (head list joined with `\n`)
2. Replaces `@inertia` with the SSR body (pre-rendered component HTML from the SSR server, which includes the `data-page` attribute)

**Fallback logging:** When SSR fails and `failOnError=false`, the exception is logged at `WARNING` level using `java.util.logging` (no external dependency). This ensures operators are aware of SSR failures in production without requiring SLF4J.

**Fallback behavior:**
- SSR enabled + success → returns SSR HTML with head and body injected
- SSR enabled + failure + `failOnError=false` → logs warning, falls back to CSR template
- SSR enabled + failure + `failOnError=true` → propagates IOException
- SSR disabled (per-render or no client) → delegates to template resolver

**Thread safety:** `SsrGateway` is stateless and safe for concurrent use. It depends on `SsrClient` also being thread-safe — `HttpSsrClient` satisfies this because `java.net.http.HttpClient` is thread-safe.

### InertiaConfig Changes

New fields:

```java
private final SsrClient ssrClient;        // null = SSR not available
private final boolean ssrEnabled;          // global default, true if ssrClient is set
private final boolean ssrFailOnError;      // default false

// Accessors (following existing getX()/isX() convention)
public SsrClient getSsrClient() { ... }
public boolean isSsrEnabled() { ... }
public boolean isSsrFailOnError() { ... }

// Builder additions
public Builder ssrClient(SsrClient client) { ... }
public Builder ssrEnabled(boolean enabled) { ... }
public Builder ssrFailOnError(boolean fail) { ... }
```

If `ssrClient` is null, SSR is completely unavailable regardless of other flags. If `ssrClient` is provided but `ssrEnabled` is false, SSR is available but off by default — individual renders can still opt in via `RenderOptions`.

### RenderOptions Changes

Add an `ssr` field to the existing class with builder pattern (preserving the current API):

```java
public final class RenderOptions {

    private static final RenderOptions EMPTY = new RenderOptions(null, null, null);

    private final Boolean encryptHistory;
    private final Boolean clearHistory;
    private final Boolean ssr;              // null = use global default, true/false = override

    private RenderOptions(Boolean encryptHistory, Boolean clearHistory, Boolean ssr) {
        this.encryptHistory = encryptHistory;
        this.clearHistory = clearHistory;
        this.ssr = ssr;
    }

    public Boolean getEncryptHistory() { return encryptHistory; }
    public Boolean getClearHistory() { return clearHistory; }
    public Boolean getSsr() { return ssr; }

    public static RenderOptions empty() { return EMPTY; }
    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private Boolean encryptHistory;
        private Boolean clearHistory;
        private Boolean ssr;

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

        public RenderOptions build() {
            return new RenderOptions(encryptHistory, clearHistory, ssr);
        }
    }
}
```

### InertiaEngine Changes

Minimal. The engine constructs an `SsrGateway` internally from config and uses it in the HTML rendering path:

```java
// In constructor:
this.ssrGateway = new SsrGateway(
    config.getTemplateResolver(),
    config.getSsrClient(),       // may be null
    config.isSsrFailOnError()
);

// In render(), HTML path:
boolean useSsr = resolveSsr(options);  // per-render override vs global default
res.writeBody(ssrGateway.resolve(pageJson, useSsr));
```

The `resolveSsr` method: if `options.getSsr()` is non-null, use it; otherwise fall back to `config.isSsrEnabled()`.

### Template Changes

SSR templates need a `@inertiaHead` placeholder in `<head>`:

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

- When SSR succeeds: `@inertiaHead` is replaced with SSR head tags, `@inertia` with SSR body
- When SSR is disabled or falls back to CSR: `ClasspathTemplateResolver.resolve()` strips `@inertiaHead` (replaces with empty string) and replaces `@inertia` with the data-page div as usual
- Existing templates without `@inertiaHead` continue to work — the placeholder is optional. SSR head content is simply not injected if the placeholder is absent.

## SSR Protocol

Matches the official Inertia.js SSR protocol.

**Request:**

```
POST http://127.0.0.1:13714/render
Content-Type: application/json

{ "component": "Dashboard", "props": { ... }, "url": "/dashboard", "version": "abc123" }
```

**Response:**

```json
{
  "head": [
    "<title>Dashboard</title>",
    "<meta name=\"description\" content=\"...\">"
  ],
  "body": "<div id=\"app\" data-page=\"{...}\"><div class=\"dashboard\">...</div></div>"
}
```

The `body` includes the `data-page` attribute on the root `<div>` — this is the SSR server's responsibility (consistent with the official Inertia.js protocol). The Java adapter does not inject `data-page` itself on the SSR path.

## Adapter Configuration

### Spring Boot

New properties under `inertia.ssr.*`:

```properties
inertia.ssr.enabled=true                    # global default (requires ssr.url to take effect)
inertia.ssr.url=http://127.0.0.1:13714     # SSR server endpoint
inertia.ssr.timeout=1500                    # milliseconds
inertia.ssr.fail-on-error=false             # graceful fallback by default
```

**InertiaProperties** nested class:

```java
public static class Ssr {
    private boolean enabled = true;
    private String url;                      // null by default — SSR only activates when explicitly set
    private int timeout = 1500;
    private boolean failOnError = false;
}
```

**InertiaAutoConfiguration:**
- If `inertia.ssr.url` is set and `inertia.ssr.enabled=true`, creates an `HttpSsrClient` bean
- Uses `ObjectProvider<SsrClient>` so users can provide their own bean (e.g., Spring's `RestClient`-based implementation)
- If no `SsrClient` is available, SSR is not configured — gateway passes through to template resolver

### Javalin

SSR configuration flows through `InertiaConfig`, which the user constructs and passes to `InertiaEngine` → `InertiaPlugin`. The existing pattern is preserved — `InertiaPlugin` remains a thin wrapper that takes an `InertiaEngine`:

```java
// User configures SSR via InertiaConfig.Builder
InertiaConfig config = InertiaConfig.builder()
    .templateResolver(new ClasspathTemplateResolver("templates/app.html"))
    .ssrClient(new HttpSsrClient("http://127.0.0.1:13714", Duration.ofMillis(1500)))
    .ssrEnabled(true)
    .build();

InertiaEngine engine = new InertiaEngine(config);
InertiaPlugin plugin = new InertiaPlugin(engine);
```

Alternatively, SSR can be enabled via environment variable `SSR_URL` — if set, the example app creates an `HttpSsrClient` from it (consistent with the existing `DEV=true` pattern). This is application-level logic, not baked into the plugin.

## Testing Strategy

### Unit Tests (inertiajs-core)

**`SsrGatewayTest`:**
- SSR enabled + successful response → returns SSR HTML with head and body injected
- SSR enabled + IOException + failOnError=false → falls back to CSR template, logs warning
- SSR enabled + IOException + failOnError=true → propagates exception
- SSR disabled → delegates to template resolver, never calls SsrClient
- No SsrClient configured (null) → always delegates to template resolver
- `@inertiaHead` placeholder removed when falling back to CSR
- Template without `@inertiaHead` → SSR body still injected at `@inertia`, no error

**`HttpSsrClientTest`** (uses `com.sun.net.httpserver.HttpServer`):
- Successful render → parses head list and body correctly
- Non-200 response → throws IOException
- Connection refused → throws IOException
- Timeout exceeded → throws IOException

**`InertiaEngineTest` additions:**
- Render with SSR configured and enabled → HTML contains SSR body
- Render with SSR configured but disabled via RenderOptions.ssr(false) → CSR output
- Render with no SSR configured → CSR output (unchanged behavior)
- Inertia request (JSON) → SSR is never called regardless of config

**`ClasspathTemplateResolverTest` additions:**
- Template with `@inertiaHead` → stripped in CSR resolve()
- `getRawTemplate()` returns template with placeholders intact

### Integration Tests (adapters)

**Spring:**
- AutoConfiguration wires SsrClient from properties correctly
- @ConditionalOnMissingBean allows custom SsrClient bean override

**Javalin:**
- Engine with SSR config produces SSR output when SsrClient is provided

### Not in Scope

End-to-end tests with a real Node SSR server — that's the user's responsibility in their app.

## Files Changed

### New Files (inertiajs-core)
- `io.github.emmajiugo.inertia.core.SsrClient` — Interface
- `io.github.emmajiugo.inertia.core.SsrResponse` — Record
- `io.github.emmajiugo.inertia.core.HttpSsrClient` — Default HTTP implementation
- `io.github.emmajiugo.inertia.core.SsrGateway` — Decorator/gateway
- Test files for each above

### Modified Files (inertiajs-core)
- `TemplateResolver` — Add `getRawTemplate()` default method (backwards-compatible)
- `ClasspathTemplateResolver` — Add `HEAD_PLACEHOLDER = "@inertiaHead"` constant, override `getRawTemplate()`, strip `@inertiaHead` in `resolve()`
- `InertiaConfig` — Add ssrClient, ssrEnabled, ssrFailOnError fields + builder methods
- `RenderOptions` — Add `ssr` field to existing class + builder
- `InertiaEngine` — Construct SsrGateway, use in HTML render path

### Modified Files (inertiajs-spring)
- `InertiaProperties` — Add nested Ssr class
- `InertiaAutoConfiguration` — Wire SsrClient bean conditionally

### Modified Files (inertiajs-javalin)
- No changes to `InertiaPlugin` itself — SSR flows through `InertiaConfig`/`InertiaEngine`

### Template Files
- Add `@inertiaHead` placeholder to example templates (optional, backwards-compatible)
