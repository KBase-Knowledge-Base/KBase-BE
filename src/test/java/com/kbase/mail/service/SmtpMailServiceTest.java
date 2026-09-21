package com.kbase.mail.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.kbase.config.properties.MailProperties;
import com.kbase.mail.config.MailConfig;
import com.kbase.shared.exception.ErrorCode;
import com.kbase.shared.exception.MailServiceUnavailableException;

import jakarta.mail.Message;
import jakarta.mail.internet.MimeMessage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class SmtpMailServiceTest {

    private static final Instant EXPIRES_AT = Instant.parse("2026-09-17T12:05:00Z");

    @Test
    void mailServiceContractDoesNotExposeSmtpTypes() {
        assertThat(MailService.class.getDeclaredMethods())
                .allSatisfy(method -> assertThat(method.getParameterTypes())
                        .noneMatch(type -> type.getName().startsWith("org.springframework.mail")));
    }

    @Test
    void sendsVerificationAndInvitationMessagesThroughFakeSmtp() throws Exception {
        try (FakeSmtpServer smtp = new FakeSmtpServer()) {
            MailProperties properties = fakeMailProperties(smtp.getPort());
            JavaMailSenderImpl sender = new JavaMailSenderImpl();
            sender.setHost(properties.getHost());
            sender.setPort(properties.getPort());
            sender.setUsername(properties.getUsername());
            sender.setPassword(properties.getAppPassword());
            Properties javaMailProperties = new Properties();
            javaMailProperties.put("mail.smtp.auth", "false");
            javaMailProperties.put("mail.smtp.starttls.enable", "false");
            javaMailProperties.put("mail.smtp.connectiontimeout", "2000");
            javaMailProperties.put("mail.smtp.timeout", "2000");
            javaMailProperties.put("mail.smtp.writetimeout", "2000");
            sender.setJavaMailProperties(javaMailProperties);
            SmtpMailService service = new SmtpMailService(sender, properties);

            service.sendEmailVerificationOtp(
                    "recipient@example.invalid", "123456", EXPIRES_AT);
            String verificationMessage = smtp.awaitMessage();
            assertThat(verificationMessage).contains("To: recipient@example.invalid");
            assertThat(verificationMessage).contains("Verify your KBase email address");
            assertThat(verificationMessage).contains("123456");
            assertThat(verificationMessage).contains("2026-09-17T12:05:00Z");

            smtp.resetMessageLatch();
            service.sendProjectInvitation(
                    "invitee@example.invalid",
                    "Owner <Admin>",
                    "KBase Docs",
                    "https://app.example.invalid/invitations/opaque-token",
                    EXPIRES_AT);
            String invitationMessage = smtp.awaitMessage();
            assertThat(invitationMessage).contains("To: invitee@example.invalid");
            assertThat(invitationMessage).contains("Owner &lt;Admin&gt;");
            assertThat(invitationMessage).contains("KBase Docs");
            assertThat(invitationMessage).contains("https://app.example.invalid/invitations/opaque-token");
            assertThat(invitationMessage).doesNotContain("TEST_ONLY_NOT_A_CREDENTIAL");
        }
    }

    @Test
    void mailFailureMapsToEmailServiceUnavailableWithoutCredentialOrPayload() {
        String secret = "TEST_ONLY_SMTP_SECRET";
        JavaMailSenderImpl failingSender = new JavaMailSenderImpl() {
            @Override
            public void send(MimeMessage message) {
                throw new MailSendException("fake provider failure: " + secret);
            }
        };
        MailProperties properties = fakeMailProperties(25252);
        SmtpMailService service = new SmtpMailService(failingSender, properties);

        assertThatThrownBy(() -> service.sendEmailVerificationOtp(
                "recipient@example.invalid", "654321", EXPIRES_AT))
                .isInstanceOf(MailServiceUnavailableException.class)
                .satisfies(thrown -> {
                    assertThat(thrown).hasMessage(ErrorCode.EMAIL_SERVICE_UNAVAILABLE.getDefaultMessage());
                    assertThat(thrown.getMessage()).doesNotContain(secret, "654321");
                    assertThat(((MailServiceUnavailableException) thrown).getErrorCode())
                            .isEqualTo(ErrorCode.EMAIL_SERVICE_UNAVAILABLE);
                });
    }

    @Test
    void mailConfigUsesTypedGmailSettingsAndTimeoutWithoutConnecting() {
        MailProperties properties = new MailProperties();
        properties.setHost("smtp.gmail.com");
        properties.setPort(587);
        properties.setUsername("fake-sender@example.invalid");
        properties.setAppPassword("TEST_ONLY_NOT_A_CREDENTIAL");
        properties.setAuth(true);
        properties.setStartTls(true);
        properties.setTimeout(Duration.ofSeconds(7));

        JavaMailSenderImpl sender = (JavaMailSenderImpl) new MailConfig().javaMailSender(properties);

        assertThat(sender.getHost()).isEqualTo("smtp.gmail.com");
        assertThat(sender.getPort()).isEqualTo(587);
        assertThat(sender.getUsername()).isEqualTo("fake-sender@example.invalid");
        assertThat(sender.getJavaMailProperties())
                .containsEntry("mail.smtp.auth", "true")
                .containsEntry("mail.smtp.starttls.enable", "true")
                .containsEntry("mail.smtp.starttls.required", "true")
                .containsEntry("mail.smtp.connectiontimeout", "7000")
                .containsEntry("mail.smtp.timeout", "7000")
                .containsEntry("mail.smtp.writetimeout", "7000");
    }

    @Test
    void adapterFailureDoesNotWriteOtpInvitationTokenOrCredentialToLogs(CapturedOutput output) {
        String rawOtp = "654321";
        String invitationToken = "opaque-invitation-token";
        String credential = "TEST_ONLY_SMTP_SECRET";
        JavaMailSenderImpl failingSender = new JavaMailSenderImpl() {
            @Override
            public void send(MimeMessage message) {
                throw new MailSendException("provider=" + credential
                        + " otp=" + rawOtp + " token=" + invitationToken);
            }
        };
        SmtpMailService service = new SmtpMailService(failingSender, fakeMailProperties(25253));

        assertThatThrownBy(() -> service.sendEmailVerificationOtp(
                "recipient@example.invalid", rawOtp, EXPIRES_AT))
                .isInstanceOf(MailServiceUnavailableException.class);

        // The failure is diagnosable by exception type only; the provider
        // message (which carries payloads/credentials in this test) and the
        // exception cause must never reach the logs.
        assertThat(output).contains("Email delivery failed via SMTP provider type=");
        assertThat(output).contains("MailSendException");
        assertThat(output).doesNotContain(rawOtp, invitationToken, credential);
        assertThat(output).doesNotContain("provider=");
    }

    private static MailProperties fakeMailProperties(int port) {
        MailProperties properties = new MailProperties();
        properties.setHost("127.0.0.1");
        properties.setPort(port);
        properties.setUsername("fake-sender@example.invalid");
        properties.setAppPassword("TEST_ONLY_NOT_A_CREDENTIAL");
        properties.setAuth(false);
        properties.setStartTls(false);
        properties.setTimeout(Duration.ofSeconds(2));
        return properties;
    }

    private static final class FakeSmtpServer implements AutoCloseable {

        private final ServerSocket serverSocket;
        private final Thread worker;
        private volatile CountDownLatch messageLatch = new CountDownLatch(1);
        private volatile String message;

        private FakeSmtpServer() throws IOException {
            serverSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
            worker = new Thread(this::serve, "kbase-test-fake-smtp");
            worker.setDaemon(true);
            worker.start();
        }

        private int getPort() {
            return serverSocket.getLocalPort();
        }

        private void resetMessageLatch() {
            message = null;
            messageLatch = new CountDownLatch(1);
        }

        private String awaitMessage() throws InterruptedException {
            assertThat(messageLatch.await(5, TimeUnit.SECONDS)).isTrue();
            return message;
        }

        private void serve() {
            while (!serverSocket.isClosed()) {
                try (Socket socket = serverSocket.accept();
                        BufferedReader reader = new BufferedReader(new InputStreamReader(
                                socket.getInputStream(), StandardCharsets.UTF_8));
                        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                                socket.getOutputStream(), StandardCharsets.UTF_8))) {
                    reply(writer, "220 localhost ESMTP test server");
                    StringBuilder data = new StringBuilder();
                    boolean dataMode = false;
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (dataMode) {
                            if (".".equals(line)) {
                                dataMode = false;
                                message = data.toString();
                                messageLatch.countDown();
                                reply(writer, "250 2.0.0 queued");
                            } else {
                                data.append(line).append('\n');
                            }
                            continue;
                        }

                        String command = line.toUpperCase(Locale.ROOT);
                        if (command.startsWith("EHLO") || command.startsWith("HELO")) {
                            reply(writer, "250-localhost");
                            reply(writer, "250 SIZE 1000000");
                        } else if (command.startsWith("MAIL FROM") || command.startsWith("RCPT TO")) {
                            reply(writer, "250 2.1.0 ok");
                        } else if (command.startsWith("DATA")) {
                            dataMode = true;
                            reply(writer, "354 End data with <CR><LF>.<CR><LF>");
                        } else if (command.startsWith("QUIT")) {
                            reply(writer, "221 2.0.0 bye");
                            break;
                        } else {
                            reply(writer, "250 2.0.0 ok");
                        }
                    }
                } catch (IOException ignored) {
                    // The test closes the socket to stop the worker; no provider
                    // diagnostics or message payload is logged.
                }
            }
        }

        private static void reply(BufferedWriter writer, String response) throws IOException {
            writer.write(response);
            writer.write("\r\n");
            writer.flush();
        }

        @Override
        public void close() throws Exception {
            serverSocket.close();
            worker.join(1000);
        }
    }
}
