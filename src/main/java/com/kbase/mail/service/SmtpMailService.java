package com.kbase.mail.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Objects;

import com.kbase.config.properties.MailProperties;
import com.kbase.shared.exception.MailServiceUnavailableException;

import jakarta.mail.MessagingException;

import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.util.HtmlUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Gmail SMTP adapter behind the application-level {@link MailService} port. */
@Service
public class SmtpMailService implements MailService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SmtpMailService.class);

    private static final String VERIFICATION_TEMPLATE = "templates/mail/email-verification-otp.html";
    private static final String INVITATION_TEMPLATE = "templates/mail/project-invitation.html";
    private static final String VERIFICATION_SUBJECT = "Verify your KBase email address";
    private static final String INVITATION_SUBJECT = "You have been invited to a KBase project";

    private final JavaMailSender mailSender;
    private final MailProperties properties;

    public SmtpMailService(JavaMailSender mailSender, MailProperties properties) {
        this.mailSender = Objects.requireNonNull(mailSender, "mailSender");
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    @Override
    public void sendEmailVerificationOtp(String recipientEmail, String otp, Instant expiresAt) {
        Objects.requireNonNull(recipientEmail, "recipientEmail");
        Objects.requireNonNull(otp, "otp");
        Objects.requireNonNull(expiresAt, "expiresAt");
        String html = render(VERIFICATION_TEMPLATE, Map.of(
                "recipientEmail", HtmlUtils.htmlEscape(recipientEmail),
                "otp", HtmlUtils.htmlEscape(otp),
                "expiresAt", formatInstant(expiresAt)));
        send(recipientEmail, VERIFICATION_SUBJECT, html);
    }

    @Override
    public void sendProjectInvitation(
            String recipientEmail,
            String inviterDisplayName,
            String projectName,
            String invitationUrl,
            Instant expiresAt) {
        Objects.requireNonNull(recipientEmail, "recipientEmail");
        Objects.requireNonNull(inviterDisplayName, "inviterDisplayName");
        Objects.requireNonNull(projectName, "projectName");
        Objects.requireNonNull(invitationUrl, "invitationUrl");
        Objects.requireNonNull(expiresAt, "expiresAt");
        String html = render(INVITATION_TEMPLATE, Map.of(
                "recipientEmail", HtmlUtils.htmlEscape(recipientEmail),
                "inviterDisplayName", HtmlUtils.htmlEscape(inviterDisplayName),
                "projectName", HtmlUtils.htmlEscape(projectName),
                "invitationUrl", HtmlUtils.htmlEscape(invitationUrl),
                "expiresAt", formatInstant(expiresAt)));
        send(recipientEmail, INVITATION_SUBJECT, html);
    }

    private void send(String recipientEmail, String subject, String html) {
        try {
            var message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message,
                    false,
                    StandardCharsets.UTF_8.name());
            helper.setTo(recipientEmail);
            if (properties.getUsername() != null && !properties.getUsername().isBlank()) {
                helper.setFrom(properties.getUsername());
            }
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
        } catch (MessagingException | RuntimeException exception) {
            // Deliberately discard the provider exception as a cause: provider
            // diagnostics may contain message payloads or credential details.
            // The exception TYPE alone is safe to log and is the minimum
            // server-side diagnostic for SMTP failures (audit L-07).
            LOGGER.error("Email delivery failed via SMTP provider type={}",
                    exception.getClass().getName());
            throw new MailServiceUnavailableException();
        }
    }

    private String render(String templatePath, Map<String, String> values) {
        try (var inputStream = new ClassPathResource(templatePath).getInputStream()) {
            String template = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            String rendered = template;
            for (Map.Entry<String, String> entry : values.entrySet()) {
                rendered = rendered.replace("{{" + entry.getKey() + "}}", entry.getValue());
            }
            return rendered;
        } catch (IOException exception) {
            throw new MailServiceUnavailableException();
        }
    }

    private String formatInstant(Instant instant) {
        return HtmlUtils.htmlEscape(DateTimeFormatter.ISO_INSTANT.format(instant));
    }
}
