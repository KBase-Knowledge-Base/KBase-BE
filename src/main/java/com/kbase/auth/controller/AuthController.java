package com.kbase.auth.controller;

import com.kbase.auth.dto.request.LoginRequest;
import com.kbase.auth.dto.request.RegisterRequest;
import com.kbase.auth.dto.request.ResendVerificationOtpRequest;
import com.kbase.auth.dto.request.VerifyEmailRequest;
import com.kbase.auth.dto.response.AccessTokenResponse;
import com.kbase.auth.dto.response.LoginResponse;
import com.kbase.auth.dto.response.RegisterResponse;
import com.kbase.auth.dto.response.VerifyEmailResponse;
import com.kbase.auth.service.AuthService;
import com.kbase.auth.service.EmailVerificationService;
import com.kbase.config.properties.RefreshCookieProperties;
import com.kbase.shared.exception.BusinessException;
import com.kbase.shared.exception.ErrorCode;

import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public authentication endpoints. The raw refresh token exists only in the
 * HttpOnly cookie and is never returned in a JSON body or logged.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final EmailVerificationService emailVerificationService;
    private final RefreshCookieProperties refreshCookieProperties;

    public AuthController(
            AuthService authService,
            EmailVerificationService emailVerificationService,
            RefreshCookieProperties refreshCookieProperties) {
        this.authService = authService;
        this.emailVerificationService = emailVerificationService;
        this.refreshCookieProperties = refreshCookieProperties;
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/verify-email")
    public ResponseEntity<VerifyEmailResponse> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        EmailVerificationService.VerifyEmailResult result =
                emailVerificationService.verify(request.email(), request.otp());
        return ResponseEntity.ok(new VerifyEmailResponse(result.email(), result.emailVerified()));
    }

    @PostMapping("/resend-verification-otp")
    public ResponseEntity<Void> resendVerificationOtp(
            @Valid @RequestBody ResendVerificationOtpRequest request) {
        emailVerificationService.resend(request.email());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthService.LoginResult result = authService.login(request);
        ResponseCookie cookie = refreshCookie(result.rawRefreshToken());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(new LoginResponse(
                        result.accessToken(),
                        "Bearer",
                        result.expiresInSeconds(),
                        result.user()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AccessTokenResponse> refresh(
            @CookieValue(name = "${kbase.refresh-cookie.name}", required = false) String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_MISSING);
        }
        AccessTokenResponse response = authService.refresh(rawRefreshToken);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = "${kbase.refresh-cookie.name}", required = false) String rawRefreshToken) {
        authService.logout(rawRefreshToken);
        ResponseCookie cleared = clearedRefreshCookie();
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cleared.toString())
                .build();
    }

    private ResponseCookie refreshCookie(String rawToken) {
        return ResponseCookie.from(refreshCookieProperties.getName(), rawToken)
                .httpOnly(true)
                .secure(refreshCookieProperties.isSecure())
                .sameSite(refreshCookieProperties.getSameSite())
                .path(refreshCookieProperties.getPath())
                .maxAge(refreshCookieProperties.getMaxAge())
                .build();
    }

    private ResponseCookie clearedRefreshCookie() {
        return ResponseCookie.from(refreshCookieProperties.getName(), "")
                .httpOnly(true)
                .secure(refreshCookieProperties.isSecure())
                .sameSite(refreshCookieProperties.getSameSite())
                .path(refreshCookieProperties.getPath())
                .maxAge(java.time.Duration.ZERO)
                .build();
    }
}
