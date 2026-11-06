package org.dariusturcu.backend.security;

import org.dariusturcu.backend.config.SecurityConfig;
import org.dariusturcu.backend.model.user.Role;
import org.dariusturcu.backend.model.user.User;
import org.dariusturcu.backend.repository.UserRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.pattern.PathPattern;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class EndpointAuthorizationMatrixTest {

    private static final String TRANSACTIONAL_DATABASE_IMAGE = "pgvector/pgvector:pg18";
    private static final String ANALYTICS_DATABASE_IMAGE = "postgres:18";
    private static final String PLACEHOLDER_JWT_SECRET = "matrix-test-signing-secret-of-sufficient-length";
    private static final String PLACEHOLDER_INTERNAL_API_KEY = "matrix-test-internal-api-key";
    private static final String PLACEHOLDER_OAUTH_CLIENT_ID = "matrix-test-oauth-client-id";
    private static final String PLACEHOLDER_OAUTH_CLIENT_SECRET = "matrix-test-oauth-client-secret";
    private static final String PLACEHOLDER_FRONTEND_URL = "http://localhost:3000";
    private static final int MINIMUM_EXPECTED_AUTHENTICATED_ROUTES = 30;
    private static final int MINIMUM_EXPECTED_ADMIN_ROUTES = 10;
    private static final String SAMPLE_PATH_VARIABLE_REPLACEMENT = "1";

    private static final Set<String> INTENDED_PUBLIC_ALLOW_LIST = Set.of(
            "/auth/**",
            "/oauth2/**",
            "/login/oauth2/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**",
            "/v3/api-docs",
            "/api/enums/**",
            "/api/playlists/invites/*/preview",
            "/api/groups/invites/*/preview",
            "/api/users/*/avatar",
            "/api/ground-truth/**",
            "/actuator/health/**",
            "/health",
            "/error"
    );

    @Container
    static final PostgreSQLContainer<?> transactionalDatabase =
            new PostgreSQLContainer<>(TRANSACTIONAL_DATABASE_IMAGE);

    @Container
    static final PostgreSQLContainer<?> analyticsDatabase =
            new PostgreSQLContainer<>(ANALYTICS_DATABASE_IMAGE);

    @DynamicPropertySource
    static void registerContextProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", transactionalDatabase::getJdbcUrl);
        registry.add("spring.datasource.username", transactionalDatabase::getUsername);
        registry.add("spring.datasource.password", transactionalDatabase::getPassword);
        registry.add("spring.flyway.enabled", () -> false);
        registry.add("analytics.datasource.url", analyticsDatabase::getJdbcUrl);
        registry.add("analytics.datasource.username", analyticsDatabase::getUsername);
        registry.add("analytics.datasource.password", analyticsDatabase::getPassword);
        registry.add("jwt.secret", () -> PLACEHOLDER_JWT_SECRET);
        registry.add("ai.service.internal-api-key", () -> PLACEHOLDER_INTERNAL_API_KEY);
        registry.add("spring.security.oauth2.client.registration.google.client-id", () -> PLACEHOLDER_OAUTH_CLIENT_ID);
        registry.add("spring.security.oauth2.client.registration.google.client-secret", () -> PLACEHOLDER_OAUTH_CLIENT_SECRET);
        registry.add("app.oauth2.redirect-uri", () -> PLACEHOLDER_FRONTEND_URL + "/oauth2/redirect");
        registry.add("frontend.url", () -> PLACEHOLDER_FRONTEND_URL);
    }

    @BeforeAll
    static void migrateTransactionalSchema() {
        Flyway.configure()
                .dataSource(
                        transactionalDatabase.getJdbcUrl(),
                        transactionalDatabase.getUsername(),
                        transactionalDatabase.getPassword())
                .load()
                .migrate();
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RequestMappingHandlerMapping handlerMapping;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private org.dariusturcu.backend.ratelimit.RateLimitingFilter rateLimitingFilter;

    @Test
    void publicAllowListMatchesExactIntendedList() {
        assertThat(SecurityConfig.PUBLIC_ALLOW_LIST)
                .containsExactlyInAnyOrderElementsOf(INTENDED_PUBLIC_ALLOW_LIST);
    }

    @Test
    void everyMappingOutsidePublicAllowListAnswersUnauthorizedWithoutSession() throws Exception {
        AntPathMatcher pathMatcher = new AntPathMatcher();
        Map<RequestMappingInfo, HandlerMethod> handlerMethods = handlerMapping.getHandlerMethods();
        assertThat(handlerMethods).isNotEmpty();

        int checkedRouteCount = 0;

        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMethods.entrySet()) {
            RequestMappingInfo mappingInfo = entry.getKey();
            Set<String> patterns = extractPatterns(mappingInfo);
            Set<RequestMethod> methods = extractMethods(mappingInfo);

            for (String pattern : patterns) {
                String samplePath = pattern.replaceAll("\\{[^}]+\\}", SAMPLE_PATH_VARIABLE_REPLACEMENT);

                boolean isExplicitlyAuthenticated = SecurityConfig.AUTHENTICATED_AUTH_ENDPOINTS.stream()
                        .anyMatch(authPattern -> pathMatcher.match(authPattern, samplePath));

                boolean isPublic = !isExplicitlyAuthenticated && SecurityConfig.PUBLIC_ALLOW_LIST.stream()
                        .anyMatch(publicPattern -> pathMatcher.match(publicPattern, samplePath));

                if (!isPublic) {
                    for (RequestMethod method : methods) {
                        rateLimitingFilter.resetForTesting();
                        final int currentCount = checkedRouteCount;
                        MockHttpServletRequestBuilder requestBuilder = toRequestBuilder(method, samplePath)
                                .with(csrf())
                                .with(request -> {
                                    request.setRemoteAddr("10.0." + (currentCount / 200) + "." + (currentCount % 200 + 1));
                                    return request;
                                });

                        mockMvc.perform(requestBuilder)
                                .andExpect(status().isUnauthorized());
                        checkedRouteCount++;
                    }
                }
            }
        }

        assertThat(checkedRouteCount).isGreaterThan(MINIMUM_EXPECTED_AUTHENTICATED_ROUTES);
    }

    @Test
    void everyAdminMappingAnswersForbiddenForNormalUser() throws Exception {
        User normalUser = persistUser("normal-matrix-tester", Role.USER);
        Authentication normalAuth = authFor(normalUser);

        Map<RequestMappingInfo, HandlerMethod> handlerMethods = handlerMapping.getHandlerMethods();
        int checkedAdminRoutes = 0;

        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMethods.entrySet()) {
            RequestMappingInfo mappingInfo = entry.getKey();
            Set<String> patterns = extractPatterns(mappingInfo);
            Set<RequestMethod> methods = extractMethods(mappingInfo);

            for (String pattern : patterns) {
                if (pattern.startsWith("/api/admin")) {
                    String samplePath = pattern.replaceAll("\\{[^}]+\\}", SAMPLE_PATH_VARIABLE_REPLACEMENT);
                    for (RequestMethod method : methods) {
                        MockHttpServletRequestBuilder requestBuilder = toRequestBuilder(method, samplePath)
                                .with(csrf())
                                .with(SecurityMockMvcRequestPostProcessors.authentication(normalAuth));

                        mockMvc.perform(requestBuilder)
                                .andExpect(status().isForbidden());
                        checkedAdminRoutes++;
                    }
                }
            }
        }

        assertThat(checkedAdminRoutes).isGreaterThan(MINIMUM_EXPECTED_ADMIN_ROUTES);
    }

    @Test
    void ownershipProtectionsPreventCrossUserAccess() throws Exception {
        User ownerUser = persistUser("resource-owner-user", Role.USER);
        User intruderUser = persistUser("resource-intruder-user", Role.USER);

        Authentication ownerAuth = authFor(ownerUser);
        Authentication intruderAuth = authFor(intruderUser);

        // 1. Playlists ownership check
        MvcResult playlistCreateResult = mockMvc.perform(post("/api/users/me/playlists")
                        .with(csrf())
                        .with(SecurityMockMvcRequestPostProcessors.authentication(ownerAuth)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode playlistJson = objectMapper.readTree(playlistCreateResult.getResponse().getContentAsString());
        long playlistId = playlistJson.get("id").asLong();

        // User A cannot read User B's playlist
        mockMvc.perform(get("/api/playlists/{playlistId}", playlistId)
                        .with(csrf())
                        .with(SecurityMockMvcRequestPostProcessors.authentication(intruderAuth)))
                .andExpect(status().isForbidden());

        // User A cannot update User B's playlist
        mockMvc.perform(patch("/api/playlists/{playlistId}", playlistId)
                        .with(csrf())
                        .with(SecurityMockMvcRequestPostProcessors.authentication(intruderAuth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Tampered Playlist\"}"))
                .andExpect(status().isForbidden());

        // User A cannot delete User B's playlist
        mockMvc.perform(delete("/api/playlists/{playlistId}", playlistId)
                        .with(csrf())
                        .with(SecurityMockMvcRequestPostProcessors.authentication(intruderAuth)))
                .andExpect(status().isForbidden());

        // User A cannot add a song to User B's playlist
        mockMvc.perform(post("/api/playlists/{playlistId}/songs", playlistId)
                        .with(csrf())
                        .with(SecurityMockMvcRequestPostProcessors.authentication(intruderAuth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Title\",\"artist\":\"Artist\",\"releaseYear\":2000,\"youtubeId\":\"dQw4w9WgXcQ\",\"color\":\"123456\"}"))
                .andExpect(status().isForbidden());

        // User A cannot delete a song from User B's playlist
        mockMvc.perform(delete("/api/playlists/{playlistId}/songs/999", playlistId)
                        .with(csrf())
                        .with(SecurityMockMvcRequestPostProcessors.authentication(intruderAuth)))
                .andExpect(status().isForbidden());

        // User A cannot export User B's playlist info PDF
        mockMvc.perform(get("/api/playlists/{playlistId}/export/info", playlistId)
                        .with(csrf())
                        .with(SecurityMockMvcRequestPostProcessors.authentication(intruderAuth)))
                .andExpect(status().isForbidden());

        // User A cannot export User B's playlist QR PDF
        mockMvc.perform(get("/api/playlists/{playlistId}/export/qr", playlistId)
                        .with(csrf())
                        .with(SecurityMockMvcRequestPostProcessors.authentication(intruderAuth)))
                .andExpect(status().isForbidden());

        // User A cannot export User B's playlist duplex PDF
        mockMvc.perform(get("/api/playlists/{playlistId}/export/duplex", playlistId)
                        .with(csrf())
                        .with(SecurityMockMvcRequestPostProcessors.authentication(intruderAuth)))
                .andExpect(status().isForbidden());

        // User A cannot upload a cover image for User B's playlist
        MockMultipartFile fakeCover = new MockMultipartFile(
                "cover", "cover.png", MediaType.IMAGE_PNG_VALUE, new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47});
        mockMvc.perform(multipart(HttpMethod.PUT, "/api/playlists/{playlistId}/cover", playlistId)
                        .file(fakeCover)
                        .with(csrf())
                        .with(SecurityMockMvcRequestPostProcessors.authentication(intruderAuth)))
                .andExpect(status().isForbidden());

        // 2. Groups ownership check
        MvcResult groupCreateResult = mockMvc.perform(post("/api/groups")
                        .with(csrf())
                        .with(SecurityMockMvcRequestPostProcessors.authentication(ownerAuth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Owner Host\"}"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode groupJson = objectMapper.readTree(groupCreateResult.getResponse().getContentAsString());
        long groupId = groupJson.get("id").asLong();

        // User A cannot read User B's group details
        mockMvc.perform(get("/api/groups/{groupId}", groupId)
                        .with(csrf())
                        .with(SecurityMockMvcRequestPostProcessors.authentication(intruderAuth)))
                .andExpect(status().isForbidden());

        // User A cannot update User B's group settings
        mockMvc.perform(patch("/api/groups/{groupId}/settings", groupId)
                        .with(csrf())
                        .with(SecurityMockMvcRequestPostProcessors.authentication(intruderAuth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"winConditionCardCount\":5}"))
                .andExpect(status().isForbidden());

        // User A cannot start session on User B's group
        mockMvc.perform(post("/api/groups/{groupId}/start-session", groupId)
                        .with(csrf())
                        .with(SecurityMockMvcRequestPostProcessors.authentication(intruderAuth)))
                .andExpect(status().isForbidden());

        // 3. Game sessions ownership check
        mockMvc.perform(get("/api/sessions/{sessionId}", 999999L)
                        .with(csrf())
                        .with(SecurityMockMvcRequestPostProcessors.authentication(intruderAuth)))
                .andExpect(status().isNotFound());

        // 4. User profile and GDPR export ownership check
        // User A cannot read User B's private account record
        mockMvc.perform(get("/api/users/{userId}", ownerUser.getId())
                        .with(csrf())
                        .with(SecurityMockMvcRequestPostProcessors.authentication(intruderAuth)))
                .andExpect(status().isForbidden());

        // User A calling personal data export gets User A's data, never User B's data
        mockMvc.perform(get("/api/users/me/export")
                        .with(csrf())
                        .with(SecurityMockMvcRequestPostProcessors.authentication(intruderAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(intruderUser.getUsername()))
                .andExpect(jsonPath("$.email").value(intruderUser.getEmail()));
    }

    private User persistUser(String username, Role role) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setPassword("securePassword123!");
        user.setRole(role);
        user.setEmailVerified(true);
        user.setCreatedAt(Instant.now());
        return userRepository.save(user);
    }

    private Authentication authFor(User user) {
        UserPrincipal principal = new UserPrincipal(user);
        return new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());
    }

    private Set<String> extractPatterns(RequestMappingInfo mappingInfo) {
        if (mappingInfo.getPathPatternsCondition() != null) {
            return mappingInfo.getPathPatternsCondition().getPatterns().stream()
                    .map(PathPattern::getPatternString)
                    .collect(Collectors.toSet());
        }
        return Set.of();
    }

    private Set<RequestMethod> extractMethods(RequestMappingInfo mappingInfo) {
        Set<RequestMethod> methods = mappingInfo.getMethodsCondition().getMethods();
        if (methods.isEmpty()) {
            return Set.of(RequestMethod.GET);
        }
        return methods;
    }

    private MockHttpServletRequestBuilder toRequestBuilder(RequestMethod method, String path) {
        return switch (method) {
            case GET -> MockMvcRequestBuilders.get(path);
            case POST -> MockMvcRequestBuilders.post(path);
            case PUT -> MockMvcRequestBuilders.put(path);
            case PATCH -> MockMvcRequestBuilders.patch(path);
            case DELETE -> MockMvcRequestBuilders.delete(path);
            case HEAD -> MockMvcRequestBuilders.head(path);
            case OPTIONS -> MockMvcRequestBuilders.options(path);
            case TRACE -> MockMvcRequestBuilders.request(HttpMethod.TRACE, path);
            default -> MockMvcRequestBuilders.get(path);
        };
    }
}
