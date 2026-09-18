package com.kbase.config.properties;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kbase.refresh-cookie")
public class RefreshCookieProperties {

    private String name = "kbase_refresh_token";
    private boolean secure = true;
    private String sameSite = "Lax";
    private Duration maxAge = Duration.ofDays(7);
    private String path = "/api/v1/auth";

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public boolean isSecure() {
        return secure;
    }

    public void setSecure(boolean secure) {
        this.secure = secure;
    }

    public String getSameSite() {
        return sameSite;
    }

    public void setSameSite(String sameSite) {
        this.sameSite = sameSite;
    }

    public Duration getMaxAge() {
        return maxAge;
    }

    public void setMaxAge(Duration maxAge) {
        this.maxAge = maxAge;
    }
}
