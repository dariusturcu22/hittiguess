package org.dariusturcu.backend.model.session;

public record PlayerCardDTO(Long songId, String artist, String title, int releaseYear, String color, int position) {
}
