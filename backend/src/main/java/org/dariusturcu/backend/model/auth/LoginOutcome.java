package org.dariusturcu.backend.model.auth;

// A correct password alone completes login for most accounts (Completed), but not for a
// twoFactorEnabled account, which instead gets a short-lived pending token to submit to
// /auth/2fa/verify (TwoFactorRequired). AuthController branches on which of the two it got
// back rather than AuthService returning a half-populated AuthResult.
public sealed interface LoginOutcome {
    record Completed(AuthResult authResult) implements LoginOutcome {
    }

    record TwoFactorRequired(String pendingToken) implements LoginOutcome {
    }
}
