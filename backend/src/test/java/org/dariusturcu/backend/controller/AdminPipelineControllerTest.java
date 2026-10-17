package org.dariusturcu.backend.controller;

import org.dariusturcu.backend.exception.GlobalExceptionHandler;
import org.dariusturcu.backend.model.admin.PipelineControlResultDTO;
import org.dariusturcu.backend.model.song.EnqueueResultDTO;
import org.dariusturcu.backend.model.song.PendingImportOrigin;
import org.dariusturcu.backend.model.user.Role;
import org.dariusturcu.backend.model.user.User;
import org.dariusturcu.backend.pipeline.*;
import org.dariusturcu.backend.security.AdminAccessGuard;
import org.dariusturcu.backend.security.UserPrincipal;
import org.dariusturcu.backend.service.ApiUsageService;
import org.dariusturcu.backend.service.CatalogSeedingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AdminPipelineControllerTest {
    private static final long RUN_ID = 12;
    private static final String VIDEO_ID = "dQw4w9WgXcQ";
    private static final PipelineControlResultDTO ONE_CHANGE = new PipelineControlResultDTO(1);
    private final PipelineJobService engine = mock(PipelineJobService.class);
    private final AdminPipelineRepository repository = mock(AdminPipelineRepository.class);
    private final CatalogSeedingService seeding = mock(CatalogSeedingService.class);
    private final AdminPipelineService pipeline = new AdminPipelineService(repository, engine,
            new PipelineSettings(PipelineSettings.DEFAULT_YOUTUBE_RESERVE_UNITS), mock(ApiUsageService.class), seeding);
    private final AdminSongPipelineService songs = spy(new AdminSongPipelineService(mock(org.springframework.jdbc.core.JdbcTemplate.class),
            mock(PipelineJobRepository.class), engine, pipeline, JsonMapper.builder().build()));
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new AdminPipelineController(pipeline, new AdminAccessGuard()),
                    new AdminSongPipelineController(songs, new AdminAccessGuard()))
            .setControllerAdvice(new AdminPipelineValidationAdvice(), new GlobalExceptionHandler()).build();

    record Endpoint(HttpMethod method, String path, String body) { }

    static Stream<Endpoint> endpoints() {
        return Stream.of(
                new Endpoint(HttpMethod.GET, "/api/admin/queues", null),
                new Endpoint(HttpMethod.GET, "/api/admin/queues/musicbrainz/jobs", null),
                new Endpoint(HttpMethod.GET, "/api/admin/runs", null),
                new Endpoint(HttpMethod.GET, "/api/admin/batches", null),
                new Endpoint(HttpMethod.POST, "/api/admin/queues/musicbrainz/pause", null),
                new Endpoint(HttpMethod.POST, "/api/admin/queues/musicbrainz/resume", null),
                new Endpoint(HttpMethod.POST, "/api/admin/pipeline/pause", null),
                new Endpoint(HttpMethod.POST, "/api/admin/pipeline/resume", null),
                new Endpoint(HttpMethod.POST, "/api/admin/runs/12/retry", null),
                new Endpoint(HttpMethod.POST, "/api/admin/runs/12/discard", null),
                new Endpoint(HttpMethod.POST, "/api/admin/runs/12/bump", null),
                new Endpoint(HttpMethod.POST, "/api/admin/queues/retry-failed", null),
                new Endpoint(HttpMethod.POST, "/api/admin/batches/12/cancel", null),
                new Endpoint(HttpMethod.POST, "/api/admin/pipeline/enqueue", "{\"youtubeIds\":[\"dQw4w9WgXcQ\"]}"),
                new Endpoint(HttpMethod.POST, "/api/admin/pipeline/run-now", "{\"youtubeId\":\"dQw4w9WgXcQ\"}"),
                new Endpoint(HttpMethod.POST, "/api/admin/songs/12/rerun", null),
                new Endpoint(HttpMethod.PUT, "/api/admin/songs/12", "{\"releaseYear\":1999}"),
                new Endpoint(HttpMethod.DELETE, "/api/admin/songs/12", null));
    }

    private void authenticate(Role role) {
        User user = new User(); user.setUsername("pipeline-test"); user.setRole(role);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new UserPrincipal(user), null, null));
    }

    @AfterEach
    void clearAuthentication() { SecurityContextHolder.clearContext(); }

    @ParameterizedTest
    @MethodSource("endpoints")
    void everyPipelineEndpointRequiresAdmin(Endpoint endpoint) throws Exception {
        authenticate(Role.USER);
        var request = request(endpoint.method(), endpoint.path());
        if (endpoint.body() != null) { request.contentType(MediaType.APPLICATION_JSON).content(endpoint.body()); }
        mvc.perform(request).andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
        verifyNoInteractions(engine, repository, seeding);
    }

    @ParameterizedTest
    @MethodSource("endpoints")
    void anAdminCanCallEveryPipelineEndpoint(Endpoint endpoint) throws Exception {
        authenticate(Role.ADMIN);
        PipelineRun run = new PipelineRun(RUN_ID, VIDEO_ID, null, PipelineLane.BACKFILL, PipelineRunStatus.FAILED,
                PendingImportOrigin.ADMIN_SEED, null, Instant.now(), null, null);
        when(engine.findRun(RUN_ID)).thenReturn(Optional.of(run));
        when(engine.startRun(anyString(), any(), any(), any(), any())).thenReturn(run);
        when(engine.retryFailed(RUN_ID)).thenReturn(true);
        when(engine.discardRun(RUN_ID)).thenReturn(true);
        when(engine.bump(RUN_ID)).thenReturn(1);
        when(repository.batchExists(RUN_ID)).thenReturn(true);
        when(seeding.enqueue(any(org.dariusturcu.backend.model.song.AdminCatalogSeedingRequest.class)))
                .thenReturn(new EnqueueResultDTO(Set.of(VIDEO_ID), Set.of(), Set.of(), RUN_ID));
        doReturn(pipeline.run(run)).when(songs).rerun(anyLong());
        doReturn(ONE_CHANGE).when(songs).update(anyLong(), any());
        doReturn(ONE_CHANGE).when(songs).delete(anyLong());
        var request = request(endpoint.method(), endpoint.path());
        if (endpoint.body() != null) { request.contentType(MediaType.APPLICATION_JSON).content(endpoint.body()); }
        mvc.perform(request).andExpect(status().isOk());
    }

    @Test
    void invalidStageStatusPageAndEnumsUseStandardBadRequestBodies() throws Exception {
        authenticate(Role.ADMIN);
        for (String path : List.of("/api/admin/queues/unknown/jobs", "/api/admin/queues/musicbrainz/jobs?status=unknown",
                "/api/admin/queues/musicbrainz/jobs?page=-1", "/api/admin/runs?status=unknown", "/api/admin/runs?lane=unknown", "/api/admin/runs?state=unknown",
                "/api/admin/batches?page=-1", "/api/admin/runs?page=bad")) {
            mvc.perform(get(path)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.message").exists()).andExpect(jsonPath("$.timestamp").exists());
        }
    }

    @Test
    void invalidBodiesUseStandardBadRequestBodies() throws Exception {
        authenticate(Role.ADMIN);
        mvc.perform(post("/api/admin/pipeline/enqueue").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        mvc.perform(post("/api/admin/pipeline/enqueue").contentType(MediaType.APPLICATION_JSON).content("{\"youtubeIds\":[\"bad\"]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        mvc.perform(post("/api/admin/pipeline/run-now").contentType(MediaType.APPLICATION_JSON).content("{\"youtubeId\":\"bad\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        mvc.perform(put("/api/admin/songs/12").contentType(MediaType.APPLICATION_JSON).content("{\"releaseYear\":999}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        mvc.perform(put("/api/admin/songs/12").contentType(MediaType.APPLICATION_JSON).content("{\"verificationStatus\":\"bad\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
    }
}
