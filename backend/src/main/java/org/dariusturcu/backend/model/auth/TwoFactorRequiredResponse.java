package org.dariusturcu.backend.model.auth;

public record TwoFactorRequiredResponse(
        boolean twoFactorRequired,
        String pendingToken
) {
    public TwoFactorRequiredResponse(String pendingToken) {
        this(true, pendingToken);
    }
}
