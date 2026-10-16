package org.dariusturcu.backend.controller;

import org.dariusturcu.backend.exception.GlobalExceptionHandler;
import org.dariusturcu.backend.model.admin.AdminDashboardDTO;
import org.dariusturcu.backend.model.admin.DashboardQueueDTO;
import org.dariusturcu.backend.model.admin.DashboardSongCountsDTO;
import org.dariusturcu.backend.model.usage.AdminUsageDTO;
import org.dariusturcu.backend.model.user.Role;
import org.dariusturcu.backend.model.user.User;
import org.dariusturcu.backend.security.AdminAccessGuard;
import org.dariusturcu.backend.security.UserPrincipal;
import org.dariusturcu.backend.service.AdminDashboardService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Runs the dashboard endpoint through a standalone MockMvc with the real admin guard and
 * exception handler, so access control and the response shape are checked without a database.
 */
class AdminDashboardControllerTest {

    private static final String DASHBOARD_PATH = "/api/admin/dashboard";
    private static final String PRICES_VERIFIED_ON = "2026-10-16";
    private static final long VERIFIED_COUNT = 30;
    private static final long WAITING_ROWS = 6;

    private final AdminDashboardService adminDashboardService = mock(AdminDashboardService.class);

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new AdminDashboardController(adminDashboardService, new AdminAccessGuard()))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private static void authenticateWithRole(Role role) {
        User user = new User();
        user.setUsername("dashboard-test-" + role.name().toLowerCase());
        user.setRole(role);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new UserPrincipal(user), null, null));
    }

    @Test
    void anAdminGetsTheDashboard() throws Exception {
        authenticateWithRole(Role.ADMIN);
        AdminUsageDTO usage = new AdminUsageDTO(null, null, null, BigDecimal.ZERO, BigDecimal.ZERO,
                LocalDate.parse(PRICES_VERIFIED_ON));
        when(adminDashboardService.dashboard()).thenReturn(new AdminDashboardDTO(
                new DashboardSongCountsDTO(VERIFIED_COUNT, 0L, 0L, 0L, 0L, VERIFIED_COUNT),
                new DashboardQueueDTO(WAITING_ROWS, 0L),
                usage));

        mockMvc.perform(get(DASHBOARD_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.songs.verified").value(VERIFIED_COUNT))
                .andExpect(jsonPath("$.songs.total").value(VERIFIED_COUNT))
                .andExpect(jsonPath("$.queue.waiting").value(WAITING_ROWS))
                .andExpect(jsonPath("$.usage.pricesVerifiedOn").value(PRICES_VERIFIED_ON));
    }

    @Test
    void aNormalUserIsForbidden() throws Exception {
        authenticateWithRole(Role.USER);

        mockMvc.perform(get(DASHBOARD_PATH)).andExpect(status().isForbidden());

        verify(adminDashboardService, never()).dashboard();
    }
}
