package org.dariusturcu.backend.model.session;

// Broadcast to the whole session when a player first gets a round's artist or title
// right. Says who and which part, never the answer itself.
public record CorrectGuessDTO(Long roundId, Long playerId, String displayName, boolean artistGuessed, boolean titleGuessed) {
}
