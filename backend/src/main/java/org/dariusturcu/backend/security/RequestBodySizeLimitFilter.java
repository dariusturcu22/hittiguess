package org.dariusturcu.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.dariusturcu.backend.exception.ErrorResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

/**
 * Refuses any request body larger than MAX_REQUEST_BODY_BYTES before it reaches security,
 * rate limiting or a controller. A declared Content-Length over the limit is answered with
 * 413 straight away; a body without a declared length is cut off once it passes the limit.
 * Multipart uploads are capped separately by spring.servlet.multipart, set to the same limit.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class RequestBodySizeLimitFilter extends OncePerRequestFilter {

    public static final long MAX_REQUEST_BODY_BYTES = 1_048_576L;
    static final String BODY_TOO_LARGE_MESSAGE = "The request body exceeds the 1 MB limit";
    private static final long UNKNOWN_CONTENT_LENGTH = -1L;

    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        long declaredLength = request.getContentLengthLong();
        if (declaredLength > MAX_REQUEST_BODY_BYTES) {
            writeBodyTooLargeResponse(response);
            return;
        }
        HttpServletRequest boundedRequest = declaredLength == UNKNOWN_CONTENT_LENGTH
                ? new BodyLimitedRequest(request)
                : request;
        filterChain.doFilter(boundedRequest, response);
    }

    private void writeBodyTooLargeResponse(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.PAYLOAD_TOO_LARGE.value(),
                BODY_TOO_LARGE_MESSAGE,
                LocalDateTime.now()
        );
        objectMapper.writeValue(response.getWriter(), errorResponse);
    }

    /**
     * Thrown while reading a body without a declared length once it passes the limit.
     * GlobalExceptionHandler maps it to 413.
     */
    public static class RequestBodyTooLargeException extends IOException {
        public RequestBodyTooLargeException() {
            super(BODY_TOO_LARGE_MESSAGE);
        }
    }

    private static final class BodyLimitedRequest extends HttpServletRequestWrapper {

        private ServletInputStream limitedInputStream;

        BodyLimitedRequest(HttpServletRequest request) {
            super(request);
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (limitedInputStream == null) {
                limitedInputStream = new LimitedServletInputStream(super.getInputStream());
            }
            return limitedInputStream;
        }

        @Override
        public BufferedReader getReader() throws IOException {
            String encoding = getCharacterEncoding();
            Charset charset = encoding == null ? StandardCharsets.UTF_8 : Charset.forName(encoding);
            return new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }
    }

    private static final class LimitedServletInputStream extends ServletInputStream {

        private static final int END_OF_STREAM = -1;

        private final ServletInputStream delegate;
        private long bytesRead;

        LimitedServletInputStream(ServletInputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public int read() throws IOException {
            int nextByte = delegate.read();
            if (nextByte != END_OF_STREAM) {
                countBytes(1);
            }
            return nextByte;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int count = delegate.read(buffer, offset, length);
            if (count > 0) {
                countBytes(count);
            }
            return count;
        }

        private void countBytes(long count) throws RequestBodyTooLargeException {
            bytesRead += count;
            if (bytesRead > MAX_REQUEST_BODY_BYTES) {
                throw new RequestBodyTooLargeException();
            }
        }

        @Override
        public boolean isFinished() {
            return delegate.isFinished();
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            delegate.setReadListener(readListener);
        }
    }
}
