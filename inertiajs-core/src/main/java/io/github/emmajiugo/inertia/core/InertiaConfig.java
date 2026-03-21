package io.github.emmajiugo.inertia.core;

import java.util.function.Supplier;

public final class InertiaConfig {

    private final Supplier<String> versionSupplier;
    private final TemplateResolver templateResolver;
    private final JsonSerializer jsonSerializer;
    private final SsrClient ssrClient;
    private final boolean ssrEnabled;
    private final boolean ssrFailOnError;

    private InertiaConfig(Builder builder) {
        this.versionSupplier = builder.versionSupplier;
        this.templateResolver = builder.templateResolver;
        this.jsonSerializer = builder.jsonSerializer != null
                ? builder.jsonSerializer
                : new JacksonJsonSerializer();
        this.ssrClient = builder.ssrClient;
        this.ssrEnabled = builder.ssrEnabled;
        this.ssrFailOnError = builder.ssrFailOnError;
    }

    public String getVersion() { return versionSupplier.get(); }
    public TemplateResolver getTemplateResolver() { return templateResolver; }
    public JsonSerializer getJsonSerializer() { return jsonSerializer; }
    public SsrClient getSsrClient() { return ssrClient; }
    public boolean isSsrEnabled() { return ssrEnabled; }
    public boolean isSsrFailOnError() { return ssrFailOnError; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private Supplier<String> versionSupplier = () -> "1";
        private TemplateResolver templateResolver;
        private JsonSerializer jsonSerializer;
        private SsrClient ssrClient;
        private boolean ssrEnabled = true;
        private boolean ssrFailOnError = false;

        private Builder() {}

        public Builder version(String version) {
            this.versionSupplier = () -> version;
            return this;
        }

        public Builder versionSupplier(Supplier<String> versionSupplier) {
            this.versionSupplier = versionSupplier;
            return this;
        }

        public Builder templateResolver(TemplateResolver templateResolver) {
            this.templateResolver = templateResolver;
            return this;
        }

        public Builder jsonSerializer(JsonSerializer jsonSerializer) {
            this.jsonSerializer = jsonSerializer;
            return this;
        }

        public Builder ssrClient(SsrClient ssrClient) {
            this.ssrClient = ssrClient;
            return this;
        }

        public Builder ssrEnabled(boolean ssrEnabled) {
            this.ssrEnabled = ssrEnabled;
            return this;
        }

        public Builder ssrFailOnError(boolean ssrFailOnError) {
            this.ssrFailOnError = ssrFailOnError;
            return this;
        }

        public InertiaConfig build() {
            if (templateResolver == null) {
                throw new IllegalStateException("templateResolver is required");
            }
            return new InertiaConfig(this);
        }
    }
}
