package io.github.emmajiugo.inertia.core;

/**
 * Optional configuration for a single render call.
 * Controls history encryption, SSR, and other per-page behaviors.
 *
 * Usage:
 * <pre>
 * engine.render(req, res, "Account/Settings", props,
 *     RenderOptions.builder().encryptHistory(true).ssr(false).build());
 * </pre>
 */
public final class RenderOptions {

    private static final RenderOptions EMPTY = new RenderOptions(null, null, null);

    private final Boolean encryptHistory;
    private final Boolean clearHistory;
    private final Boolean ssr;

    private RenderOptions(Boolean encryptHistory, Boolean clearHistory, Boolean ssr) {
        this.encryptHistory = encryptHistory;
        this.clearHistory = clearHistory;
        this.ssr = ssr;
    }

    public Boolean getEncryptHistory() { return encryptHistory; }
    public Boolean getClearHistory() { return clearHistory; }
    public Boolean getSsr() { return ssr; }

    public static RenderOptions empty() { return EMPTY; }
    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private Boolean encryptHistory;
        private Boolean clearHistory;
        private Boolean ssr;

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

        public RenderOptions build() { return new RenderOptions(encryptHistory, clearHistory, ssr); }
    }
}
