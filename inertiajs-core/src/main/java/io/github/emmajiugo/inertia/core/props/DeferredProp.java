package io.github.emmajiugo.inertia.core.props;

import java.util.function.Supplier;

/**
 * A prop whose value is deferred — excluded from the initial page response
 * and loaded via a follow-up partial reload request after the page renders.
 * Props can be grouped so that props in the same group are fetched together.
 * The default group is "default".
 */
public record DeferredProp<T>(Supplier<T> supplier, String group) implements Prop<T> {

    public DeferredProp(Supplier<T> supplier) {
        this(supplier, "default");
    }

    @Override
    public T resolve() {
        return supplier.get();
    }
}
