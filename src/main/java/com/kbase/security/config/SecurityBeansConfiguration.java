package com.kbase.security.config;

import com.kbase.config.properties.JwtProperties;
import com.kbase.security.jwt.JwtService;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Security components that every application context needs, including
 * non-web contexts. The servlet filter chain itself stays in
 * {@link SecurityConfig}.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityBeansConfiguration {

    @Bean
    public JwtService jwtService(JwtProperties properties) {
        return new JwtService(properties);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
