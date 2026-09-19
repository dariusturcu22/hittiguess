package org.dariusturcu.backend.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void reusesTheIncomingRequestIdWhenPresent() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain filterChain = mock(FilterChain.class);
        when(request.getHeader(CorrelationIdContext.HEADER_NAME)).thenReturn("existing-request-id");

        filter.doFilter(request, response, filterChain);

        verify(response).setHeader(CorrelationIdContext.HEADER_NAME, "existing-request-id");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void generatesARequestIdWhenNoneIsSupplied() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain filterChain = mock(FilterChain.class);
        when(request.getHeader(CorrelationIdContext.HEADER_NAME)).thenReturn(null);

        filter.doFilter(request, response, filterChain);

        verify(response).setHeader(eq(CorrelationIdContext.HEADER_NAME), argThatNonBlank());
    }

    @Test
    void clearsTheMdcAfterTheRequestCompletes() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain filterChain = mock(FilterChain.class);
        when(request.getHeader(CorrelationIdContext.HEADER_NAME)).thenReturn("some-id");

        filter.doFilter(request, response, filterChain);

        assertThat(CorrelationIdContext.getCurrentRequestId()).isNull();
    }

    private String argThatNonBlank() {
        return org.mockito.ArgumentMatchers.argThat(value -> value != null && !value.isBlank());
    }
}
