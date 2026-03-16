package io.inertia.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "inertia")
public class InertiaProperties {

    private String version = "1";
    private String templatePath = "templates/app.html";

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getTemplatePath() { return templatePath; }
    public void setTemplatePath(String templatePath) { this.templatePath = templatePath; }
}
