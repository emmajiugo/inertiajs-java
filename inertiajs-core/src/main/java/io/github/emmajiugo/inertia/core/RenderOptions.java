package io.github.emmajiugo.inertia.core;

import java.util.Map;

public final class RenderOptions {

    private static final RenderOptions EMPTY = new RenderOptions(null, null, null, null, null);

    private final Boolean encryptHistory;
    private final Boolean clearHistory;
    private final Boolean ssr;
    private final Boolean preserveFragment;
    private final Map<String, Object> scrollProps;

    private RenderOptions(Boolean encryptHistory, Boolean clearHistory, Boolean ssr,
                          Boolean preserveFragment, Map<String, Object> scrollProps) {
        this.encryptHistory = encryptHistory;
        this.clearHistory = clearHistory;
        this.ssr = ssr;
        this.preserveFragment = preserveFragment;
        this.scrollProps = scrollProps;
    }

    public Boolean getEncryptHistory() { return encryptHistory; }
    public Boolean getClearHistory() { return clearHistory; }
    public Boolean getSsr() { return ssr; }
    public Boolean getPreserveFragment() { return preserveFragment; }
    public Map<String, Object> getScrollProps() { return scrollProps; }

    public static RenderOptions empty() { return EMPTY; }
    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private Boolean encryptHistory;
        private Boolean clearHistory;
        private Boolean ssr;
        private Boolean preserveFragment;
        private Map<String, Object> scrollProps;

        private Builder() {}

        public Builder encryptHistory(boolean encryptHistory) {
            this.encryptHistory = encryptHistory;
            return this;
        }

        public Builder clearHistory(boolean clearHistory) {
            this.clearHistory = clearHistory;
            return this;
        }

        public Builder ssr(boolean ssr) {
            this.ssr = ssr;
            return this;
        }

        public Builder preserveFragment(boolean preserveFragment) {
            this.preserveFragment = preserveFragment;
            return this;
        }

        public Builder scrollProps(Map<String, Object> scrollProps) {
            this.scrollProps = scrollProps;
            return this;
        }

        public RenderOptions build() {
            return new RenderOptions(encryptHistory, clearHistory, ssr,
                    preserveFragment, scrollProps);
        }
    }
}
