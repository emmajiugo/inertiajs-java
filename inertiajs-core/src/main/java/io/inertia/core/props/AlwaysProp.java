package io.inertia.core.props;

import java.util.function.Supplier;

/**
 * A prop that is always included in every response, even during partial reloads.
 * Never filtered out.
 */
public record AlwaysProp<T>(Supplier<T> supplier) implements Prop<T> {

    @Override
    public T resolve() {
        return supplier.get();
    }
}
