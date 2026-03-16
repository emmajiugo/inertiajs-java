package io.inertia.core.props;

import java.util.function.Supplier;

/**
 * Static factory methods for creating typed props.
 */
public final class InertiaProps {

    private InertiaProps() {}

    public static <T> LazyProp<T> lazy(Supplier<T> supplier) {
        return new LazyProp<>(supplier);
    }

    public static <T> AlwaysProp<T> always(T value) {
        return new AlwaysProp<>(() -> value);
    }

    public static <T> AlwaysProp<T> always(Supplier<T> supplier) {
        return new AlwaysProp<>(supplier);
    }

    public static <T> OptionalProp<T> optional(Supplier<T> supplier) {
        return new OptionalProp<>(supplier);
    }
}
