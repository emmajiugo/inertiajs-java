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

    public static <T> DeferredProp<T> defer(Supplier<T> supplier) {
        return new DeferredProp<>(supplier);
    }

    public static <T> DeferredProp<T> defer(Supplier<T> supplier, String group) {
        return new DeferredProp<>(supplier, group);
    }

    public static <T> MergeProp<T> merge(Supplier<T> supplier) {
        return MergeProp.append(supplier);
    }

    public static <T> MergeProp<T> prepend(Supplier<T> supplier) {
        return MergeProp.prepend(supplier);
    }

    public static <T> MergeProp<T> deepMerge(Supplier<T> supplier) {
        return MergeProp.deep(supplier);
    }

    public static <T> OnceProp<T> once(Supplier<T> supplier) {
        return new OnceProp<>(supplier);
    }
}
