package io.inertia.spring;

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
}
