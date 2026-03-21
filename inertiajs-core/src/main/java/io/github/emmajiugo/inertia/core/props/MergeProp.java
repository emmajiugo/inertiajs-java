package io.github.emmajiugo.inertia.core.props;

import java.util.function.Supplier;

/**
 * A prop that tells the client to merge (append) its value with existing data
 * instead of replacing it. Used for infinite scroll, pagination, etc.
 *
 * Use {@link MergeProp#prepend(Supplier)} to prepend instead of append.
 * Use {@link MergeProp#deep(Supplier)} for deep merge behavior.
 *
 * Optionally specify a {@code matchOn} key to deduplicate items by field.
 */
public final class MergeProp<T> implements Resolvable<T> {

    public enum Strategy { APPEND, PREPEND, DEEP }

    private final Supplier<T> supplier;
    private final Strategy strategy;
    private final String matchOn;

    private MergeProp(Supplier<T> supplier, Strategy strategy, String matchOn) {
        this.supplier = supplier;
        this.strategy = strategy;
        this.matchOn = matchOn;
    }

    public static <T> MergeProp<T> append(Supplier<T> supplier) {
        return new MergeProp<>(supplier, Strategy.APPEND, null);
    }

    public static <T> MergeProp<T> prepend(Supplier<T> supplier) {
        return new MergeProp<>(supplier, Strategy.PREPEND, null);
    }

    public static <T> MergeProp<T> deep(Supplier<T> supplier) {
        return new MergeProp<>(supplier, Strategy.DEEP, null);
    }

    public MergeProp<T> matchOn(String field) {
        return new MergeProp<>(this.supplier, this.strategy, field);
    }

    public T resolve() { return supplier.get(); }
    public Strategy getStrategy() { return strategy; }
    public String getMatchOn() { return matchOn; }
}
