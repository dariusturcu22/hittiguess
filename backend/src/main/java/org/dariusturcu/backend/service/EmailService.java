package org.dariusturcu.backend.service;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import lombok.extern.slf4j.Slf4j;
import org.dariusturcu.backend.config.EmailConfig.EmailMetadata;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class EmailService {
    private static final String VERIFICATION_EMAIL_SUBJECT_TEMPLATE = "Verify your %s email";
    private static final String PASSWORD_RESET_EMAIL_SUBJECT_TEMPLATE = "Reset your %s password";

    private final Resend resend;
    private final String fromAddress;
    private final EmailMetadata emailMetadata;
    private final boolean sendingEnabled;

    public EmailService(
            Resend resend,
            @Value("${resend.from-address}") String fromAddress,
            @Value("${resend.api-key:}") String apiKey,
            EmailMetadata emailMetadata
    ) {
        this.resend = resend;
        this.fromAddress = fromAddress;
        this.sendingEnabled = !apiKey.isBlank();
        this.emailMetadata = emailMetadata;
    }

    public void sendVerificationEmail(String recipientEmail, String verificationLink) {
        String productName = emailMetadata.productName();
        String contactAddress = emailMetadata.contactAddress();
        String subject = VERIFICATION_EMAIL_SUBJECT_TEMPLATE.formatted(productName);

        String htmlBody = """
                <p>Click the link below to verify your %s email address:</p>
                <p><a href="%s">%s</a></p>
                <p>This message was sent because a sign-up request was made for this email address on %s.</p>
                <p>This link expires soon and can only be used once. If you did not create an account, you can safely ignore this email.</p>
                <p>For questions or assistance, contact us at %s.</p>
                """.formatted(productName, verificationLink, verificationLink, productName, contactAddress);

        String plainTextBody = """
                Click the link below to verify your %s email address:

                %s

                This message was sent because a sign-up request was made for this email address on %s.
                This link expires soon and can only be used once. If you did not create an account, you can safely ignore this email.

                For questions or assistance, contact us at %s.
                """.formatted(productName, verificationLink, productName, contactAddress);

        send(recipientEmail, subject, htmlBody, plainTextBody);
    }

    public void sendPasswordResetEmail(String recipientEmail, String resetLink) {
        String productName = emailMetadata.productName();
        String contactAddress = emailMetadata.contactAddress();
        String subject = PASSWORD_RESET_EMAIL_SUBJECT_TEMPLATE.formatted(productName);

        String htmlBody = """
                <p>Click the link below to reset your %s password:</p>
                <p><a href="%s">%s</a></p>
                <p>This message was sent because a password reset request was made for your %s account.</p>
                <p>This link expires soon and can only be used once. If you did not request a password reset, you can safely ignore this email.</p>
                <p>For questions or assistance, contact us at %s.</p>
                """.formatted(productName, resetLink, resetLink, productName, contactAddress);

        String plainTextBody = """
                Click the link below to reset your %s password:

                %s

                This message was sent because a password reset request was made for your %s account.
                This link expires soon and can only be used once. If you did not request a password reset, you can safely ignore this email.

                For questions or assistance, contact us at %s.
                """.formatted(productName, resetLink, productName, contactAddress);

        send(recipientEmail, subject, htmlBody, plainTextBody);
    }

    private void send(String recipientEmail, String subject, String htmlBody, String plainTextBody) {
        if (!sendingEnabled) {
            return;
        }

        CreateEmailOptions options = CreateEmailOptions.builder()
                .from(fromAddress)
                .to(recipientEmail)
                .subject(subject)
                .html(htmlBody)
                .text(plainTextBody)
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
