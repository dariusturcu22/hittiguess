package org.dariusturcu.backend.pipeline;

// A transient failure is retried with backoff; a permanent one fails the run at once.
public enum FailureKind {
    TRANSIENT,
    PERMANENT
}
