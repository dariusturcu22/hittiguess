package org.dariusturcu.backend.observability.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.encoder.EncoderBase;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import org.dariusturcu.backend.observability.CorrelationIdContext;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Renders each log line as a single-line JSON object: timestamp, level, logger, thread,
 * message, the request's correlation id, and the current OpenTelemetry trace/span id when
 * one exists. This shape is what a Loki/Promtail pipeline expects to scrape, without this
 * service needing a live Loki endpoint to produce it or to be tested.
 */
public class JsonLineEncoder extends EncoderBase<ILoggingEvent> {

    @Override
    public byte[] headerBytes() {
        return new byte[0];
    }

    @Override
    public byte[] encode(ILoggingEvent event) {
        StringBuilder json = new StringBuilder(256);
        json.append('{');
        appendField(json, "timestamp", DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(event.getTimeStamp())), true);
        appendField(json, "level", event.getLevel().toString(), false);
        appendField(json, "logger", event.getLoggerName(), false);
        appendField(json, "thread", event.getThreadName(), false);
        appendField(json, "message", event.getFormattedMessage(), false);

        String requestId = CorrelationIdContext.getCurrentRequestId();
        if (requestId != null) {
            appendField(json, "requestId", requestId, false);
        }

        SpanContext spanContext = Span.current().getSpanContext();
        if (spanContext.isValid()) {
            appendField(json, "traceId", spanContext.getTraceId(), false);
            appendField(json, "spanId", spanContext.getSpanId(), false);
        }

        for (Map.Entry<String, String> mdcEntry : event.getMDCPropertyMap().entrySet()) {
            if (!CorrelationIdContext.MDC_KEY.equals(mdcEntry.getKey())) {
                appendField(json, mdcEntry.getKey(), mdcEntry.getValue(), false);
            }
        }

        IThrowableProxy throwableProxy = event.getThrowableProxy();
        if (throwableProxy != null) {
            appendField(json, "exception", ThrowableProxyUtil.asString(throwableProxy), false);
        }

        json.append('}').append(System.lineSeparator());
        return json.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public byte[] footerBytes() {
        return new byte[0];
    }

    private void appendField(StringBuilder json, String name, String value, boolean isFirstField) {
        if (!isFirstField) {
            json.append(',');
        }
        json.append('"').append(escape(name)).append("\":\"").append(escape(value)).append('"');
    }

    private String escape(String rawValue) {
        if (rawValue == null) {
            return "";
        }
        StringBuilder escaped = new StringBuilder(rawValue.length());
        for (int characterIndex = 0; characterIndex < rawValue.length(); characterIndex++) {
            char character = rawValue.charAt(characterIndex);
            switch (character) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (character < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) character));
                    } else {
                        escaped.append(character);
                    }
                }
            }
        }
        return escaped.toString();
    }
}
