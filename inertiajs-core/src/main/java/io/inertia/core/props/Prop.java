package io.inertia.core.props;

/**
 * Marker interface for typed props that control evaluation and
 * filtering behavior during partial reloads.
 */
public sealed interface Prop<T> permits LazyProp, AlwaysProp, OptionalProp {

    T resolve();
}
