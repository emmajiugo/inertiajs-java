package io.inertia.core;

public final class InertiaConfig {

    private final String version;
    private final TemplateResolver templateResolver;
    private final JsonSerializer jsonSerializer;

    private InertiaConfig(Builder builder) {
        this.version = builder.version;
        this.templateResolver = builder.templateResolver;
        this.jsonSerializer = builder.jsonSerializer != null
                ? builder.jsonSerializer
                : new JacksonJsonSerializer();
    }

    public String getVersion() { return version; }
    public TemplateResolver getTemplateResolver() { return templateResolver; }
    public JsonSerializer getJsonSerializer() { return jsonSerializer; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String version = "1";
        private TemplateResolver templateResolver;
        private JsonSerializer jsonSerializer;

        private Builder() {}

        public Builder version(String version) {
            this.version = version;
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

        public InertiaConfig build() {
            if (templateResolver == null) {
                throw new IllegalStateException("templateResolver is required");
            }
            return new InertiaConfig(this);
        }
    }
}
