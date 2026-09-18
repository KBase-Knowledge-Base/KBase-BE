package com.kbase.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceTest {

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private JwtService jwtService;
    private RefreshSessionService refreshSessionService;
    private EmailVerificationService emailVerificationService;
    private AuthService service;
    private User user;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        jwtService = mock(JwtService.class);
        refreshSessionService = mock(RefreshSessionService.class);
        emailVerificationService = mock(EmailVerificationService.class);
        service = new AuthService(
                userRepository,
                passwordEncoder,
                jwtService,
                refreshSessionService,
                emailVerificationService);

        user = new User(
                "user@example.com", "bcrypt-hash", "Example User",
                SystemRole.USER, UserStatus.ACTIVE);
        user.setId(UUID.randomUUID());
    }

    private static RegisterRequest registerRequest() {
        return new RegisterRequest("User@Example.com", "ExamplePassword123", "Example User");
    }

    private static LoginRequest loginRequest() {
        return new LoginRequest("user@example.com", "ExamplePassword123");
    }

    private void stubSuccessfulLogin() {
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("ExamplePassword123", "bcrypt-hash")).thenReturn(true);
        when(jwtService.generateAccessToken(user))
                .thenReturn(new JwtService.IssuedAccessToken("access-token", 900));
        when(refreshSessionService.createSession(user))
                .thenReturn(new RefreshSessionService.CreatedRefreshSession(
                        "raw-refresh-token", UUID.randomUUID(), null));
    }

    @Test
    void registerCreatesUnverifiedUserAndIssuesOtp() {
        when(userRepository.existsByEmail("user@example.com")).thenReturn(false);
        when(passwordEncoder.encode("ExamplePassword123")).thenReturn("bcrypt-hash");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            saved.setCreatedAt(java.time.Instant.now());
            return saved;
        });

        RegisterResponse response = service.register(registerRequest());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(captor.capture());
        User persisted = captor.getValue();
        assertThat(persisted.getEmail()).isEqualTo("user@example.com");
        assertThat(persisted.getPasswordHash()).isEqualTo("bcrypt-hash");
        assertThat(persisted.getSystemRole()).isEqualTo(SystemRole.USER);
        assertThat(persisted.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(persisted.getEmailVerifiedAt()).isNull();

        verify(emailVerificationService).issueOtp(persisted);
        assertThat(response.emailVerified()).isFalse();
        assertThat(response.systemRole()).isEqualTo(SystemRole.USER);
        assertThat(response.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(response.createdAt()).isNotNull();
    }

    @Test
    void registerNormalizesEmailAndRejectsDuplicates() {
        when(userRepository.existsByEmail("user@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register(registerRequest()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS));
        verify(userRepository, never()).saveAndFlush(any(User.class));
        verify(emailVerificationService, never()).issueOtp(any(User.class));
    }

    @Test
    void loginRejectsUnknownEmailAndWrongPasswordWithGenericError() {
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.login(loginRequest()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_CREDENTIALS));

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("ExamplePassword123", "bcrypt-hash")).thenReturn(false);
        assertThatThrownBy(() -> service.login(loginRequest()))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS);
                    assertThat(exception.getSafeMessage())
                            .doesNotContain("user@example.com")
                            .doesNotContain("bcrypt-hash");
                });

        verify(jwtService, never()).generateAccessToken(any(User.class));
        verify(refreshSessionService, never()).createSession(any(User.class));
    }

    @Test
    void loginRejectsUnverifiedAccountBeforeIssuingTokens() {
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("ExamplePassword123", "bcrypt-hash")).thenReturn(true);

        assertThatThrownBy(() -> service.login(loginRequest()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.EMAIL_NOT_VERIFIED));
        verify(jwtService, never()).generateAccessToken(any(User.class));
    }

    @Test
    void loginRejectsDisabledAccount() {
        user.setStatus(UserStatus.DISABLED);
        user.setEmailVerifiedAt(java.time.Instant.now());
        stubSuccessfulLogin();
        // password matches, but account is disabled

        assertThatThrownBy(() -> service.login(loginRequest()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.ACCOUNT_DISABLED));
        verify(jwtService, never()).generateAccessToken(any(User.class));
    }

    @Test
    void loginReturnsAccessAndRefreshCredentialsForVerifiedAccount() {
        user.setEmailVerifiedAt(java.time.Instant.now());
        stubSuccessfulLogin();

        AuthService.LoginResult result = service.login(loginRequest());

        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.expiresInSeconds()).isEqualTo(900);
        assertThat(result.rawRefreshToken()).isEqualTo("raw-refresh-token");
        assertThat(result.user().email()).isEqualTo("user@example.com");
        assertThat(result.user().emailVerified()).isTrue();
        assertThat(result.user().systemRole()).isEqualTo(SystemRole.USER);
        assertThat(result.user()).extracting(UserResponse::toString)
                .asString()
                .doesNotContain("password");
        assertThat(result.toString()).doesNotContain("raw-refresh-token");
    }

    @Test
    void refreshRejectsMissingInvalidAndRevokedTokens() {
        assertThatThrownBy(() -> service.refresh(null))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.REFRESH_TOKEN_MISSING));
        assertThatThrownBy(() -> service.refresh(" "))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.REFRESH_TOKEN_MISSING));

        when(refreshSessionService.validate("raw-token"))
                .thenThrow(new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));
        assertThatThrownBy(() -> service.refresh("raw-token"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN));
    }

    @Test
    void refreshIssuesNewAccessTokenForActiveVerifiedSession() {
        user.setEmailVerifiedAt(java.time.Instant.now());
        RefreshSession session = new RefreshSession(user, "hash", java.time.Instant.now().plusSeconds(60));
        when(refreshSessionService.validate("raw-token")).thenReturn(session);
        when(jwtService.generateAccessToken(user))
                .thenReturn(new JwtService.IssuedAccessToken("new-access-token", 900));

        AccessTokenResponse response = service.refresh("raw-token");

        assertThat(response.accessToken()).isEqualTo("new-access-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(900);
    }

    @Test
    void refreshRejectsDisabledOrUnverifiedAccount() {
        RefreshSession session = new RefreshSession(user, "hash", java.time.Instant.now().plusSeconds(60));
        when(refreshSessionService.validate("raw-token")).thenReturn(session);

        user.setStatus(UserStatus.DISABLED);
        assertThatThrownBy(() -> service.refresh("raw-token"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ACCOUNT_DISABLED));

        user.setStatus(UserStatus.ACTIVE);
        assertThatThrownBy(() -> service.refresh("raw-token"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.EMAIL_NOT_VERIFIED));
        verify(jwtService, never()).generateAccessToken(any(User.class));
    }

    @Test
    void logoutRevokesSessionWhenTokenPresentAndIsIdempotent() {
        service.logout(null);
        service.logout("  ");
        verify(refreshSessionService, never()).revokeIfPresent(anyString());

        service.logout("raw-token");
        verify(refreshSessionService).revokeIfPresent("raw-token");
    }
}
