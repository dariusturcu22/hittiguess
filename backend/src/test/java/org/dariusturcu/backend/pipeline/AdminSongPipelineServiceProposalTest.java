package org.dariusturcu.backend.pipeline;

import org.dariusturcu.backend.model.admin.ProposalBatchRequest;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminSongPipelineServiceProposalTest {
    private static final long PROPOSED_SONG_ID = 1;
    private static final long UNPROPOSED_SONG_ID = 2;
    private static final long ACTIVE_GAME_SONG_ID = 3;
    private static final long MISSING_SONG_ID = 4;
    private static final int PROPOSED_YEAR = 2014;
    private static final String SELECT_YOUTUBE_ID = "SELECT youtube_id FROM songs WHERE id = ?";
    private static final String SELECT_PROPOSAL = "SELECT proposed_release_year FROM songs WHERE id = ? FOR UPDATE";
    private static final String ACCEPT_UPDATE_FRAGMENT = "SET release_year = proposed_release_year";
    private static final String DISMISS_UPDATE_FRAGMENT = "SET proposed_release_year = NULL, proposed_at = NULL WHERE id = ?";

    private final JdbcTemplate core = mock(JdbcTemplate.class);
    private final AdminSongPipelineService service = new AdminSongPipelineService(core, mock(PipelineJobRepository.class),
            mock(PipelineJobService.class), mock(AdminPipelineService.class), JsonMapper.builder().build());

    private void songWithProposal(long songId, boolean proposed) {
        when(core.queryForList(SELECT_YOUTUBE_ID, String.class, songId)).thenReturn(List.of("video" + songId));
        List<Map<String, Object>> proposalRows = new java.util.ArrayList<>();
        proposalRows.add(java.util.Collections.singletonMap("proposed_release_year", proposed ? PROPOSED_YEAR : null));
        when(core.queryForList(SELECT_PROPOSAL, songId)).thenReturn(proposalRows);
    }

    private void songInActiveGame(long songId) {
        when(core.queryForObject(anyString(), eq(Boolean.class), eq(songId), eq(songId), eq(songId))).thenReturn(true);
    }

    @Test
    void acceptSetsTheProposedYearOnASongWithAProposal() {
        songWithProposal(PROPOSED_SONG_ID, true);

        var result = service.acceptProposals(new ProposalBatchRequest(List.of(PROPOSED_SONG_ID)));

        assertThat(result.affected()).isEqualTo(1);
        assertThat(result.skippedSongIds()).isEmpty();
        verify(core).update(org.mockito.ArgumentMatchers.contains(ACCEPT_UPDATE_FRAGMENT), eq(PROPOSED_SONG_ID));
    }

    @Test
    void acceptSkipsASongWithoutAProposal() {
        songWithProposal(UNPROPOSED_SONG_ID, false);

        var result = service.acceptProposals(new ProposalBatchRequest(List.of(UNPROPOSED_SONG_ID)));

        assertThat(result.affected()).isZero();
        assertThat(result.skippedSongIds()).containsExactly(UNPROPOSED_SONG_ID);
        verify(core, never()).update(anyString(), anyLong());
    }

    @Test
    void acceptSkipsASongInAnActiveGame() {
        songWithProposal(ACTIVE_GAME_SONG_ID, true);
        songInActiveGame(ACTIVE_GAME_SONG_ID);

        var result = service.acceptProposals(new ProposalBatchRequest(List.of(ACTIVE_GAME_SONG_ID)));

        assertThat(result.affected()).isZero();
        assertThat(result.skippedSongIds()).containsExactly(ACTIVE_GAME_SONG_ID);
        verify(core, never()).update(anyString(), anyLong());
    }

    @Test
    void acceptSkipsAnUnknownSong() {
        when(core.queryForList(SELECT_YOUTUBE_ID, String.class, MISSING_SONG_ID)).thenReturn(List.of());

        var result = service.acceptProposals(new ProposalBatchRequest(List.of(MISSING_SONG_ID)));

        assertThat(result.skippedSongIds()).containsExactly(MISSING_SONG_ID);
    }

    @Test
    void dismissClearsTheProposalEvenForASongInAnActiveGame() {
        songWithProposal(ACTIVE_GAME_SONG_ID, true);
        songInActiveGame(ACTIVE_GAME_SONG_ID);

        var result = service.dismissProposals(new ProposalBatchRequest(List.of(ACTIVE_GAME_SONG_ID)));

        assertThat(result.affected()).isEqualTo(1);
        verify(core).update(org.mockito.ArgumentMatchers.contains(DISMISS_UPDATE_FRAGMENT), eq(ACTIVE_GAME_SONG_ID));
    }

    @Test
    void dismissSkipsASongWithoutAProposal() {
        songWithProposal(UNPROPOSED_SONG_ID, false);

        var result = service.dismissProposals(new ProposalBatchRequest(List.of(UNPROPOSED_SONG_ID)));

        assertThat(result.skippedSongIds()).containsExactly(UNPROPOSED_SONG_ID);
    }

    @Test
    void anEmptyOrOversizedBatchIsRejected() {
        assertThatThrownBy(() -> service.acceptProposals(new ProposalBatchRequest(List.of())))
                .isInstanceOf(IllegalArgumentException.class);
        List<Long> oversized = java.util.stream.LongStream.rangeClosed(1, AdminSongPipelineService.MAXIMUM_PROPOSAL_BATCH_SIZE + 1)
                .boxed().toList();
        assertThatThrownBy(() -> service.dismissProposals(new ProposalBatchRequest(oversized)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
