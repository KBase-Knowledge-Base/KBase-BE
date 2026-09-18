package com.kbase.security.jwt;

import java.io.IOException;
import java.util.UUID;

import com.kbase.security.handler.RestSecurityErrorWriter;
import com.kbase.security.principal.CustomUserDetailsService;
import com.kbase.security.principal.CustomUserPrincipal;
import com.kbase.shared.exception.ErrorCode;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates requests carrying a Bearer access token.
 *
 * <p>Validation always resolves the current database User so that disabled
 * accounts lose access immediately even when an old token is unexpired. The
 * filter never evaluates project membership or document ownership.</p>
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** Request attribute carrying the token-failure code for the entry point. */
    public static final String TOKEN_VALIDATION_FAILURE_ATTRIBUTE =
            JwtAuthenticationFilter.class.getName() + ".tokenValidationFailure";

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;
    private final RestSecurityErrorWriter errorWriter;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            CustomUserDetailsService userDetailsService,
            RestSecurityErrorWriter errorWriter) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.errorWriter = errorWriter;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            markFailure(request, ErrorCode.INVALID_ACCESS_TOKEN);
            filterChain.doFilter(request, response);
            return;
        }

        try {
            Claims claims = jwtService.parseAndValidate(token);
            UUID userId = parseUserId(claims);
            CustomUserPrincipal principal = userDetailsService.loadUserById(userId)
                    .orElse(null);
            if (principal == null) {
                markFailure(request, ErrorCode.INVALID_ACCESS_TOKEN);
                filterChain.doFilter(request, response);
                return;
            }
            if (!principal.isActive()) {
                SecurityContextHolder.clearContext();
                errorWriter.write(ErrorCode.ACCOUNT_DISABLED, request, response);
                return;
            }
            if (!principal.isEmailVerified()) {
                SecurityContextHolder.clearContext();
                errorWriter.write(ErrorCode.EMAIL_NOT_VERIFIED, request, response);
                return;
            }

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            principal, null, principal.getAuthorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (ExpiredJwtException exception) {
            markFailure(request, ErrorCode.ACCESS_TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException exception) {
            markFailure(request, ErrorCode.INVALID_ACCESS_TOKEN);
        }

        filterChain.doFilter(request, response);
    }

    private static UUID parseUserId(Claims claims) {
        String subject = claims.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("JWT subject is missing");
        }
        return UUID.fromString(subject);
    }

    private static void markFailure(HttpServletRequest request, ErrorCode errorCode) {
        SecurityContextHolder.clearContext();
        request.setAttribute(TOKEN_VALIDATION_FAILURE_ATTRIBUTE, errorCode);
    }
}
