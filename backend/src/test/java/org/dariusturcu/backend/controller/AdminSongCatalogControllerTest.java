package org.dariusturcu.backend.controller;

import org.dariusturcu.backend.exception.GlobalExceptionHandler;
import org.dariusturcu.backend.model.song.AdminSongDetailDTO;
import org.dariusturcu.backend.model.song.CatalogAddedByKind;
import org.dariusturcu.backend.model.song.CatalogPipeline;
import org.dariusturcu.backend.model.song.CatalogSongStatus;
import org.dariusturcu.backend.model.song.VerificationStatus;
import org.dariusturcu.backend.model.user.Role;
import org.dariusturcu.backend.model.user.User;
import org.dariusturcu.backend.repository.AdminSongCatalogRepository;
import org.dariusturcu.backend.repository.PendingImportRepository;
import org.dariusturcu.backend.repository.SongReportRepository;
import org.dariusturcu.backend.repository.SongRepository;
import org.dariusturcu.backend.security.AdminAccessGuard;
import org.dariusturcu.backend.security.UserPrincipal;
import org.dariusturcu.backend.service.AdminSongCatalogService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Runs the catalog endpoints through a standalone MockMvc with the real admin guard, the real
 * request validation in the service, and the real exception handler, over a mocked repository.
 */
class AdminSongCatalogControllerTest {

    private static final String CATALOG_PATH = "/api/admin/songs";
    private static final String DETAIL_PATH = "/api/admin/songs/{songId}";
    private static final long SONG_ID = 12;
    private static final String YOUTUBE_ID = "dQw4w9WgXcQ";

    private final AdminSongCatalogRepository catalogRepository = mock(AdminSongCatalogRepository.class);
    private final AdminSongCatalogService catalogService = spy(new AdminSongCatalogService(catalogRepository,
            mock(SongRepository.class), mock(PendingImportRepository.class), mock(SongReportRepository.class)));

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new AdminSongCatalogController(catalogService, new AdminAccessGuard()))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private static void authenticateWithRole(Role role) {
        User user = new User();
        user.setUsername("catalog-test-" + role.name().toLowerCase());
        user.setRole(role);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new UserPrincipal(user), null, null));
    }

    private static Map<CatalogSongStatus, Long> oneVerifiedSong() {
        Map<CatalogSongStatus, Long> counts = new EnumMap<>(CatalogSongStatus.class);
        for (CatalogSongStatus catalogStatus : CatalogSongStatus.values()) {
            counts.put(catalogStatus, 0L);
        }
        counts.put(CatalogSongStatus.VERIFIED, 1L);
        return counts;
    }

    @Test
    void anAdminGetsTheCatalogPage() throws Exception {
        authenticateWithRole(Role.ADMIN);
        when(catalogRepository.countByStatus(isNull())).thenReturn(oneVerifiedSong());
        when(catalogRepository.findPage(isNull(), isNull(), anyInt(), anyLong())).thenReturn(List.of());

        mockMvc.perform(get(CATALOG_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(AdminSongCatalogService.FIRST_PAGE))
                .andExpect(jsonPath("$.size").value(AdminSongCatalogService.DEFAULT_PAGE_SIZE))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.counts.all").value(1))
                .andExpect(jsonPath("$.counts.verified").value(1));
    }

    @Test
    void anAdminGetsASongDetail() throws Exception {
        authenticateWithRole(Role.ADMIN);
        AdminSongDetailDTO detail = new AdminSongDetailDTO(SONG_ID, YOUTUBE_ID, "Zi-mi", List.of("Loredana"), 2019,
                CatalogSongStatus.NEEDS_REVIEW, CatalogPipeline.FAST, Instant.parse("2026-10-15T09:14:00Z"), "mara.k",
                CatalogAddedByKind.USER, VerificationStatus.NEEDS_REVIEW, "medium", null, 2L, List.of());
        doReturn(detail).when(catalogService).detail(SONG_ID);

        mockMvc.perform(get(DETAIL_PATH, SONG_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.songId").value(SONG_ID))
                .andExpect(jsonPath("$.status").value(CatalogSongStatus.NEEDS_REVIEW.name()))
                .andExpect(jsonPath("$.pipeline").value(CatalogPipeline.FAST.name()))
                .andExpect(jsonPath("$.openReportCount").value(2));
    }

    @Test
    void aNormalUserIsForbiddenFromBothEndpoints() throws Exception {
        authenticateWithRole(Role.USER);

        mockMvc.perform(get(CATALOG_PATH)).andExpect(status().isForbidden());
        mockMvc.perform(get(DETAIL_PATH, SONG_ID)).andExpect(status().isForbidden());

        verify(catalogRepository, never()).countByStatus(any());
        verify(catalogService, never()).detail(any());
    }

    @Test
    void aTwoCharacterQueryIsABadRequestWithTheStandardErrorBody() throws Exception {
        authenticateWithRole(Role.ADMIN);

        mockMvc.perform(get(CATALOG_PATH).param("query", " ab "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()))
                .andExpect(jsonPath("$.message").exists());

        verify(catalogRepository, never()).countByStatus(any());
    }

    @Test
    void anOversizePageIsABadRequest() throws Exception {
        authenticateWithRole(Role.ADMIN);

        mockMvc.perform(get(CATALOG_PATH).param("size", String.valueOf(AdminSongCatalogService.MAXIMUM_PAGE_SIZE + 1)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(CATALOG_PATH).param("size", String.valueOf(AdminSongCatalogService.MINIMUM_PAGE_SIZE - 1)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aNegativePageIsABadRequest() throws Exception {
        authenticateWithRole(Role.ADMIN);

        mockMvc.perform(get(CATALOG_PATH).param("page", String.valueOf(AdminSongCatalogService.FIRST_PAGE - 1)))
                .andExpect(status().isBadRequest());
    }
}
