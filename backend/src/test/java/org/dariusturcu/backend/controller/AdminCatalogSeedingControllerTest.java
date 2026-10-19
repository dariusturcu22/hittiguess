package org.dariusturcu.backend.controller;

import org.dariusturcu.backend.exception.GlobalExceptionHandler;
import org.dariusturcu.backend.model.song.AdminCatalogSeedingRequest;
import org.dariusturcu.backend.model.song.EnqueueResultDTO;
import org.dariusturcu.backend.model.user.Role;
import org.dariusturcu.backend.model.user.User;
import org.dariusturcu.backend.security.AdminAccessGuard;
import org.dariusturcu.backend.security.UserPrincipal;
import org.dariusturcu.backend.service.CatalogSeedingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminCatalogSeedingControllerTest {
    private static final String VIDEO_ID = "dQw4w9WgXcQ";
    private static final long BATCH_ID = 17;
    private static final String ENQUEUE_BODY = "{\"youtubeIds\":[\"" + VIDEO_ID + "\"]}";
    private final CatalogSeedingService seeding = mock(CatalogSeedingService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(
            new AdminCatalogSeedingController(seeding, new AdminAccessGuard()))
            .setControllerAdvice(new GlobalExceptionHandler()).build();

    private void authenticate(Role role) {
        User user = new User();
        user.setUsername("seeding-test");
        user.setRole(role);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new UserPrincipal(user), null, null));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void anAdminCanEnqueueAStageBatch() throws Exception {
        authenticate(Role.ADMIN);
        when(seeding.enqueue(any(AdminCatalogSeedingRequest.class)))
                .thenReturn(new EnqueueResultDTO(Set.of(VIDEO_ID), Set.of(), Set.of(), BATCH_ID));

        mvc.perform(post("/api/admin/catalog-seeding/enqueue")
                        .contentType(MediaType.APPLICATION_JSON).content(ENQUEUE_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batchId").value(BATCH_ID));
    }

    @Test
    void enqueueRequiresAdmin() throws Exception {
        authenticate(Role.USER);

        mvc.perform(post("/api/admin/catalog-seeding/enqueue")
                        .contentType(MediaType.APPLICATION_JSON).content(ENQUEUE_BODY))
                .andExpect(status().isForbidden());
        verifyNoInteractions(seeding);
    }

    @Test
    void sequentialDrainAndStatusRoutesNoLongerExist() throws Exception {
        authenticate(Role.ADMIN);

        mvc.perform(post("/api/admin/catalog-seeding/drain")).andExpect(status().isNotFound());
        mvc.perform(get("/api/admin/catalog-seeding/status")).andExpect(status().isNotFound());
        verifyNoInteractions(seeding);
    }
}
