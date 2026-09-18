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
import com.kbase.config.OpenApiConfig;
import com.kbase.config.properties.RefreshCookieProperties;
import com.kbase.shared.exception.BusinessException;
import com.kbase.shared.exception.ErrorCode;
import com.kbase.shared.response.ApiErrorResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

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
@Tag(name = OpenApiConfig.TAG_AUTHENTICATION,
        description = "Public authentication. The verification OTP is a Redis-backed 6-digit code sent through "
                + "Gmail SMTP for registration email verification only — not OTP login, MFA or password reset. "
                + "The refresh token is only ever an HttpOnly cookie and never appears in a JSON body.")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final String JSON_ERROR = "application/json";

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

    @Operation(summary = "Register account",
            description = "Creates an account with systemRole USER, status ACTIVE and no verified email. "
                    + "A 6-digit verification OTP is stored as short-lived Redis state (TTL 5 minutes by default) "
                    + "and sent to the email through Gmail SMTP. No auto-login; role and verification state are not client-settable.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Account created unverified; verification OTP sent"),
            @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR — request fields are invalid",
                    content = @Content(mediaType = JSON_ERROR, schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "EMAIL_ALREADY_EXISTS — the email is already registered",
                    content = @Content(mediaType = JSON_ERROR, schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "EMAIL_SERVICE_UNAVAILABLE — Gmail delivery failed and the "
                    + "registration was rolled back, or OTP_SERVICE_UNAVAILABLE — Redis OTP state is unavailable",
                    content = @Content(mediaType = JSON_ERROR, schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @Operation(summary = "Verify email with OTP",
            description = "Verifies registration email ownership. The OTP is the 6-digit email verification code "
                    + "delivered by Gmail SMTP (baseline: TTL 5 minutes, maximum 5 attempts); the raw OTP is never returned "
                    + "by the API. Success sets the account email as verified and invalidates the Redis OTP state.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Email verified"),
            @ApiResponse(responseCode = "400", description = "INVALID_OTP — wrong code, or OTP_EXPIRED — the pending "
                    + "verification state has expired",
                    content = @Content(mediaType = JSON_ERROR, schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "EMAIL_ALREADY_VERIFIED — the account email is already verified",
                    content = @Content(mediaType = JSON_ERROR, schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "OTP_ATTEMPTS_EXCEEDED — too many wrong attempts",
                    content = @Content(mediaType = JSON_ERROR, schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "OTP_SERVICE_UNAVAILABLE — Redis OTP state is unavailable",
                    content = @Content(mediaType = JSON_ERROR, schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping("/verify-email")
    public ResponseEntity<VerifyEmailResponse> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        EmailVerificationService.VerifyEmailResult result =
                emailVerificationService.verify(request.email(), request.otp());
        return ResponseEntity.ok(new VerifyEmailResponse(result.email(), result.emailVerified()));
    }

    @Operation(summary = "Resend verification OTP",
            description = "Sends a new verification OTP through Gmail SMTP for an existing, unverified account. "
                    + "A resend cooldown applies (60 seconds by default); the new OTP replaces the previous pending "
                    + "state and resets its TTL and attempt counter. The OTP exists only as protected short-lived Redis state.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "New OTP sent"),
            @ApiResponse(responseCode = "409", description = "EMAIL_ALREADY_VERIFIED — the account email is already verified",
                    content = @Content(mediaType = JSON_ERROR, schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "OTP_RESEND_COOLDOWN — another OTP was requested too recently",
                    content = @Content(mediaType = JSON_ERROR, schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "OTP_SERVICE_UNAVAILABLE — Redis OTP state unavailable, or "
                    + "EMAIL_SERVICE_UNAVAILABLE — Gmail delivery failed",
                    content = @Content(mediaType = JSON_ERROR, schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping("/resend-verification-otp")
    public ResponseEntity<Void> resendVerificationOtp(
            @Valid @RequestBody ResendVerificationOtpRequest request) {
        emailVerificationService.resend(request.email());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Login",
            description = "Authenticates with email and password. Requires an ACTIVE account with a verified email. "
                    + "Returns a JWT access token for the Authorization: Bearer header; the refresh token is set as an "
                    + "HttpOnly cookie (kbase_refresh_token) and is not part of the JSON response. Credential failures "
                    + "never reveal whether the email exists.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Authenticated; access token in body and refresh cookie in Set-Cookie"),
            @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR — request fields are invalid",
                    content = @Content(mediaType = JSON_ERROR, schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "INVALID_CREDENTIALS — unknown email or wrong password",
                    content = @Content(mediaType = JSON_ERROR, schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "EMAIL_NOT_VERIFIED — the email has not been verified, or "
                    + "ACCOUNT_DISABLED — the account is disabled",
                    content = @Content(mediaType = JSON_ERROR, schema = @Schema(implementation = ApiErrorResponse.class)))
    })
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

    @Operation(summary = "Refresh access token",
            description = "Issues a new access token from the refresh session. Requires the HttpOnly refresh cookie "
                    + "(kbase_refresh_token); no Bearer access token is used. The session is PostgreSQL-backed "
                    + "(hash-only) and must be unrevoked and unexpired for an ACTIVE verified user. Core v1 does not "
                    + "rotate the refresh token. Because the cookie is HttpOnly and browser-managed, Swagger UI may not "
                    + "be able to call this endpoint outside a same-origin session; that is a tooling limitation, not an API change.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "New access token issued"),
            @ApiResponse(responseCode = "401", description = "REFRESH_TOKEN_MISSING — no refresh cookie, "
                    + "INVALID_REFRESH_TOKEN / REFRESH_TOKEN_EXPIRED / REFRESH_SESSION_REVOKED — the refresh session is unusable",
                    content = @Content(mediaType = JSON_ERROR, schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping("/refresh")
    public ResponseEntity<AccessTokenResponse> refresh(
            @Parameter(in = ParameterIn.COOKIE, name = "kbase_refresh_token", required = true,
                    description = "HttpOnly refresh cookie set by login; not readable by client scripts")
            @CookieValue(name = "${kbase.refresh-cookie.name}", required = false) String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_MISSING);
        }
        AccessTokenResponse response = authService.refresh(rawRefreshToken);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Logout",
            description = "Revokes the current refresh session and clears the refresh cookie. Effectively idempotent: "
                    + "logout without a valid cookie still returns 204. Access tokens are not blacklisted; an existing "
                    + "access token remains valid until it expires.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Refresh session revoked and cookie cleared; no response body")
    })
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @Parameter(in = ParameterIn.COOKIE, name = "kbase_refresh_token", required = false,
                    description = "Optional HttpOnly refresh cookie identifying the session to revoke")
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
