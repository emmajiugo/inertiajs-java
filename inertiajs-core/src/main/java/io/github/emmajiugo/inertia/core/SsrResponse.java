package io.github.emmajiugo.inertia.core;

import java.util.List;

/**
 * Response from an SSR server.
 *
 * @param head list of HTML tags to inject into &lt;head&gt; (e.g., title, meta)
 * @param body pre-rendered component HTML (includes data-page attribute)
 */
public record SsrResponse(List<String> head, String body) {}
