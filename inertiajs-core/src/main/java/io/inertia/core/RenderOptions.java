package io.inertia.core;

/**
 * Optional configuration for a single render call.
 * Controls history encryption and other per-page behaviors.
 *
 * Usage:
 * <pre>
 * engine.render(req, res, "Account/Settings", props,
 *     RenderOptions.builder().encryptHistory(true).build());
 * </pre>
 */
public final class RenderOptions {

    private static final RenderOptions EMPTY = new RenderOptions(null, null);

    private final Boolean encryptHistory;
    private final Boolean clearHistory;

    private RenderOptions(Boolean encryptHistory, Boolean clearHistory) {
        this.encryptHistory = encryptHistory;
        this.clearHistory = clearHistory;
    }

    public Boolean getEncryptHistory() { return encryptHistory; }
    public Boolean getClearHistory() { return clearHistory; }

    public static RenderOptions empty() { return EMPTY; }
    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private Boolean encryptHistory;
        private Boolean clearHistory;

        private Builder() {}

        public Builder encryptHistory(boolean encryptHistory) {
            this.encryptHistory = encryptHistory;
            return this;
        }

        public Builder clearHistory(boolean clearHistory) {
            this.clearHistory = clearHistory;
            return this;
        }

        public RenderOptions build() { return new RenderOptions(encryptHistory, clearHistory); }
    }
}
