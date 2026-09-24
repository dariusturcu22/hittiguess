package org.dariusturcu.backend.service;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class EmailService {
    private static final String VERIFICATION_EMAIL_SUBJECT = "Verify your hittiguess email";
    private static final String PASSWORD_RESET_EMAIL_SUBJECT = "Reset your hittiguess password";

    private final Resend resend;
    private final String fromAddress;
    private final boolean sendingEnabled;

    public EmailService(
            Resend resend,
            @Value("${resend.from-address}") String fromAddress,
            @Value("${resend.api-key:}") String apiKey
    ) {
        this.resend = resend;
        this.fromAddress = fromAddress;
        this.sendingEnabled = !apiKey.isBlank();
    }

    public void sendVerificationEmail(String recipientEmail, String verificationLink) {
        String body = """
                <p>Click the link below to verify your hittiguess email address:</p>
                <p><a href="%s">%s</a></p>
                <p>This link expires soon and can only be used once.</p>
                """.formatted(verificationLink, verificationLink);
        send(recipientEmail, VERIFICATION_EMAIL_SUBJECT, body);
    }

    public void sendPasswordResetEmail(String recipientEmail, String resetLink) {
        String body = """
                <p>Click the link below to reset your hittiguess password:</p>
                <p><a href="%s">%s</a></p>
                <p>This link expires soon and can only be used once. Ignore this email if you
                did not request a password reset.</p>
                """.formatted(resetLink, resetLink);
        send(recipientEmail, PASSWORD_RESET_EMAIL_SUBJECT, body);
    }

    private void send(String recipientEmail, String subject, String htmlBody) {
        if (!sendingEnabled) {
            return;
        }

        CreateEmailOptions options = CreateEmailOptions.builder()
                .from(fromAddress)
                .to(recipientEmail)
                .subject(subject)
                .html(htmlBody)
                .build();

        try {
            resend.emails().send(options);
        } catch (ResendException exception) {
            // A transient email-provider failure should not block account creation or a
            // password-reset request from otherwise succeeding; the caller already generated
            // a valid token, and resend-verification/password-reset/request exist to retry.
            log.error("Email delivery failed", exception);
        }
    }
}
