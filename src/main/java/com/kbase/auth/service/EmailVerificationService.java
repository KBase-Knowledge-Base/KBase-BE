package com.kbase.auth.service;

import java.time.Clock;
import java.util.Locale;
import java.util.Objects;

import com.kbase.mail.service.MailService;
import com.kbase.shared.exception.BusinessException;
import com.kbase.shared.exception.ErrorCode;
import com.kbase.user.entity.User;
import com.kbase.user.repository.UserRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates registration email verification: transient OTP state in Redis
 * through {@link OtpService} and delivery through {@link MailService}. The
 * persistent verification result is {@code users.email_verified_at}.
 */
@Service
public class EmailVerificationService {

    private final UserRepository userRepository;
    private final OtpService otpService;
    private final MailService mailService;
    private final Clock clock;

    @Autowired
    public EmailVerificationService(
            UserRepository userRepository,
            OtpService otpService,
            MailService mailService) {
        this(userRepository, otpService, mailService, Clock.systemUTC());
    }

    public EmailVerificationService(
            UserRepository userRepository,
            OtpService otpService,
            MailService mailService,
            Clock clock) {
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
        this.otpService = Objects.requireNonNull(otpService, "otpService");
        this.mailService = Objects.requireNonNull(mailService, "mailService");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Issues the first verification OTP after registration. */
    public void issueOtp(User user) {
        Objects.requireNonNull(user, "user");
        OtpService.GeneratedOtp generated = otpService.issueVerificationOtp(user.getId());
        sendWithCleanup(user, generated);
    }

    /**
     * Verifies a submitted OTP, sets the persistent verification timestamp and
     * best-effort removes the transient Redis state.
     */
    @Transactional
    public VerifyEmailResult verify(String email, String otp) {
        User user = requireUnverifiedUser(email);
        otpService.verify(user.getId(), otp);
        user.setEmailVerifiedAt(clock.instant());
        userRepository.save(user);
        bestEffortDeleteOtpState(user.getId());
        return new VerifyEmailResult(user.getEmail(), true);
    }

    /** Replaces the pending OTP after the resend cooldown has elapsed. */
    public void resend(String email) {
        User user = requireUnverifiedUser(email);
        OtpService.GeneratedOtp generated = otpService.resendVerificationOtp(user.getId());
        sendWithCleanup(user, generated);
    }

    private User requireUnverifiedUser(String email) {
        String normalized = normalizeEmail(email);
        User user = userRepository.findByEmail(normalized)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (user.getEmailVerifiedAt() != null) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_VERIFIED);
        }
        return user;
    }

    /**
     * Delivers the OTP by email. If the send fails after the Redis write, the
     * new state is best-effort removed so the user can retry with a clean
     * resend; the original mail failure is rethrown unchanged.
     */
    private void sendWithCleanup(User user, OtpService.GeneratedOtp generated) {
        try {
            mailService.sendEmailVerificationOtp(
                    user.getEmail(), generated.getOtp(), generated.getExpiresAt());
        } catch (RuntimeException exception) {
            bestEffortDeleteOtpState(user.getId());
            throw exception;
        }
    }

    private void bestEffortDeleteOtpState(java.util.UUID userId) {
        try {
            otpService.deleteVerificationOtp(userId);
        } catch (RuntimeException ignored) {
            // Best-effort only: repeated verification is still rejected by
            // the persisted emailVerifiedAt value.
        }
    }

    static String normalizeEmail(String email) {
        Objects.requireNonNull(email, "email");
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public record VerifyEmailResult(String email, boolean emailVerified) {
    }
}
