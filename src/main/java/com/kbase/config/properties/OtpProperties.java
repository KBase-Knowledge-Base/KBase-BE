package com.kbase.config.properties;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kbase.otp")
public class OtpProperties {

    private int length = 6;
    private Duration ttl = Duration.ofMinutes(5);
    private Duration resendCooldown = Duration.ofSeconds(60);
    private int maxAttempts = 5;
    private String hashSecret;

    public int getLength() {
        return length;
    }

    public void setLength(int length) {
        this.length = length;
    }

    public Duration getTtl() {
        return ttl;
    }

    public void setTtl(Duration ttl) {
        this.ttl = ttl;
    }

    public Duration getResendCooldown() {
        return resendCooldown;
    }

    public void setResendCooldown(Duration resendCooldown) {
        this.resendCooldown = resendCooldown;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public String getHashSecret() {
        return hashSecret;
    }

    public void setHashSecret(String hashSecret) {
        this.hashSecret = hashSecret;
    }
}
