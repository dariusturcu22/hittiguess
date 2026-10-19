package org.dariusturcu.backend.config;

import com.resend.Resend;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EmailConfig {
    // A syntactically valid but inert placeholder: EmailService.sendingEnabled gates every
    // real call, so this key is never actually sent to Resend when RESEND_API_KEY is unset.
    private static final String DISABLED_PLACEHOLDER_API_KEY = "re_disabled_placeholder";

    @Bean
    public Resend resend(@Value("${resend.api-key:}") String apiKey) {
        return new Resend(apiKey.isBlank() ? DISABLED_PLACEHOLDER_API_KEY : apiKey);
    }
}
