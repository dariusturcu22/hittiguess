package org.dariusturcu.backend.observability;

import io.opentelemetry.api.trace.Span;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Assigns a correlation id to every request: reuses one supplied on the incoming
 * X-Request-Id header, or generates a new one otherwise. Stored in MDC for the
 * duration of the request so every log line emitted while handling it carries the
 * same id, attached to the current OpenTelemetry span so a trace and its logs can
 * be cross-referenced, and echoed back on the response so a caller (or the AI
 * microservice, on the reverse path) can log the same id.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    private static final String REQUEST_ID_SPAN_ATTRIBUTE = "request.id";

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String incomingRequestId = request.getHeader(CorrelationIdContext.HEADER_NAME);
        String requestId = StringUtils.hasText(incomingRequestId) ? incomingRequestId : UUID.randomUUID().toString();

        CorrelationIdContext.setCurrentRequestId(requestId);
        Span.current().setAttribute(REQUEST_ID_SPAN_ATTRIBUTE, requestId);
        response.setHeader(CorrelationIdContext.HEADER_NAME, requestId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            CorrelationIdContext.clear();
        }
    }
}
