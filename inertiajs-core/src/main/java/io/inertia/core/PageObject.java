package io.inertia.core;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public final class PageObject {

    private final String component;
    private final Map<String, Object> props;
    private final String url;
    private final String version;

    public PageObject(String component, Map<String, Object> props, String url, String version) {
        this.component = component;
        this.props = props;
        this.url = url;
        this.version = version;
    }

    public String getComponent() { return component; }
    public Map<String, Object> getProps() { return props; }
    public String getUrl() { return url; }
    public String getVersion() { return version; }
}
