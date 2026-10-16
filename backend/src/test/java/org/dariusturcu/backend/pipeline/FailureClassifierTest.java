package org.dariusturcu.backend.pipeline;

import org.dariusturcu.backend.pipeline.FailureClassifier.FailureCause;
import org.dariusturcu.backend.pipeline.FailureClassifier.ProviderHint;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FailureClassifierTest {

    @ParameterizedTest
    @ValueSource(ints = {408, 425, 429, 500, 502, 503, 504, 599})
    void outagesRateLimitsAndTimeoutStatusesAreTransientForEveryProvider(int httpStatus) {
        for (ProviderHint provider : ProviderHint.values()) {
            assertThat(FailureClassifier.classify(httpStatus, FailureCause.HTTP_ERROR, provider))
                    .isEqualTo(FailureClassifier.TRANSIENT);
        }
    }

    @ParameterizedTest
    @EnumSource(value = FailureCause.class, names = {"TIMEOUT", "CONNECTION_ERROR", "OTHER"})
    void timeoutsConnectionErrorsAndUnknownExceptionsWithoutAStatusAreTransient(FailureCause cause) {
        assertThat(FailureClassifier.classify(null, cause, ProviderHint.KEYLESS_PROVIDER))
                .isEqualTo(FailureClassifier.TRANSIENT);
    }

    @Test
    void aTimeoutIsTransientEvenWhenAStatusIsAttached() {
        assertThat(FailureClassifier.classify(FailureClassifier.HTTP_NOT_FOUND, FailureCause.TIMEOUT,
                ProviderHint.YOUTUBE_VIDEO_LOOKUP)).isEqualTo(FailureClassifier.TRANSIENT);
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403})
    void aKeyedProviderRefusingTheRequestIsATransientRejectedKey(int httpStatus) {
        assertThat(FailureClassifier.classify(httpStatus, FailureCause.HTTP_ERROR, ProviderHint.API_KEY_PROVIDER))
                .isEqualTo(FailureClassifier.KEY_REJECTED);
        assertThat(FailureClassifier.classify(httpStatus, FailureCause.HTTP_ERROR, ProviderHint.YOUTUBE_VIDEO_LOOKUP))
                .isEqualTo(FailureClassifier.KEY_REJECTED);
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403})
    void aKeylessProviderRefusingTheRequestIsTransientWithoutRejectingAKey(int httpStatus) {
        assertThat(FailureClassifier.classify(httpStatus, FailureCause.HTTP_ERROR, ProviderHint.KEYLESS_PROVIDER))
                .isEqualTo(FailureClassifier.TRANSIENT);
    }

    @ParameterizedTest
    @ValueSource(ints = {404, 410})
    void aMissingYoutubeVideoIsPermanent(int httpStatus) {
        assertThat(FailureClassifier.classify(httpStatus, FailureCause.HTTP_ERROR, ProviderHint.YOUTUBE_VIDEO_LOOKUP))
                .isEqualTo(FailureClassifier.PERMANENT);
    }

    @ParameterizedTest
    @ValueSource(ints = {404, 410})
    void aNotFoundFromAnotherProviderIsTransient(int httpStatus) {
        assertThat(FailureClassifier.classify(httpStatus, FailureCause.HTTP_ERROR, ProviderHint.API_KEY_PROVIDER))
                .isEqualTo(FailureClassifier.TRANSIENT);
        assertThat(FailureClassifier.classify(httpStatus, FailureCause.HTTP_ERROR, ProviderHint.KEYLESS_PROVIDER))
                .isEqualTo(FailureClassifier.TRANSIENT);
    }

    @Test
    void noUsableMetadataIsPermanent() {
        assertThat(FailureClassifier.classify(null, FailureCause.NO_USABLE_METADATA, ProviderHint.KEYLESS_PROVIDER))
                .isEqualTo(FailureClassifier.PERMANENT);
    }
}
