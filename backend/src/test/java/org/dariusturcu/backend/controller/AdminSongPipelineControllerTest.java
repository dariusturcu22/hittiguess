package org.dariusturcu.backend.controller;

import org.dariusturcu.backend.exception.GlobalExceptionHandler;
import org.dariusturcu.backend.model.admin.PipelineControlResultDTO;
import org.dariusturcu.backend.model.admin.ProposalBatchRequest;
import org.dariusturcu.backend.model.admin.ProposalBatchResultDTO;
import org.dariusturcu.backend.model.admin.RecheckSongsRequest;
import org.dariusturcu.backend.model.user.Role;
import org.dariusturcu.backend.model.user.User;
import org.dariusturcu.backend.pipeline.AdminSongPipelineService;
import org.dariusturcu.backend.security.AdminAccessGuard;
import org.dariusturcu.backend.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminSongPipelineControllerTest {
    private static final String RECHECK_PATH = "/api/admin/songs/recheck";
    private static final String DISMISS_PROPOSAL_PATH = "/api/admin/songs/{id}/proposal";
    private static final long SONG_ID = 42;
    private static final String ACCEPT_PROPOSALS_PATH = "/api/admin/songs/proposals/accept";
    private static final String DISMISS_PROPOSALS_PATH = "/api/admin/songs/proposals/dismiss";
    private static final String PROPOSAL_BATCH_BODY = "{\"songIds\": [1, 2]}";

    private final AdminSongPipelineService songs = mock(AdminSongPipelineService.class);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new AdminSongPipelineController(songs, new AdminAccessGuard()))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(Role role) {
        User user = new User();
        user.setId(1L);
        user.setUsername("caller");
        user.setRole(role);
        UserPrincipal principal = new UserPrincipal(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @Test
    void recheckEndpointRejectsUnauthenticatedCalls() throws Exception {
        mockMvc.perform(post(RECHECK_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"all\": true}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void recheckEndpointRejectsNonAdminCalls() throws Exception {
        authenticate(Role.USER);
        mockMvc.perform(post(RECHECK_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"all\": true}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void recheckEndpointQueuesSongsForAdmin() throws Exception {
        authenticate(Role.ADMIN);
        when(songs.recheck(any())).thenReturn(new PipelineControlResultDTO(5));

        mockMvc.perform(post(RECHECK_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"songIds\": [1, 2, 3]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.affected").value(5));

        verify(songs).recheck(new RecheckSongsRequest(List.of(1L, 2L, 3L), null));
    }

    @Test
    void dismissProposalRejectsNonAdmin() throws Exception {
        authenticate(Role.USER);
        mockMvc.perform(delete(DISMISS_PROPOSAL_PATH, SONG_ID))
                .andExpect(status().isForbidden());
    }

    @Test
    void dismissProposalClearsProposalForAdmin() throws Exception {
        authenticate(Role.ADMIN);
        when(songs.dismissProposal(SONG_ID)).thenReturn(new PipelineControlResultDTO(1));

        mockMvc.perform(delete(DISMISS_PROPOSAL_PATH, SONG_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.affected").value(1));

        verify(songs).dismissProposal(SONG_ID);
    }

    @Test
    void acceptProposalsRejectsNonAdmin() throws Exception {
        authenticate(Role.USER);
        mockMvc.perform(post(ACCEPT_PROPOSALS_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PROPOSAL_BATCH_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void acceptProposalsRejectsUnauthenticatedCalls() throws Exception {
        mockMvc.perform(post(ACCEPT_PROPOSALS_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PROPOSAL_BATCH_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void acceptProposalsReportsSkippedSongsForAdmin() throws Exception {
        authenticate(Role.ADMIN);
        when(songs.acceptProposals(any())).thenReturn(new ProposalBatchResultDTO(1, List.of(2L)));

        mockMvc.perform(post(ACCEPT_PROPOSALS_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PROPOSAL_BATCH_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.affected").value(1))
                .andExpect(jsonPath("$.skippedSongIds[0]").value(2));

        verify(songs).acceptProposals(new ProposalBatchRequest(List.of(1L, 2L)));
    }

    @Test
    void dismissProposalsRejectsNonAdmin() throws Exception {
        authenticate(Role.USER);
        mockMvc.perform(post(DISMISS_PROPOSALS_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PROPOSAL_BATCH_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void dismissProposalsClearsProposalsForAdmin() throws Exception {
        authenticate(Role.ADMIN);
        when(songs.dismissProposals(any())).thenReturn(new ProposalBatchResultDTO(2, List.of()));

        mockMvc.perform(post(DISMISS_PROPOSALS_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PROPOSAL_BATCH_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.affected").value(2));

        verify(songs).dismissProposals(new ProposalBatchRequest(List.of(1L, 2L)));
    }
}
