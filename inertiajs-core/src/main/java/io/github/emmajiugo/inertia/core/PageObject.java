package io.github.emmajiugo.inertia.core;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public final class PageObject {

    private final String component;
    private final Map<String, Object> props;
    private final String url;
    private final String version;
    private final Map<String, List<String>> deferredProps;
    private final List<String> mergeProps;
    private final List<String> prependProps;
    private final List<String> deepMergeProps;
    private final Map<String, String> matchPropsOn;
    private final Map<String, OncePropsEntry> onceProps;
    private final Boolean encryptHistory;
    private final Boolean clearHistory;

    private PageObject(Builder builder) {
        this.component = builder.component;
        this.props = builder.props;
        this.url = builder.url;
        this.version = builder.version;
        this.deferredProps = nullIfEmpty(builder.deferredProps);
        this.mergeProps = nullIfEmpty(builder.mergeProps);
        this.prependProps = nullIfEmpty(builder.prependProps);
        this.deepMergeProps = nullIfEmpty(builder.deepMergeProps);
        this.matchPropsOn = nullIfEmpty(builder.matchPropsOn);
        this.onceProps = nullIfEmpty(builder.onceProps);
        this.encryptHistory = builder.encryptHistory;
        this.clearHistory = builder.clearHistory;
    }

    public String getComponent() { return component; }
    public Map<String, Object> getProps() { return props; }
    public String getUrl() { return url; }
    public String getVersion() { return version; }
    public Map<String, List<String>> getDeferredProps() { return deferredProps; }
    public List<String> getMergeProps() { return mergeProps; }
    public List<String> getPrependProps() { return prependProps; }
    public List<String> getDeepMergeProps() { return deepMergeProps; }
    public Map<String, String> getMatchPropsOn() { return matchPropsOn; }
    public Map<String, OncePropsEntry> getOnceProps() { return onceProps; }
    public Boolean getEncryptHistory() { return encryptHistory; }
    public Boolean getClearHistory() { return clearHistory; }

    public static Builder builder() { return new Builder(); }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record OncePropsEntry(String prop, Long expiresAt) {}

    private static <T> List<T> nullIfEmpty(List<T> list) {
        return list != null && !list.isEmpty() ? list : null;
    }

    private static <K, V> Map<K, V> nullIfEmpty(Map<K, V> map) {
        return map != null && !map.isEmpty() ? map : null;
    }

    public static final class Builder {
        private String component;
        private Map<String, Object> props;
        private String url;
        private String version;
        private Map<String, List<String>> deferredProps;
        private List<String> mergeProps;
        private List<String> prependProps;
        private List<String> deepMergeProps;
        private Map<String, String> matchPropsOn;
        private Map<String, OncePropsEntry> onceProps;
        private Boolean encryptHistory;
        private Boolean clearHistory;

        private Builder() {}

        public Builder component(String component) { this.component = component; return this; }
        public Builder props(Map<String, Object> props) { this.props = props; return this; }
        public Builder url(String url) { this.url = url; return this; }
        public Builder version(String version) { this.version = version; return this; }
        public Builder deferredProps(Map<String, List<String>> deferredProps) { this.deferredProps = deferredProps; return this; }
        public Builder mergeProps(List<String> mergeProps) { this.mergeProps = mergeProps; return this; }
        public Builder prependProps(List<String> prependProps) { this.prependProps = prependProps; return this; }
        public Builder deepMergeProps(List<String> deepMergeProps) { this.deepMergeProps = deepMergeProps; return this; }
        public Builder matchPropsOn(Map<String, String> matchPropsOn) { this.matchPropsOn = matchPropsOn; return this; }
        public Builder onceProps(Map<String, OncePropsEntry> onceProps) { this.onceProps = onceProps; return this; }
        public Builder encryptHistory(Boolean encryptHistory) { this.encryptHistory = encryptHistory; return this; }
        public Builder clearHistory(Boolean clearHistory) { this.clearHistory = clearHistory; return this; }

        public PageObject build() { return new PageObject(this); }
    }
}
