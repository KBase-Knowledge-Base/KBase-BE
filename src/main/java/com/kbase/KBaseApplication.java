package com.kbase;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

/**
 * JWT-only authentication: no in-memory default user exists, so the servlet
 * UserDetailsService auto-configuration (and its generated-password log) is
 * excluded.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class KBaseApplication {

    public static void main(String[] args) {
        SpringApplication.run(KBaseApplication.class, args);
    }
}
