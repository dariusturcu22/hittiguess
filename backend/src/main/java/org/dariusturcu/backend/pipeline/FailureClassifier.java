package org.dariusturcu.backend.pipeline;

import java.util.Set;

/**
 * Sorts a stage failure into transient or permanent. Outages, rate limits, timeouts, and
 * unknown errors are transient. An API key the provider refuses is transient too and pauses
 * the stage, since every job of that stage would fail the same way. Only a YouTube video that
 * no longer exists and a source that returned no usable metadata are permanent.
 */
public final class FailureClassifier {

    static final int HTTP_BAD_REQUEST = 400;
    static final int HTTP_UNAUTHORIZED = 401;
    static final int HTTP_FORBIDDEN = 403;
    static final int HTTP_NOT_FOUND = 404;
    static final int HTTP_REQUEST_TIMEOUT = 408;
    static final int HTTP_GONE = 410;
    static final int HTTP_TOO_EARLY = 425;
    static final int HTTP_TOO_MANY_REQUESTS = 429;
    static final int SERVER_ERROR_RANGE_START = 500;
    static final int SERVER_ERROR_RANGE_END = 599;

    private static final Set<Integer> RETRYABLE_CLIENT_STATUSES =
            Set.of(HTTP_REQUEST_TIMEOUT, HTTP_TOO_EARLY, HTTP_TOO_MANY_REQUESTS);
    private static final Set<Integer> KEY_REJECTED_STATUSES =
            Set.of(HTTP_BAD_REQUEST, HTTP_UNAUTHORIZED, HTTP_FORBIDDEN);
    private static final Set<Integer> VIDEO_GONE_STATUSES = Set.of(HTTP_NOT_FOUND, HTTP_GONE);

    static final FailureClassification TRANSIENT = new FailureClassification(FailureKind.TRANSIENT, false);
    static final FailureClassification PERMANENT = new FailureClassification(FailureKind.PERMANENT, false);
    static final FailureClassification KEY_REJECTED = new FailureClassification(FailureKind.TRANSIENT, true);

    private FailureClassifier() {
    }

    public enum FailureCause {
        HTTP_ERROR,
        TIMEOUT,
        CONNECTION_ERROR,
        NO_USABLE_METADATA,
        OTHER
    }

    public enum ProviderHint {
        YOUTUBE_VIDEO_LOOKUP(true),
        API_KEY_PROVIDER(true),
        KEYLESS_PROVIDER(false);

        private final boolean usesApiKey;

        ProviderHint(boolean usesApiKey) {
            this.usesApiKey = usesApiKey;
        }

        public boolean usesApiKey() {
            return usesApiKey;
        }
    }

    public record FailureClassification(FailureKind kind, boolean keyRejected) {
    }

    /** The HTTP status is null when the call never got a response. */
    public static FailureClassification classify(Integer httpStatus, FailureCause cause, ProviderHint provider) {
        if (cause == FailureCause.NO_USABLE_METADATA) {
            return PERMANENT;
        }
        if (cause == FailureCause.TIMEOUT || cause == FailureCause.CONNECTION_ERROR || httpStatus == null) {
            return TRANSIENT;
        }
        if (isServerError(httpStatus) || RETRYABLE_CLIENT_STATUSES.contains(httpStatus)) {
            return TRANSIENT;
        }
        if (provider.usesApiKey() && KEY_REJECTED_STATUSES.contains(httpStatus)) {
            return KEY_REJECTED;
        }
        if (provider == ProviderHint.YOUTUBE_VIDEO_LOOKUP && VIDEO_GONE_STATUSES.contains(httpStatus)) {
            return PERMANENT;
        }
        return TRANSIENT;
    }

    private static boolean isServerError(int httpStatus) {
        return httpStatus >= SERVER_ERROR_RANGE_START && httpStatus <= SERVER_ERROR_RANGE_END;
    }
}
