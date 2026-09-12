package org.dariusturcu.backend.exception;

public enum ResourceType {
    PLAYLIST("Playlist"),
    SONG("Song"),
    USER("User"),
    SONG_NOT_IN_PLAYLIST("Song"),
    PLAYLIST_MEMBER("Playlist member");

    private final String displayName;

    ResourceType(String displayName) {
        this.displayName = displayName;
    }

    public String formatNotFoundMessage(Long id) {
        return displayName + " {id=" + id + "} not found";
    }
}
