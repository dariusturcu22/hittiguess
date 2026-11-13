package org.dariusturcu.backend.security;

import jakarta.servlet.ServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestBodySizeLimitFilterTest {

    private static final int LIMIT_BYTES = (int) RequestBodySizeLimitFilter.MAX_REQUEST_BODY_BYTES;
    private static final long UNKNOWN_CONTENT_LENGTH = -1L;

    private final RequestBodySizeLimitFilter filter = new RequestBodySizeLimitFilter(JsonMapper.builder().build());

    @Test
    void aDeclaredBodyOverTheLimitIsRefusedWith413BeforeTheChainRuns() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/playlists");
        request.setContent(new byte[LIMIT_BYTES + 1]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE.value());
        assertThat(response.getContentAsString()).contains(RequestBodySizeLimitFilter.BODY_TOO_LARGE_MESSAGE);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void aDeclaredBodyAtTheLimitPassesThrough() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/playlists");
        request.setContent(new byte[LIMIT_BYTES]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void aBodyWithoutADeclaredLengthIsCutOffOncePastTheLimit() throws Exception {
        MockHttpServletRequest request = requestWithoutDeclaredLength(new byte[LIMIT_BYTES + 1]);
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        ServletRequest forwardedRequest = chain.getRequest();
        InputStream body = forwardedRequest.getInputStream();
        assertThatThrownBy(body::readAllBytes)
                .isInstanceOf(RequestBodySizeLimitFilter.RequestBodyTooLargeException.class);
    }

    @Test
    void aBodyWithoutADeclaredLengthUnderTheLimitReadsInFull() throws Exception {
        byte[] content = new byte[LIMIT_BYTES];
        MockHttpServletRequest request = requestWithoutDeclaredLength(content);
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest().getInputStream().readAllBytes()).hasSize(content.length);
    }

    private static MockHttpServletRequest requestWithoutDeclaredLength(byte[] content) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/playlists") {
            @Override
            public long getContentLengthLong() {
                return UNKNOWN_CONTENT_LENGTH;
            }
        };
        request.setContent(content);
        return request;
    }
}
