package io.inertia.core;

import java.util.Map;

@FunctionalInterface
public interface SharedPropsResolver {

    Map<String, Object> resolve(InertiaRequest request);
}
