package io.github.emmajiugo.inertia.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "inertia")
public class InertiaProperties {

    private String version;
    private String manifestPath = "static/.vite/manifest.json";
    private String templatePath = "templates/app.html";
    private boolean cacheTemplates = true;

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getManifestPath() { return manifestPath; }
    public void setManifestPath(String manifestPath) { this.manifestPath = manifestPath; }

    public String getTemplatePath() { return templatePath; }
    public void setTemplatePath(String templatePath) { this.templatePath = templatePath; }

    public boolean isCacheTemplates() { return cacheTemplates; }
    public void setCacheTemplates(boolean cacheTemplates) { this.cacheTemplates = cacheTemplates; }

    private Ssr ssr = new Ssr();

    public Ssr getSsr() { return ssr; }
    public void setSsr(Ssr ssr) { this.ssr = ssr; }

    public static class Ssr {
        private boolean enabled = true;
        private String url;               // null by default — SSR only activates when explicitly set
        private int timeout = 1500;
        private boolean failOnError = false;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }

        public int getTimeout() { return timeout; }
        public void setTimeout(int timeout) { this.timeout = timeout; }

        public boolean isFailOnError() { return failOnError; }
        public void setFailOnError(boolean failOnError) { this.failOnError = failOnError; }
    }
}
