package org.dariusturcu.backend.model.session;

import java.util.List;

public record SessionResultsDTO(
        Long groupId,
        List<PlayerResultDTO> cardCountRanking,
        List<LeaderboardEntryDTO> mostArtistsGuessed,
        List<LeaderboardEntryDTO> mostTitlesGuessed) {
}
