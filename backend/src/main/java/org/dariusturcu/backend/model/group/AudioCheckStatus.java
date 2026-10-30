package org.dariusturcu.backend.model.group;

// Where one player's audio share stands. CANNOT_SHARE is never stored: it is reported for
// a member whose browser cannot capture audio, whatever their stored status says.
public enum AudioCheckStatus {
    NOT_SHARED,
    TESTING,
    OK,
    FAILED,
    CANNOT_SHARE
}
