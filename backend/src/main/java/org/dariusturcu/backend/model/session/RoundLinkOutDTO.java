package org.dariusturcu.backend.model.session;

// The current round's YouTube link-out for the DJ: the stored video id and its canonical
// watch URL, which resolves on the real YouTube page or app, never an embedded player.
// Fetchable only by the round's DJ and only before the reveal makes the song public.
public record RoundLinkOutDTO(
        Long roundId,
        int roundNumber,
        String youtubeId,
        String watchUrl) {
}
