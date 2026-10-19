package org.dariusturcu.backend.observability;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;

/**
 * Forwards the current request's correlation id onto any outgoing RestClient call, so a
 * single user action stays traceable across the core service and the AI microservice's
 * logs. A no-op when there's no correlation id on the current thread (a call made outside
 * request handling, such as a scheduled job).
 */
@Component
public class CorrelationIdPropagatingInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        String requestId = CorrelationIdContext.getCurrentRequestId();
        if (StringUtils.hasText(requestId)) {
            request.getHeaders().set(CorrelationIdContext.HEADER_NAME, requestId);
        }
        return execution.execute(request, body);
    }
}
