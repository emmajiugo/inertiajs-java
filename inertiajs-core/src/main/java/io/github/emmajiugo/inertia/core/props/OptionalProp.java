package io.github.emmajiugo.inertia.core.props;

import java.util.function.Supplier;

/**
 * A prop that is excluded by default. Only included when explicitly
 * requested via X-Inertia-Partial-Data (the "only" list).
 */
public record OptionalProp<T>(Supplier<T> supplier) implements Prop<T> {

    @Override
    public T resolve() {
        return supplier.get();
    }
}
