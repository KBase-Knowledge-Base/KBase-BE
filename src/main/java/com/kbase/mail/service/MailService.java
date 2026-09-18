package com.kbase.mail.service;

import java.time.Instant;

/**
 * Application mail port. Feature services depend on this contract, never on
 * JavaMail, SMTP or Gmail-specific types.
 */
public interface MailService {

    void sendEmailVerificationOtp(String recipientEmail, String otp, Instant expiresAt);

    void sendProjectInvitation(
            String recipientEmail,
            String inviterDisplayName,
            String projectName,
            String invitationUrl,
            Instant expiresAt);
}
