package org.dariusturcu.backend.model.group;

// OPEN is the lobby. AUDIO_CHECK follows the admin's Start game: members can still join
// and leave while every required DJ shares their audio and passes the sound test, and the
// settings stay locked. LOCKED is a running game session.
public enum GroupStatus {
    OPEN,
    AUDIO_CHECK,
    LOCKED
}
