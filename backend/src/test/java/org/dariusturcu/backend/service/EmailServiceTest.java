package org.dariusturcu.backend.service;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.Emails;
import com.resend.services.emails.model.CreateEmailOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Every send here goes through a mocked Resend client, never a real HTTP call.
@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    private static final String FROM_ADDRESS = "onboarding@resend.dev";
    private static final String RECIPIENT = "player@example.com";

    @Mock
    private Resend resend;

    @Mock
    private Emails emails;

    @Test
    void sendVerificationEmailCallsResendWhenAnApiKeyIsConfigured() throws ResendException {
        when(resend.emails()).thenReturn(emails);
        EmailService emailService = new EmailService(resend, FROM_ADDRESS, "re_real_key");

        emailService.sendVerificationEmail(RECIPIENT, "https://hittiguess.example/verify-email?token=abc");

        verify(emails).send(any(CreateEmailOptions.class));
    }

    @Test
    void sendPasswordResetEmailCallsResendWhenAnApiKeyIsConfigured() throws ResendException {
        when(resend.emails()).thenReturn(emails);
        EmailService emailService = new EmailService(resend, FROM_ADDRESS, "re_real_key");

        emailService.sendPasswordResetEmail(RECIPIENT, "https://hittiguess.example/reset-password?token=abc");

        verify(emails).send(any(CreateEmailOptions.class));
    }

    @Test
    void sendingDegradesToALogLineInsteadOfCallingResendWhenNoApiKeyIsConfigured() throws ResendException {
        EmailService emailService = new EmailService(resend, FROM_ADDRESS, "");

        emailService.sendVerificationEmail(RECIPIENT, "https://hittiguess.example/verify-email?token=abc");

        verify(emails, never()).send(any(CreateEmailOptions.class));
    }

    @Test
    void aResendExceptionDuringSendIsSwallowedRatherThanPropagated() throws ResendException {
        when(resend.emails()).thenReturn(emails);
        EmailService emailService = new EmailService(resend, FROM_ADDRESS, "re_real_key");
        when(emails.send(any(CreateEmailOptions.class))).thenThrow(new ResendException("delivery failed"));

        emailService.sendVerificationEmail(RECIPIENT, "https://hittiguess.example/verify-email?token=abc");

        verify(emails).send(any(CreateEmailOptions.class));
    }
}
