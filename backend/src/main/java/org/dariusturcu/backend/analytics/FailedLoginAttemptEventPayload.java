package org.dariusturcu.backend.analytics;

// Carries nothing about who tried to sign in: the attempted username is often an email
// address or a mistyped password, so only the event itself and its timestamp are kept.
public record FailedLoginAttemptEventPayload() {
}
