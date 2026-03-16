package io.inertia.core;

@FunctionalInterface
public interface TemplateResolver {

    String resolve(String pageJson);
}
