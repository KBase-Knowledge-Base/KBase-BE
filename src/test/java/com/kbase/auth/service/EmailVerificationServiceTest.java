package com.kbase.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import com.kbase.mail.service.MailService;
import com.kbase.shared.exception.BusinessException;
import com.kbase.shared.exception.ErrorCode;
import com.kbase.shared.exception.MailServiceUnavailableException;
import com.kbase.shared.exception.OtpServiceUnavailableException;
import com.kbase.user.entity.User;
import com.kbase.user.enums.SystemRole;
import com.kbase.user.enums.UserStatus;
import com.kbase.user.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class EmailVerificationServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-17T00:00:00Z");

    private UserRepository userRepository;
    private OtpService otpService;
    private MailService mailService;
    private EmailVerificationService service;
    private User user;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        otpService = mock(OtpService.class);
        mailService = mock(MailService.class);
        service = new EmailVerificationService(
                userRepository, otpService, mailService, Clock.fixed(NOW, ZoneOffset.UTC));

        user = new User(
                "user@example.com", "password-hash", "Example User",
                SystemRole.USER, UserStatus.ACTIVE);
        user.setId(UUID.randomUUID());
    }

    @Test
    void issueOtpStoresProtectedStateThenSendsEmailWithRawOtp() {
        OtpService.GeneratedOtp generated =
                OtpService.GeneratedOtp.of("123456", NOW.plusSeconds(300));
        when(otpService.issueVerificationOtp(user.getId())).thenReturn(generated);

        service.issueOtp(user);

        verify(otpService).issueVerificationOtp(user.getId());
        ArgumentCaptor<String> otpCaptor = ArgumentCaptor.forClass(String.class);
        verify(mailService).sendEmailVerificationOtp(
                any(), otpCaptor.capture(), any());
        assertThat(otpCaptor.getValue()).isEqualTo("123456");
        verify(otpService, never()).deleteVerificationOtp(user.getId());
    }

    @Test
    void issueOtpCleansRedisStateAndRethrowsWhenMailSendFails() {
        OtpService.GeneratedOtp generated =
                OtpService.GeneratedOtp.of("123456", NOW.plusSeconds(300));
        when(otpService.issueVerificationOtp(user.getId())).thenReturn(generated);
        doThrow(new MailServiceUnavailableException())
                .when(mailService)
                .sendEmailVerificationOtp(any(), any(), any());

        assertThatThrownBy(() -> service.issueOtp(user))
                .isInstanceOfSatisfying(MailServiceUnavailableException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.EMAIL_SERVICE_UNAVAILABLE));
        verify(otpService).deleteVerificationOtp(user.getId());
    }

    @Test
    void issueOtpPropagatesRedisFailureBeforeAnyMailSend() {
        when(otpService.issueVerificationOtp(user.getId()))
                .thenThrow(new OtpServiceUnavailableException());

        assertThatThrownBy(() -> service.issueOtp(user))
                .isInstanceOfSatisfying(OtpServiceUnavailableException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.OTP_SERVICE_UNAVAILABLE));
        verify(mailService, never()).sendEmailVerificationOtp(any(), any(), any());
    }

    @Test
    void verifySetsEmailVerifiedAtAndPersistsUser() {
        user.setEmailVerifiedAt(null);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EmailVerificationService.VerifyEmailResult result =
                service.verify("User@Example.com ", "123456");

        verify(otpService).verify(user.getId(), "123456");
        assertThat(result.email()).isEqualTo("user@example.com");
        assertThat(result.emailVerified()).isTrue();
        assertThat(user.getEmailVerifiedAt()).isEqualTo(NOW);
        verify(userRepository).save(user);
        verify(otpService).deleteVerificationOtp(user.getId());
    }

    @Test
    void verifyRejectsUnknownEmailAndAlreadyVerifiedAccounts() {
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.verify("unknown@example.com", "123456"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND));

        user.setEmailVerifiedAt(NOW.minusSeconds(60));
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        assertThatThrownBy(() -> service.verify("user@example.com", "123456"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.EMAIL_ALREADY_VERIFIED));

        verify(otpService, never()).verify(any(), any());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void verifyPropagatesOtpErrorsWithoutTouchingVerificationState() {
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        doThrow(new BusinessException(ErrorCode.INVALID_OTP))
                .when(otpService).verify(user.getId(), "000000");

        assertThatThrownBy(() -> service.verify("user@example.com", "000000"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_OTP));

        assertThat(user.getEmailVerifiedAt()).isNull();
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void resendReplacesOtpThenSendsNewCode() {
        user.setEmailVerifiedAt(null);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        OtpService.GeneratedOtp generated =
                OtpService.GeneratedOtp.of("654321", NOW.plusSeconds(300));
        when(otpService.resendVerificationOtp(user.getId())).thenReturn(generated);

        service.resend("user@example.com");

        verify(otpService).resendVerificationOtp(user.getId());
        verify(mailService).sendEmailVerificationOtp(
                any(), any(), any());
        verify(otpService, never()).deleteVerificationOtp(user.getId());
    }

    @Test
    void resendRespectsCooldownAndCleanupOnMailFailure() {
        user.setEmailVerifiedAt(null);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        doThrow(new BusinessException(ErrorCode.OTP_RESEND_COOLDOWN))
                .when(otpService).resendVerificationOtp(user.getId());

        assertThatThrownBy(() -> service.resend("user@example.com"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.OTP_RESEND_COOLDOWN));
        verify(mailService, never()).sendEmailVerificationOtp(any(), any(), any());

        OtpService.GeneratedOtp generated =
                OtpService.GeneratedOtp.of("654321", NOW.plusSeconds(300));
        doReturn(generated).when(otpService).resendVerificationOtp(user.getId());
        doThrow(new MailServiceUnavailableException())
                .when(mailService)
                .sendEmailVerificationOtp(any(), any(), any());

        assertThatThrownBy(() -> service.resend("user@example.com"))
                .isInstanceOfSatisfying(MailServiceUnavailableException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.EMAIL_SERVICE_UNAVAILABLE));
        verify(otpService).deleteVerificationOtp(user.getId());
    }

    @Test
    void bestEffortCleanupFailureDoesNotMaskMailFailure() {
        OtpService.GeneratedOtp generated =
                OtpService.GeneratedOtp.of("123456", NOW.plusSeconds(300));
        when(otpService.issueVerificationOtp(user.getId())).thenReturn(generated);
        doThrow(new MailServiceUnavailableException())
                .when(mailService)
                .sendEmailVerificationOtp(any(), any(), any());
        doThrow(new OtpServiceUnavailableException())
                .when(otpService).deleteVerificationOtp(user.getId());

        assertThatThrownBy(() -> service.issueOtp(user))
                .isInstanceOf(MailServiceUnavailableException.class);
    }
}
