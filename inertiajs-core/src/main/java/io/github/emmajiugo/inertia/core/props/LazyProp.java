package io.github.emmajiugo.inertia.core.props;

import java.util.function.Supplier;

/**
 * A prop whose value is computed lazily. Included by default in full renders.
 * During partial reloads, included unless explicitly excluded via
 * X-Inertia-Partial-Except.
 */
public record LazyProp<T>(Supplier<T> supplier) implements Prop<T> {

    @Override
    public T resolve() {
        return supplier.get();
    }
}
