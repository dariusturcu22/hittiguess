package org.dariusturcu.backend.controller;

import org.dariusturcu.backend.exception.GlobalExceptionHandler;
import org.dariusturcu.backend.model.usage.AdminUsageDTO;
import org.dariusturcu.backend.model.usage.ProviderUsageDTO;
import org.dariusturcu.backend.model.usage.TokenUsageDTO;
import org.dariusturcu.backend.model.usage.UsageProvider;
import org.dariusturcu.backend.model.usage.YoutubeQuotaUsageDTO;
import org.dariusturcu.backend.model.user.Role;
import org.dariusturcu.backend.model.user.User;
import org.dariusturcu.backend.security.AdminAccessGuard;
import org.dariusturcu.backend.security.UserPrincipal;
import org.dariusturcu.backend.service.ApiUsageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Runs the usage endpoint through a standalone MockMvc with the real admin guard and exception
 * handler, so access control and the response shape are checked without a database.
 */
class AdminUsageControllerTest {

    private static final String USAGE_PATH = "/api/admin/usage";
    private static final long UNITS_USED = 120;
    private static final long DAILY_LIMIT = 10_000;

    private final ApiUsageService apiUsageService = mock(ApiUsageService.class);

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new AdminUsageController(apiUsageService, new AdminAccessGuard()))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private static void authenticateWithRole(Role role) {
        User user = new User();
        user.setUsername("usage-test-" + role.name().toLowerCase());
        user.setRole(role);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new UserPrincipal(user), null, null));
    }

    private static AdminUsageDTO sampleUsage() {
        TokenUsageDTO noTokens = new TokenUsageDTO(0, 0, 0, BigDecimal.ZERO);
        return new AdminUsageDTO(
                new YoutubeQuotaUsageDTO(UNITS_USED, DAILY_LIMIT, DAILY_LIMIT - UNITS_USED, LocalDate.of(2026, 10, 16),
                        Instant.parse("2026-10-17T07:00:00Z")),
                new ProviderUsageDTO(UsageProvider.DEEPINFRA, noTokens, noTokens),
                new ProviderUsageDTO(UsageProvider.OPENAI, noTokens, noTokens),
                BigDecimal.ZERO, BigDecimal.ZERO, LocalDate.of(2026, 10, 16));
    }

    @Test
    void anAdminGetsTheUsageReport() throws Exception {
        authenticateWithRole(Role.ADMIN);
        when(apiUsageService.usage()).thenReturn(sampleUsage());

        mockMvc.perform(get(USAGE_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.youtube.unitsUsedToday").value(UNITS_USED))
                .andExpect(jsonPath("$.youtube.unitsRemaining").value(DAILY_LIMIT - UNITS_USED))
                .andExpect(jsonPath("$.deepInfra.provider").value(UsageProvider.DEEPINFRA.name()))
                .andExpect(jsonPath("$.openAi.provider").value(UsageProvider.OPENAI.name()));
    }

    @Test
    void aNormalUserIsForbidden() throws Exception {
        authenticateWithRole(Role.USER);

        mockMvc.perform(get(USAGE_PATH)).andExpect(status().isForbidden());

        verify(apiUsageService, never()).usage();
    }
}
