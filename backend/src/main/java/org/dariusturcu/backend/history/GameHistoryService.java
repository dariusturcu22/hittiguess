package org.dariusturcu.backend.history;

import org.dariusturcu.backend.model.session.*;
import org.dariusturcu.backend.repository.GroupRepository;
import org.dariusturcu.backend.repository.RoundRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Service
@Transactional
public class GameHistoryService {
    public static final String RULES_VERSION = "full-pass-v1";
    public static final String INTERRUPTED = "INTERRUPTED";
    public static final String TARGET_REACHED = "TARGET_REACHED";
    public static final String SONGS_EXHAUSTED = "SONGS_EXHAUSTED";
    private static final String DELETED_PLAYER = "Deleted player";
    private static final int FIRST_RANK = 1;
    private static final int MAXIMUM_PAGE_SIZE = 100;
    private static final String DIFFICULTY_MODE = "DIFFICULTY";
    private static final String CUSTOM_MODE = "CUSTOM";
    private static final String FINISHED_PARTICIPATION = "FINISHED";
    private static final String LEFT_PARTICIPATION = "LEFT";
    private final JdbcTemplate core;
    private final RoundRepository rounds;
    private final GroupRepository groups;

    public GameHistoryService(@Qualifier("coreJdbcTemplate") JdbcTemplate core,
            RoundRepository rounds, GroupRepository groups) {
        this.core = core;
        this.rounds = rounds;
        this.groups = groups;
    }

    @EventListener
    public void completed(GameCompleted completion) {
        GameSession session = completion.session();
        List<Round> sessionRounds = rounds.findBySessionOrderByRoundNumberAsc(session);
        String groupName = groups.findById(session.getGroupId())
                .map(group -> "Group " + group.getJoinCode()).orElse("Game group");
        List<Long> inserted = core.query("""
                INSERT INTO game_summaries(source_session_id, group_name, started_at, ended_at,
                    mode, difficulty_tier, win_target_cards, participant_count, turns_played, ending_reason, rules_version)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT(source_session_id) DO NOTHING RETURNING id
                """, (result, rowNumber) -> result.getLong("id"), session.getId(), groupName,
                Timestamp.from(session.getCreatedAt()), Timestamp.from(Instant.now()),
                session.getDifficultyTier() == null ? CUSTOM_MODE : DIFFICULTY_MODE,
                session.getDifficultyTier() == null ? null : session.getDifficultyTier().name(),
                session.getWinConditionCardCount(), session.getPlayers().size(),
                sessionRounds.stream().filter(round -> round.getStatus() == RoundStatus.SCORED).count(),
                completion.endingReason(), RULES_VERSION);
        if (inserted.isEmpty()) {
            return;
        }
        long summaryId = inserted.getFirst();
        for (Player player : session.getPlayers()) {
            List<Round> placements = sessionRounds.stream()
                    .filter(round -> round.getActivePlayer().getId().equals(player.getId()))
                    .filter(round -> round.getStatus() == RoundStatus.SCORED && round.getPlacedPosition() != null).toList();
            List<Guess> guesses = sessionRounds.stream().flatMap(round -> round.getGuesses().stream())
                    .filter(guess -> guess.getPlayer().getId().equals(player.getId())).toList();
            List<Bet> bets = sessionRounds.stream().flatMap(round -> round.getBets().stream())
                    .filter(bet -> bet.getPlayer().getId().equals(player.getId())).toList();
            int cardRank = completion.results().cardCountRanking().stream()
                    .filter(result -> result.playerId().equals(player.getId())).findFirst().orElseThrow().rank();
            int artistRank = rank(completion.results().mostArtistsGuessed(), player.getId());
            int titleRank = rank(completion.results().mostTitlesGuessed(), player.getId());
            core.update("""
                    INSERT INTO game_participant_summaries(game_summary_id, user_id, display_name, participation_status,
                        final_card_count, card_rank, artist_rank, title_rank, is_winner, placement_attempts,
                        correct_placements, title_attempts, correct_titles, artist_attempts, correct_artists, bets_placed, bets_won)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, summaryId, player.getUser().getId(), player.getDisplayName(),
                    player.getStatus() == PlayerStatus.LEFT ? LEFT_PARTICIPATION : FINISHED_PARTICIPATION, player.getTimeline().size(),
                    cardRank, artistRank, titleRank, !INTERRUPTED.equals(completion.endingReason()) && cardRank == FIRST_RANK,
                    placements.size(), placements.stream().filter(round -> Boolean.TRUE.equals(round.getPlacementCorrect())).count(),
                    guesses.stream().filter(guess -> guess.getGuessedTitle() != null).count(), player.getTotalTitlesGuessed(),
                    guesses.stream().filter(guess -> guess.getGuessedArtist() != null).count(), player.getTotalArtistsGuessed(),
                    bets.size(), bets.stream().filter(Bet::isWon).count());
        }
    }

    private int rank(List<LeaderboardEntryDTO> results, Long playerId) {
        return results.stream().filter(result -> result.playerId().equals(playerId)).findFirst().orElseThrow().rank();
    }

    @Transactional(readOnly = true)
    public GameHistoryPage list(long userId, int page, int pageSize) {
        if (page < 0 || pageSize < 1 || pageSize > MAXIMUM_PAGE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid history page");
        }
        List<Long> summaryIds = core.query("""
                SELECT summary.id FROM game_summaries summary JOIN game_participant_summaries participant
                ON participant.game_summary_id = summary.id WHERE participant.user_id = ?
                ORDER BY summary.ended_at DESC, summary.id DESC LIMIT ? OFFSET ?
                """, (result, rowNumber) -> result.getLong("id"), userId, pageSize, (long) page * pageSize);
        long total = core.queryForObject("SELECT count(*) FROM game_participant_summaries WHERE user_id = ?", Long.class, userId);
        return new GameHistoryPage(summaryIds.stream().map(summaryId -> detail(userId, summaryId)).toList(), page, pageSize, total);
    }

    @Transactional(readOnly = true)
    public GameSummary detail(long userId, long summaryId) {
        List<GameSummary> summaries = core.query("""
                SELECT summary.* FROM game_summaries summary WHERE summary.id = ? AND EXISTS
                (SELECT 1 FROM game_participant_summaries participant WHERE participant.game_summary_id = summary.id
                 AND participant.user_id = ?)
                """, (result, rowNumber) -> summary(result), summaryId, userId);
        return summaries.stream().findFirst().orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Game history entry not found"));
    }

    @Transactional(readOnly = true)
    public List<GameSummary> export(long userId) {
        return core.query("SELECT game_summary_id FROM game_participant_summaries WHERE user_id = ? ORDER BY game_summary_id",
                (result, rowNumber) -> result.getLong("game_summary_id"), userId).stream()
                .map(summaryId -> detail(userId, summaryId)).toList();
    }

    @Transactional(readOnly = true)
    public PlayerHistoryStatistics statistics(long userId) {
        return core.queryForObject("""
                SELECT count(*) FILTER(WHERE summary.ending_reason <> ?) AS games_played,
                  count(*) FILTER(WHERE participant.is_winner) AS wins,
                  count(*) FILTER(WHERE summary.ending_reason = ?) AS interrupted_games,
                  coalesce(sum(participant.placement_attempts), 0) AS placement_attempts,
                  coalesce(sum(participant.correct_placements), 0) AS correct_placements,
                  coalesce(sum(participant.correct_titles), 0) AS correct_titles,
                  coalesce(sum(participant.correct_artists), 0) AS correct_artists
                FROM game_participant_summaries participant JOIN game_summaries summary
                ON summary.id = participant.game_summary_id WHERE participant.user_id = ?
                """, (result, rowNumber) -> new PlayerHistoryStatistics(result.getLong("games_played"), result.getLong("wins"),
                result.getLong("interrupted_games"), result.getLong("placement_attempts"), result.getLong("correct_placements"),
                result.getLong("correct_titles"), result.getLong("correct_artists")), INTERRUPTED, INTERRUPTED, userId);
    }

    @EventListener
    public void deleted(AccountHistoryDeleted deletion) {
        core.update("UPDATE game_participant_summaries SET user_id = NULL, display_name = ? WHERE user_id = ?",
                DELETED_PLAYER, deletion.userId());
        core.update("""
                DELETE FROM game_summaries summary WHERE NOT EXISTS
                (SELECT 1 FROM game_participant_summaries participant
                 WHERE participant.game_summary_id = summary.id AND participant.user_id IS NOT NULL)
                """);
    }

    private GameSummary summary(ResultSet result) throws SQLException {
        long summaryId = result.getLong("id");
        List<GameParticipantSummary> participants = core.query("""
                SELECT * FROM game_participant_summaries WHERE game_summary_id = ? ORDER BY card_rank, id
                """, (participant, rowNumber) -> new GameParticipantSummary(participant.getLong("id"),
                participant.getObject("user_id", Long.class), participant.getString("display_name"),
                participant.getString("participation_status"), participant.getInt("final_card_count"),
                participant.getInt("card_rank"), participant.getInt("artist_rank"), participant.getInt("title_rank"),
                participant.getBoolean("is_winner"), participant.getInt("placement_attempts"), participant.getInt("correct_placements"),
                participant.getInt("title_attempts"), participant.getInt("correct_titles"), participant.getInt("artist_attempts"),
                participant.getInt("correct_artists"), participant.getInt("bets_placed"), participant.getInt("bets_won")), summaryId);
        return new GameSummary(summaryId, result.getString("group_name"), result.getTimestamp("started_at").toInstant(),
                result.getTimestamp("ended_at").toInstant(), result.getString("mode"), result.getString("difficulty_tier"),
                result.getInt("win_target_cards"), result.getInt("participant_count"), result.getInt("turns_played"),
                result.getString("ending_reason"), result.getString("rules_version"), participants);
    }
}
