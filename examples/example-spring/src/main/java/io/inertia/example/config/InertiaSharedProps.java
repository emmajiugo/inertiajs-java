package io.inertia.example.config;

import io.inertia.core.InertiaRequest;
import io.inertia.core.SharedPropsResolver;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class InertiaSharedProps implements SharedPropsResolver {

    @Override
    public Map<String, Object> resolve(InertiaRequest request) {
        return Map.of(
                "auth", Map.of(
                        "user", Map.of(
                                "name", "Demo User"
                        )
                )
        );
    }
}
