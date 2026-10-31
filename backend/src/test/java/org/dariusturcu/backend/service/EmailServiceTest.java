package org.dariusturcu.backend.service;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.Emails;
import com.resend.services.emails.model.CreateEmailOptions;
import org.dariusturcu.backend.config.EmailConfig.EmailMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Every send here goes through a mocked Resend client, never a real HTTP call.
@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    private static final String FROM_ADDRESS = "onboarding@resend.dev";
    private static final String RECIPIENT = "player@example.com";
    private static final String PRODUCT_NAME = "hittiguess";
    private static final String CONTACT_ADDRESS = "contact@hittiguess.com";

    private EmailMetadata emailMetadata;

    @Mock
    private Resend resend;

    @Mock
    private Emails emails;

    @BeforeEach
    void setUp() {
        emailMetadata = new EmailMetadata(PRODUCT_NAME, CONTACT_ADDRESS);
    }

    @Test
    void sendVerificationEmailRendersAllRequiredPartsAndCallsResend() throws ResendException {
        when(resend.emails()).thenReturn(emails);
        EmailService emailService = new EmailService(resend, FROM_ADDRESS, "re_real_key", emailMetadata);
        String verificationLink = "https://hittiguess.example/verify-email?token=abc";

        emailService.sendVerificationEmail(RECIPIENT, verificationLink);

        ArgumentCaptor<CreateEmailOptions> optionsCaptor = ArgumentCaptor.forClass(CreateEmailOptions.class);
        verify(emails).send(optionsCaptor.capture());

        CreateEmailOptions options = optionsCaptor.getValue();
        assertThat(options.getFrom()).isEqualTo(FROM_ADDRESS);
        assertThat(options.getTo()).containsExactly(RECIPIENT);
        assertThat(options.getSubject()).contains(PRODUCT_NAME);

        // Verification email HTML part checks
        assertThat(options.getHtml()).contains(PRODUCT_NAME);
        assertThat(options.getHtml()).contains(CONTACT_ADDRESS);
        assertThat(options.getHtml()).contains(verificationLink);
        assertThat(options.getHtml()).contains("sign-up request was made");

        // Verification email plain-text part checks
        assertThat(options.getText()).isNotNull();
        assertThat(options.getText()).contains(PRODUCT_NAME);
        assertThat(options.getText()).contains(CONTACT_ADDRESS);
        assertThat(options.getText()).contains(verificationLink);
        assertThat(options.getText()).contains("sign-up request was made");
    }

    @Test
    void sendPasswordResetEmailRendersAllRequiredPartsAndCallsResend() throws ResendException {
        when(resend.emails()).thenReturn(emails);
        EmailService emailService = new EmailService(resend, FROM_ADDRESS, "re_real_key", emailMetadata);
        String resetLink = "https://hittiguess.example/reset-password?token=abc";

        emailService.sendPasswordResetEmail(RECIPIENT, resetLink);

        ArgumentCaptor<CreateEmailOptions> optionsCaptor = ArgumentCaptor.forClass(CreateEmailOptions.class);
        verify(emails).send(optionsCaptor.capture());

        CreateEmailOptions options = optionsCaptor.getValue();
        assertThat(options.getFrom()).isEqualTo(FROM_ADDRESS);
        assertThat(options.getTo()).containsExactly(RECIPIENT);
        assertThat(options.getSubject()).contains(PRODUCT_NAME);

        // Password reset email HTML part checks
        assertThat(options.getHtml()).contains(PRODUCT_NAME);
        assertThat(options.getHtml()).contains(CONTACT_ADDRESS);
        assertThat(options.getHtml()).contains(resetLink);
        assertThat(options.getHtml()).contains("password reset request was made");

        // Password reset email plain-text part checks
        assertThat(options.getText()).isNotNull();
        assertThat(options.getText()).contains(PRODUCT_NAME);
        assertThat(options.getText()).contains(CONTACT_ADDRESS);
        assertThat(options.getText()).contains(resetLink);
        assertThat(options.getText()).contains("password reset request was made");
    }

    @Test
    void sendingDegradesToALogLineInsteadOfCallingResendWhenNoApiKeyIsConfigured() throws ResendException {
        EmailService emailService = new EmailService(resend, FROM_ADDRESS, "", emailMetadata);

        emailService.sendVerificationEmail(RECIPIENT, "https://hittiguess.example/verify-email?token=abc");

        verify(emails, never()).send(any(CreateEmailOptions.class));
    }

    @Test
    void aResendExceptionDuringSendIsSwallowedRatherThanPropagated() throws ResendException {
        when(resend.emails()).thenReturn(emails);
        EmailService emailService = new EmailService(resend, FROM_ADDRESS, "re_real_key", emailMetadata);
        when(emails.send(any(CreateEmailOptions.class))).thenThrow(new ResendException("delivery failed"));

        emailService.sendVerificationEmail(RECIPIENT, "https://hittiguess.example/verify-email?token=abc");

        verify(emails).send(any(CreateEmailOptions.class));
    }
}
