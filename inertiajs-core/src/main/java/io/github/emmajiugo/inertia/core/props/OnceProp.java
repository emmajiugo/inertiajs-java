package io.github.emmajiugo.inertia.core.props;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

/**
 * A prop that is resolved once and cached client-side across navigations.
 * Subsequent requests include the key in X-Inertia-Except-Once-Props,
 * and the server skips resolving it.
 *
 * Optionally specify an expiration time or a custom key for cross-page sharing.
 */
public final class OnceProp<T> implements Resolvable<T> {

    private final Supplier<T> supplier;
    private final String key;
    private final Long expiresAtMs;

    private OnceProp(Supplier<T> supplier, String key, Long expiresAtMs) {
        this.supplier = supplier;
        this.key = key;
        this.expiresAtMs = expiresAtMs;
    }

    public OnceProp(Supplier<T> supplier) {
        this(supplier, null, null);
    }

    public OnceProp<T> as(String key) {
        return new OnceProp<>(this.supplier, key, this.expiresAtMs);
    }

    public OnceProp<T> until(Instant expiresAt) {
        return new OnceProp<>(this.supplier, this.key, expiresAt.toEpochMilli());
    }

    public OnceProp<T> until(Duration duration) {
        return until(Instant.now().plus(duration));
    }

    public T resolve() { return supplier.get(); }
    public String getKey() { return key; }
    public Long getExpiresAtMs() { return expiresAtMs; }
}
