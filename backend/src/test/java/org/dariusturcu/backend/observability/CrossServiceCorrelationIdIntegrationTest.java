package org.dariusturcu.backend.observability;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Proves the correlation-id filter and the RestClient interceptor work together
 * on a core-service-to-AI-service call: a request id set on the incoming request
 * is carried by the filter into MDC and the current OpenTelemetry span, then
 * forwarded onto the outgoing AI-service call by CorrelationIdPropagatingInterceptor,
 * appears in the backend's own logs, and correlates with a single trace.
 *
 * The AI-service side of the same propagation (reusing an incoming X-Request-Id,
 * echoing it back, and logging it) is verified independently in the AI
 * microservice's own test suite (tests/observability/test_request_context.py),
 * since this sandbox's JDK cannot open real loopback sockets between processes
 * (the same limitation BackendApplicationTests is excluded for), so a literal
 * cross-process HTTP call between the two real running services can't execute
 * here. MockRestServiceServer stands in for the AI service's HTTP boundary,
 * exercising the real RestClient bean, the real interceptor, and the real
 * filter, everything short of the actual second process.
 */
class CrossServiceCorrelationIdIntegrationTest {

    private InMemorySpanExporter spanExporter;
    private SdkTracerProvider tracerProvider;
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void setUpTracingAndLogCapture() {
        spanExporter = InMemorySpanExporter.create();
        tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                .build();

        Logger rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        logAppender = new ListAppender<>();
        logAppender.start();
        rootLogger.addAppender(logAppender);
    }

    @AfterEach
    void tearDownTracingAndLogCapture() {
        Logger rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        rootLogger.detachAppender(logAppender);
        tracerProvider.close();
        CorrelationIdContext.clear();
    }

    @Test
    void aRequestIdPropagatesOntoTheAiServiceCallAndCorrelatesWithASingleTrace() throws Exception {
        String incomingRequestId = "cross-service-test-" + UUID.randomUUID();

        RestClient.Builder restClientBuilder = RestClient.builder()
                .baseUrl("http://ai-service.test")
                .requestInterceptor(new CorrelationIdPropagatingInterceptor());
        MockRestServiceServer mockAiService = MockRestServiceServer.bindTo(restClientBuilder).build();
        RestClient restClient = restClientBuilder.build();

        mockAiService.expect(requestTo("http://ai-service.test/health"))
                .andExpect(header(CorrelationIdContext.HEADER_NAME, incomingRequestId))
                .andRespond(withSuccess("{\"status\":\"ok\"}", MediaType.APPLICATION_JSON)
                        .header(CorrelationIdContext.HEADER_NAME, incomingRequestId));

        var tracer = tracerProvider.get("cross-service-correlation-test");
        Span span = tracer.spanBuilder("incoming-request").startSpan();

        org.slf4j.Logger testLogger = LoggerFactory.getLogger(CrossServiceCorrelationIdIntegrationTest.class);
        try (var scope = span.makeCurrent()) {
            HttpServletRequest request = mock(HttpServletRequest.class);
            HttpServletResponse response = mock(HttpServletResponse.class);
            when(request.getHeader(CorrelationIdContext.HEADER_NAME)).thenReturn(incomingRequestId);

            CorrelationIdFilter filter = new CorrelationIdFilter();
            filter.doFilter(request, response, (filteredRequest, filteredResponse) -> {
                testLogger.info("Requesting metadata resolution from AI microservice");
                restClient.get().uri("/health").retrieve().toEntity(String.class);
            });
        } finally {
            span.end();
        }

        mockAiService.verify();

        List<SpanData> recordedSpans = spanExporter.getFinishedSpanItems();
        assertThat(recordedSpans).hasSize(1);
        SpanData incomingRequestSpan = recordedSpans.get(0);
        assertThat(incomingRequestSpan.getAttributes().get(AttributeKey.stringKey("request.id")))
                .isEqualTo(incomingRequestId);

        List<ILoggingEvent> backendLogEvents = logAppender.list;
        boolean backendLoggedTheRequestId = backendLogEvents.stream()
                .anyMatch(event -> incomingRequestId.equals(event.getMDCPropertyMap().get(CorrelationIdContext.MDC_KEY)));
        assertThat(backendLoggedTheRequestId).isTrue();
    }
}
