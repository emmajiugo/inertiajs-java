package io.github.emmajiugo.inertia.core.props;

/**
 * Marker interface for typed props that control evaluation and
 * filtering behavior during partial reloads.
 */
public sealed interface Prop<T> extends Resolvable<T> permits AlwaysProp, OptionalProp, DeferredProp {
}
