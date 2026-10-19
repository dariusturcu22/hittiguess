package org.dariusturcu.backend.pipeline;

import java.time.Duration;

public class AiStageFailure extends RuntimeException {
    private final FailureClassifier.FailureClassification classification;
    private final Duration retryAfter;

    public AiStageFailure(String reason, FailureClassifier.FailureClassification classification, Duration retryAfter) {
        super(reason);
        this.classification = classification;
        this.retryAfter = retryAfter;
    }

    public FailureClassifier.FailureClassification classification() {
        return classification;
    }

    public Duration retryAfter() {
        return retryAfter;
    }
}
