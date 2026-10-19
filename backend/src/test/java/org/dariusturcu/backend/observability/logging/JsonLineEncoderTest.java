package org.dariusturcu.backend.observability.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import org.dariusturcu.backend.observability.CorrelationIdContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

class JsonLineEncoderTest {

    private final JsonLineEncoder encoder = new JsonLineEncoder();

    @AfterEach
    void clearMdc() {
        CorrelationIdContext.clear();
    }

    @Test
    void encodesTheCoreFieldsAsOneJsonLine() {
        LoggingEvent event = new LoggingEvent();
        event.setLoggerName("org.dariusturcu.backend.SomeClass");
        event.setLevel(Level.INFO);
        event.setThreadName("main");
        event.setMessage("something happened");
        event.setLoggerContext((LoggerContext) LoggerFactory.getILoggerFactory());

        String line = new String(encoder.encode(event));

        assertThat(line).contains("\"level\":\"INFO\"");
        assertThat(line).contains("\"logger\":\"org.dariusturcu.backend.SomeClass\"");
        assertThat(line).contains("\"message\":\"something happened\"");
        assertThat(line.trim()).startsWith("{").endsWith("}");
    }

    @Test
    void includesTheCorrelationIdWhenOneIsSetOnTheCurrentThread() {
        CorrelationIdContext.setCurrentRequestId("test-request-id");

        LoggingEvent event = new LoggingEvent();
        event.setLoggerName("org.dariusturcu.backend.SomeClass");
        event.setLevel(Level.INFO);
        event.setThreadName("main");
        event.setMessage("something happened");
        event.setLoggerContext((LoggerContext) LoggerFactory.getILoggerFactory());

        String line = new String(encoder.encode(event));

        assertThat(line).contains("\"requestId\":\"test-request-id\"");
    }

    @Test
    void escapesQuotesAndNewlinesInTheMessage() {
        LoggingEvent event = new LoggingEvent();
        event.setLoggerName("org.dariusturcu.backend.SomeClass");
        event.setLevel(Level.WARN);
        event.setThreadName("main");
        event.setMessage("a \"quoted\" message\nwith a newline");
        event.setLoggerContext((LoggerContext) LoggerFactory.getILoggerFactory());

        String line = new String(encoder.encode(event));

        assertThat(line).contains("a \\\"quoted\\\" message\\nwith a newline");
    }
}
