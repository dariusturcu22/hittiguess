package org.dariusturcu.backend.util;

import java.util.Locale;

public final class EmailAddresses {

    private EmailAddresses() {
    }

    public static String normalize(String emailAddress) {
        if (emailAddress == null) {
            return null;
        }
        return emailAddress.trim().toLowerCase(Locale.ROOT);
    }
}
