package org.dariusturcu.backend.model.session;

import jakarta.validation.constraints.Size;

// A STOMP payload does not pass through the REST @Valid path, so GameSessionService
// enforces MAX_GUESS_LENGTH as well; it matches the guess columns' own length.
public record TitleArtistGuessRequest(
        @Size(max = TitleArtistGuessRequest.MAX_GUESS_LENGTH, message = "Guessed artist must be at most 255 characters")
        String guessedArtist,

        @Size(max = TitleArtistGuessRequest.MAX_GUESS_LENGTH, message = "Guessed title must be at most 255 characters")
        String guessedTitle) {

    public static final int MAX_GUESS_LENGTH = 255;
}
