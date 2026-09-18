package com.kbase.security.config;

import java.util.List;

import com.kbase.config.properties.CorsProperties;
import com.kbase.config.properties.JwtProperties;
import com.kbase.config.properties.OpenApiProperties;
import com.kbase.security.handler.RestAccessDeniedHandler;
import com.kbase.security.handler.RestAuthenticationEntryPoint;
import com.kbase.security.handler.RestSecurityErrorWriter;
import com.kbase.security.jwt.JwtAuthenticationFilter;
import com.kbase.security.jwt.JwtService;
import com.kbase.security.principal.CustomUserDetailsService;
import com.kbase.shared.exception.RequestIdFilter;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Stateless security baseline: authentication through short-lived JWTs,
 * system-role authorization only. Project and document authorization live in
 * their domain services, never in this configuration.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfig {

    private static final String[] PUBLIC_AUTH_ENDPOINTS = {
            "/api/v1/auth/register",
            "/api/v1/auth/verify-email",
            "/api/v1/auth/resend-verification-otp",
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/auth/logout"
    };

    @Bean
    public CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        List<String> origins = properties.getAllowedOrigins() == null
                ? List.of()
                : List.copyOf(properties.getAllowedOrigins());
        if (origins.contains("*")) {
            if (properties.isAllowCredentials()) {
                throw new IllegalStateException(
                        "CORS configuration is invalid: wildcard origin cannot allow credentials");
            }
            configuration.setAllowedOrigins(List.of("*"));
        } else {
            configuration.setAllowedOrigins(origins);
            configuration.setAllowCredentials(properties.isAllowCredentials());
        }
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(
                HttpHeaders.AUTHORIZATION,
                HttpHeaders.CONTENT_TYPE,
                RequestIdFilter.REQUEST_ID_HEADER));
        configuration.setExposedHeaders(List.of(RequestIdFilter.REQUEST_ID_HEADER));
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtService jwtService,
            CustomUserDetailsService userDetailsService,
            RestSecurityErrorWriter errorWriter,
            RestAuthenticationEntryPoint authenticationEntryPoint,
            RestAccessDeniedHandler accessDeniedHandler,
            CorsConfigurationSource corsConfigurationSource,
            OpenApiProperties openApiProperties) throws Exception {

        JwtAuthenticationFilter jwtAuthenticationFilter =
                new JwtAuthenticationFilter(jwtService, userDetailsService, errorWriter);

        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> {
                    authorize.requestMatchers(HttpMethod.POST, PUBLIC_AUTH_ENDPOINTS).permitAll();
                    if (openApiProperties.isEnabled()) {
                        authorize.requestMatchers(openApiDocumentationMatchers(openApiProperties))
                                .permitAll();
                    }
                    authorize.requestMatchers("/error").permitAll();
                    authorize.requestMatchers("/api/v1/admin/**").hasRole("ADMIN");
                    authorize.anyRequest().authenticated();
                })
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private static String[] openApiDocumentationMatchers(OpenApiProperties properties) {
        String apiDocsPath = properties.getApiDocsPath();
        String swaggerUiPath = properties.getSwaggerUiPath();
        return new String[] {
                apiDocsPath,
                apiDocsPath + "/**",
                swaggerUiPath,
                swaggerUiPath + "/**",
                "/swagger-ui/**"
        };
    }
}
