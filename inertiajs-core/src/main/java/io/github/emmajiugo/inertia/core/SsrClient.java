package io.github.emmajiugo.inertia.core;

import java.io.IOException;

/**
 * Sends the Inertia page object to an SSR server and returns
 * the pre-rendered HTML response.
 */
public interface SsrClient {

    /**
     * Render the given page JSON via the SSR server.
     *
     * @param pageJson the serialized Inertia page object
     * @return the SSR response containing head tags and body HTML
     * @throws IOException if the SSR server is unreachable, returns a non-200 status, or times out
     */
    SsrResponse render(String pageJson) throws IOException;
}
