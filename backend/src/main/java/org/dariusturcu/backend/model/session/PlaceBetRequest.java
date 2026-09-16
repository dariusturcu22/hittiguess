package org.dariusturcu.backend.model.session;

// position is a 0-based gap index into the ACTIVE PLAYER's own timeline for this round,
// the same shared timeline every bettor sees and bets against, not the bettor's own: 0 is
// the gap before every existing card, timeline.size() is the gap after every existing
// card, anything in between is the gap there. It must differ from the gap the active
// player already locked their own placement into, and from every other bettor's chosen
// gap this round.
public record PlaceBetRequest(int position) {
}
