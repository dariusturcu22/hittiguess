package org.dariusturcu.backend.observability;

import org.slf4j.MDC;

/**
 * Holds the correlation id for the request being processed on the current thread, via SLF4J's
 * MDC. Populated by CorrelationIdFilter for every incoming request and read back by anything
 * that needs to propagate it further, such as the AI microservice RestClient interceptor.
 */
public final class CorrelationIdContext {

    public static final String HEADER_NAME = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    private CorrelationIdContext() {
    }

    public static String getCurrentRequestId() {
        return MDC.get(MDC_KEY);
    }

    public static void setCurrentRequestId(String requestId) {
        MDC.put(MDC_KEY, requestId);
    }

    public static void clear() {
        MDC.remove(MDC_KEY);
    }
}
