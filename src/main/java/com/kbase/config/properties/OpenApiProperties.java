package com.kbase.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kbase.openapi")
public class OpenApiProperties {

    private boolean enabled = true;
    private boolean swaggerUiEnabled = true;
    private String apiDocsPath = "/v3/api-docs";
    private String swaggerUiPath = "/swagger-ui.html";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isSwaggerUiEnabled() {
        return swaggerUiEnabled;
    }

    public void setSwaggerUiEnabled(boolean swaggerUiEnabled) {
        this.swaggerUiEnabled = swaggerUiEnabled;
    }

    public String getApiDocsPath() {
        return apiDocsPath;
    }

    public void setApiDocsPath(String apiDocsPath) {
        this.apiDocsPath = apiDocsPath;
    }

    public String getSwaggerUiPath() {
        return swaggerUiPath;
    }

    public void setSwaggerUiPath(String swaggerUiPath) {
        this.swaggerUiPath = swaggerUiPath;
    }
}
