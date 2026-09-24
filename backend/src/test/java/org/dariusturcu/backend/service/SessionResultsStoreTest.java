package org.dariusturcu.backend.service;

import org.dariusturcu.backend.model.session.LeaderboardEntryDTO;
import org.dariusturcu.backend.model.session.PlayerResultDTO;
import org.dariusturcu.backend.model.session.SessionResultsDTO;
import org.dariusturcu.backend.model.session.StoredSessionResults;
import org.dariusturcu.backend.repository.StoredSessionResultsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionResultsStoreTest {

    private static final Long GROUP_ID = 7L;
    private static final Long WINNER_PLAYER_ID = 3L;
    private static final int WINNING_CARD_COUNT = 5;

    @Mock
    private StoredSessionResultsRepository storedSessionResultsRepository;

    private SessionResultsDTO results() {
        return new SessionResultsDTO(GROUP_ID,
                List.of(new PlayerResultDTO(WINNER_PLAYER_ID, "Winner", WINNING_CARD_COUNT, 1)),
                List.of(new LeaderboardEntryDTO(WINNER_PLAYER_ID, "Winner", 2)),
                List.of(new LeaderboardEntryDTO(WINNER_PLAYER_ID, "Winner", 1)));
    }

    @Test
    void storedResultsSurviveANewStoreInstanceAsAfterARestart() {
        SessionResultsStore storeBeforeRestart = new SessionResultsStore(storedSessionResultsRepository, JsonMapper.builder().build());
        storeBeforeRestart.store(GROUP_ID, results());
        ArgumentCaptor<StoredSessionResults> savedCaptor = ArgumentCaptor.forClass(StoredSessionResults.class);
        verify(storedSessionResultsRepository).save(savedCaptor.capture());
        when(storedSessionResultsRepository.findById(GROUP_ID)).thenReturn(Optional.of(savedCaptor.getValue()));

        SessionResultsStore storeAfterRestart = new SessionResultsStore(storedSessionResultsRepository, JsonMapper.builder().build());

        assertThat(storeAfterRestart.get(GROUP_ID)).contains(results());
    }

    @Test
    void aGroupWithNoCompletedSessionHasNoResults() {
        SessionResultsStore store = new SessionResultsStore(storedSessionResultsRepository, JsonMapper.builder().build());

        assertThat(store.get(GROUP_ID)).isEmpty();
    }
}
