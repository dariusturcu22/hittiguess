package org.dariusturcu.backend.service;

import org.dariusturcu.backend.model.ai.AiResponse;
import org.dariusturcu.backend.model.ai.AiServiceResolveResponse;
import org.dariusturcu.backend.model.user.Role;
import org.dariusturcu.backend.model.user.User;
import org.dariusturcu.backend.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SongMetadataServiceTest {

    @Mock
    private RestClient aiServiceRestClient;
    @Mock
    private RestClient.RequestBodyUriSpec requestBodyUriSpec;
    @Mock
    private RestClient.RequestBodySpec requestBodySpec;
    @Mock
    private RestClient.ResponseSpec responseSpec;

    private SongMetadataService songMetadataService;

    private static final Long USER_ID = 1L;
    private static final String YOUTUBE_URL = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";

    @BeforeEach
    void setUp() {
        songMetadataService = new SongMetadataService(aiServiceRestClient);
        authenticateAs(USER_ID);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(Long userId) {
        User user = new User();
        user.setId(userId);
        user.setRole(Role.USER);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new UserPrincipal(user), null, null));
    }

    private void stubSuccessfulAiCall() {
        when(aiServiceRestClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri("/metadata/resolve")).thenReturn(requestBodySpec);
        when(requestBodySpec.body(org.mockito.ArgumentMatchers.any(Object.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(AiServiceResolveResponse.class))
                .thenReturn(new AiServiceResolveResponse("SUCCESS", "some-model", null));
    }

    @Test
    void fetchMetadataReturnsSuccessForANormalCall() {
        stubSuccessfulAiCall();

        AiResponse response = songMetadataService.fetchMetadata(YOUTUBE_URL);

        assertThat(response.status()).isEqualTo("SUCCESS");
    }

    @Test
    void fetchMetadataReturnsErrorWhenTheAiServiceThrows() {
        when(aiServiceRestClient.post()).thenThrow(new RuntimeException("connection refused"));

        AiResponse response = songMetadataService.fetchMetadata(YOUTUBE_URL);

        assertThat(response.status()).isEqualTo("ERROR");
    }

    @Test
    void fetchMetadataAllowsASecondRequestOnceTheFirstOneHasFinished() {
        stubSuccessfulAiCall();

        songMetadataService.fetchMetadata(YOUTUBE_URL);
        AiResponse secondResponse = songMetadataService.fetchMetadata(YOUTUBE_URL);

        assertThat(secondResponse.status()).isEqualTo("SUCCESS");
    }

    @Test
    void fetchMetadataRejectsAConcurrentSecondRequestFromTheSameUser() throws InterruptedException {
        CountDownLatch firstCallStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstCall = new CountDownLatch(1);

        RestClient.RequestBodyUriSpec blockingUriSpec = mock(RestClient.RequestBodyUriSpec.class);
        when(aiServiceRestClient.post()).thenAnswer(invocation -> {
            firstCallStarted.countDown();
            releaseFirstCall.await(5, TimeUnit.SECONDS);
            return blockingUriSpec;
        });
        when(blockingUriSpec.uri("/metadata/resolve")).thenThrow(new RuntimeException("simulated failure after unblocking"));

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            executor.submit(() -> {
                authenticateAs(USER_ID);
                songMetadataService.fetchMetadata(YOUTUBE_URL);
            });

            assertThat(firstCallStarted.await(5, TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(() -> songMetadataService.fetchMetadata(YOUTUBE_URL))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("A metadata request is already in progress");
        } finally {
            releaseFirstCall.countDown();
            executor.shutdown();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void fetchMetadataAllowsConcurrentRequestsFromDifferentUsers() throws InterruptedException {
        CountDownLatch firstCallStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstCall = new CountDownLatch(1);
        AtomicInteger secondUserFailureCount = new AtomicInteger(0);

        RestClient.RequestBodyUriSpec blockingUriSpec = mock(RestClient.RequestBodyUriSpec.class);
        when(aiServiceRestClient.post()).thenAnswer(invocation -> {
            firstCallStarted.countDown();
            releaseFirstCall.await(5, TimeUnit.SECONDS);
            return blockingUriSpec;
        });
        when(blockingUriSpec.uri("/metadata/resolve")).thenThrow(new RuntimeException("simulated failure after unblocking"));

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            executor.submit(() -> {
                authenticateAs(USER_ID);
                songMetadataService.fetchMetadata(YOUTUBE_URL);
            });
            assertThat(firstCallStarted.await(5, TimeUnit.SECONDS)).isTrue();

            authenticateAs(2L);
            try {
                songMetadataService.fetchMetadata(YOUTUBE_URL);
            } catch (ResponseStatusException tooManyRequests) {
                secondUserFailureCount.incrementAndGet();
            }

            assertThat(secondUserFailureCount.get()).isZero();
        } finally {
            releaseFirstCall.countDown();
            executor.shutdown();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }
}
