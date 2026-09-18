package com.kbase.auth.service;

import java.util.Locale;
import java.util.Objects;

import com.kbase.auth.dto.request.LoginRequest;
import com.kbase.auth.dto.request.RegisterRequest;
import com.kbase.auth.dto.response.AccessTokenResponse;
import com.kbase.auth.dto.response.RegisterResponse;
import com.kbase.auth.dto.response.UserResponse;
import com.kbase.auth.entity.RefreshSession;
import com.kbase.security.jwt.JwtService;
import com.kbase.shared.exception.BusinessException;
import com.kbase.shared.exception.ErrorCode;
import com.kbase.user.entity.User;
import com.kbase.user.enums.SystemRole;
import com.kbase.user.enums.UserStatus;
import com.kbase.user.repository.UserRepository;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registration, login, refresh and logout orchestration. Login requires a
 * valid password, an ACTIVE account and a verified email; failure responses
 * never reveal whether an email exists.
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshSessionService refreshSessionService;
    private final EmailVerificationService emailVerificationService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            RefreshSessionService refreshSessionService,
            EmailVerificationService emailVerificationService) {
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder, "passwordEncoder");
        this.jwtService = Objects.requireNonNull(jwtService, "jwtService");
        this.refreshSessionService = Objects.requireNonNull(refreshSessionService, "refreshSessionService");
        this.emailVerificationService =
                Objects.requireNonNull(emailVerificationService, "emailVerificationService");
    }

    /** Creates an unverified USER account and issues the verification OTP. */
    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        Objects.requireNonNull(request, "request");
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        User user = new User(
                email,
                passwordEncoder.encode(request.password()),
                request.displayName().trim(),
                SystemRole.USER,
                UserStatus.ACTIVE);
        try {
            user = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            // Concurrent registration hit the unique email constraint.
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        emailVerificationService.issueOtp(user);
        return new RegisterResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getSystemRole(),
                user.getStatus(),
                user.getEmailVerifiedAt() != null,
                user.getCreatedAt());
    }

    /** Authenticates an ACTIVE, verified account and starts a refresh session. */
    @Transactional
    public LoginResult login(LoginRequest request) {
        Objects.requireNonNull(request, "request");
        String email = normalizeEmail(request.email());
        User user = userRepository.findByEmail(email)
                .orElseThrow(AuthService::invalidCredentials);
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw invalidCredentials();
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }
        if (user.getEmailVerifiedAt() == null) {
            throw new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED);
        }

        JwtService.IssuedAccessToken accessToken = jwtService.generateAccessToken(user);
        RefreshSessionService.CreatedRefreshSession session =
                refreshSessionService.createSession(user);
        return new LoginResult(
                accessToken.token(),
                accessToken.expiresInSeconds(),
                session.rawToken(),
                toUserResponse(user));
    }

    /** Exchanges a valid refresh cookie for a new short-lived access token. */
    @Transactional(readOnly = true)
    public AccessTokenResponse refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_MISSING);
        }
        RefreshSession session = refreshSessionService.validate(rawRefreshToken);
        User user = session.getUser();
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }
        if (user.getEmailVerifiedAt() == null) {
            throw new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED);
        }

        JwtService.IssuedAccessToken accessToken = jwtService.generateAccessToken(user);
        return new AccessTokenResponse(accessToken.token(), "Bearer", accessToken.expiresInSeconds());
    }

    /** Revokes the current refresh session when a token is present. */
    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        refreshSessionService.revokeIfPresent(rawRefreshToken);
    }

    private static BusinessException invalidCredentials() {
        return new BusinessException(ErrorCode.INVALID_CREDENTIALS);
    }

    static String normalizeEmail(String email) {
        Objects.requireNonNull(email, "email");
        return email.trim().toLowerCase(Locale.ROOT);
    }

    static UserResponse toUserResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getSystemRole(),
                user.getStatus(),
                user.getEmailVerifiedAt() != null);
    }

    /** Internal login result; the raw refresh token never leaves this boundary. */
    public record LoginResult(
            String accessToken,
            long expiresInSeconds,
            String rawRefreshToken,
            UserResponse user) {

        @Override
        public String toString() {
            return "LoginResult{expiresInSeconds=" + expiresInSeconds
                    + ", rawRefreshToken=[REDACTED], user=" + user + "}";
        }
    }
}
