package com.kbase.config.properties;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kbase.invitation")
public class InvitationProperties {

    private Duration expiration = Duration.ofHours(72);
    private String acceptBaseUrl = "http://localhost:3000/invitations/accept";

    public Duration getExpiration() {
        return expiration;
    }

    public void setExpiration(Duration expiration) {
        this.expiration = expiration;
    }

    public String getAcceptBaseUrl() {
        return acceptBaseUrl;
    }

    public void setAcceptBaseUrl(String acceptBaseUrl) {
        this.acceptBaseUrl = acceptBaseUrl;
    }
}
