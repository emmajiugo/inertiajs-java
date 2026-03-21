package io.github.emmajiugo.inertia.core.props;

/**
 * Common interface for any prop wrapper whose value must be resolved
 * (i.e. evaluated from a supplier) before serialization.
 */
public interface Resolvable<T> {

    T resolve();
}
