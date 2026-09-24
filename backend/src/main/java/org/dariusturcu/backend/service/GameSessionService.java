package org.dariusturcu.backend.service;

import org.dariusturcu.backend.exception.ConflictException;
import org.dariusturcu.backend.exception.ResourceNotFoundException;
import org.dariusturcu.backend.exception.ResourceType;
import org.dariusturcu.backend.model.group.DjMode;
import org.dariusturcu.backend.model.group.Group;
import org.dariusturcu.backend.model.group.GroupDetailDTO;
import org.dariusturcu.backend.model.group.Member;
import org.dariusturcu.backend.model.mapper.SessionMapper;
import org.dariusturcu.backend.model.session.Bet;
import org.dariusturcu.backend.model.session.CorrectGuessDTO;
import org.dariusturcu.backend.model.session.GameSession;
import org.dariusturcu.backend.model.session.GeneratedSongPreviewDTO;
import org.dariusturcu.backend.model.session.GenerateDifficultySetRequest;
import org.dariusturcu.backend.model.session.Guess;
import org.dariusturcu.backend.model.session.GuessResultDTO;
import org.dariusturcu.backend.model.session.GuessStateDTO;
import org.dariusturcu.backend.model.session.LeaderboardEntryDTO;
import org.dariusturcu.backend.model.session.PlaceCardRequest;
import org.dariusturcu.backend.model.session.PlacementPreviewDTO;
import org.dariusturcu.backend.model.session.Player;
import org.dariusturcu.backend.model.session.PlayerCard;
import org.dariusturcu.backend.model.session.PlayerResultDTO;
import org.dariusturcu.backend.model.session.PlayerStatus;
import org.dariusturcu.backend.model.session.Round;
import org.dariusturcu.backend.model.session.RoundStatus;
import org.dariusturcu.backend.model.session.RoundLinkOutDTO;
import org.dariusturcu.backend.model.session.RoundTiming;
import org.dariusturcu.backend.model.session.SessionResultsDTO;
import org.dariusturcu.backend.model.session.SessionStatus;
import org.dariusturcu.backend.model.session.StartCustomSessionRequest;
import org.dariusturcu.backend.model.session.StartSessionWithSongsRequest;
import org.dariusturcu.backend.model.session.TitleArtistGuessRequest;
import org.dariusturcu.backend.model.song.Song;
import org.dariusturcu.backend.model.song.SongArtist;
import org.dariusturcu.backend.model.playlist.Playlist;
import org.dariusturcu.backend.difficulty.DifficultyTier;
import org.dariusturcu.backend.difficulty.DifficultyTunedSongSelector;
import org.dariusturcu.backend.difficulty.ScoredSong;
import org.dariusturcu.backend.repository.BetRepository;
import org.dariusturcu.backend.repository.GameSessionRepository;
import org.dariusturcu.backend.repository.GroupRepository;
import org.dariusturcu.backend.repository.GuessRepository;
import org.dariusturcu.backend.repository.PlayerRepository;
import org.dariusturcu.backend.repository.PlaylistRepository;
import org.dariusturcu.backend.repository.RoundRepository;
import org.dariusturcu.backend.repository.SongRepository;
import org.dariusturcu.backend.scheduling.GameSessionScheduler;
import org.dariusturcu.backend.security.util.SecurityUtils;
import org.dariusturcu.backend.util.GuessMatcher;
import org.dariusturcu.backend.util.YoutubeLinkParser;
import org.dariusturcu.backend.websocket.GuessResultEvent;
import org.dariusturcu.backend.websocket.SessionBroadcastEvent;
import org.dariusturcu.backend.websocket.SessionEventType;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

// Orchestrates the whole round-by-round game session lifecycle: init from the group's
// settings, round rotation, placement/betting/reveal/scoring, disconnect/leave/timeout
// handling, win condition, and the purge-on-completion cleanup. See DECISIONS.md for the
// round-rotation (fixed vs rotating DJ), timer-scheduling, and betting-concurrency design
// choices this class implements.
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class GameSessionService {

    private static final int MINIMUM_PLAYERS = 2;
    private static final int ACTIVE_PLAYER_TURN_TIMEOUT_SECONDS = 90;
    private static final int AUTO_ABANDON_MINUTES = 10;
    private static final int STARTING_TOKEN_COUNT = 2;
    private static final int FIRST_GAME_ROUND_NUMBER = 1;
    private static final int FIRST_RANK = 1;
    private static final int MINIMUM_CORRECT_ARTISTS_FOR_TOKEN = 1;

    private final GameSessionRepository gameSessionRepository;
    private final PlayerRepository playerRepository;
    private final RoundRepository roundRepository;
    private final GuessRepository guessRepository;
    private final BetRepository betRepository;
    private final GroupRepository groupRepository;
    private final SongRepository songRepository;
    private final PlaylistRepository playlistRepository;
    private final GroupService groupService;
    private final PlaylistAccessService playlistAccessService;
    private final PlaylistExpansionService playlistExpansionService;
    private final DifficultyTunedSongSelector difficultySelector;
    private final PendingSessionSongPool pendingPool;
    private final SessionMapper sessionMapper;
    private final SessionResultsStore resultsStore;
    private final GameSessionScheduler gameSessionScheduler;
    private final ApplicationEventPublisher eventPublisher;

    // Self-injected through the Spring proxy so that a scheduled callback (running on the
    // scheduler's own thread, not through a caller that already went through the proxy)
    // still gets this class's @Transactional boundary applied to each effect method.
    @Autowired
    @Lazy
    private GameSessionService self;

    // --- Session lifecycle -------------------------------------------------------

    // Triggered by GameSessionStartListener off GroupService's own GAME_SESSION_STARTED
    // broadcast, in the same transaction GroupService.startGameSession already opened:
    // wiring into that existing state transition rather than duplicating it.
    public GameSession startSession(Long groupId) {
        Group group = findGroup(groupId);

        List<Member> connectedMembers = connectedMembers(group);
        if (connectedMembers.size() < MINIMUM_PLAYERS) {
            throw new ConflictException("Not enough connected members to start a session");
        }

        List<Song> songPool = pendingPool.take(groupId)
                .map(this::resolvePoolSongs)
                .orElseGet(() -> defaultSongPool(group));
        requireEnoughSongs(songPool.size(), connectedMembers.size(), group.getWinConditionCardCount());

        GameSession session = new GameSession();
        session.setGroupId(groupId);
        session.setStatus(SessionStatus.IN_PROGRESS);
        session.setDjMode(group.getDjMode());
        session.setWinConditionCardCount(group.getWinConditionCardCount());
        session.setCurrentRoundNumber(0);
        session.setCurrentGameRoundNumber(FIRST_GAME_ROUND_NUMBER);
        session.setCreatedAt(Instant.now());

        int turnOrder = 0;
        for (Member member : connectedMembers) {
            Player player = new Player();
            player.setUser(member.getUser());
            player.setDisplayName(member.getDisplayName());
            player.setAvatarUrl(member.getAvatarUrl());
            player.setTurnOrder(turnOrder++);
            player.setTokenCount(STARTING_TOKEN_COUNT);
            player.setStatus(PlayerStatus.ACTIVE);
            player.setConnected(true);
            player.setTotalArtistsGuessed(0);
            player.setTotalTitlesGuessed(0);
            session.addPlayer(player);
        }

        for (Player player : session.getPlayers()) {
            Song anchorSong = songPool.remove(0);
            player.addCard(PlayerCard.of(anchorSong));
        }
        List<Long> songQueue = songPool.stream().map(Song::getId).collect(Collectors.toCollection(ArrayList::new));
        session.setSongQueue(songQueue);

        GameSession savedSession = gameSessionRepository.save(session);

        List<Player> ordered = savedSession.getPlayers().stream()
                .sorted(Comparator.comparingInt(Player::getTurnOrder))
                .toList();

        Player firstActive;
        Player firstDj;
        if (group.getDjMode() == DjMode.FIXED) {
            firstDj = fixedDjPlayer(group, ordered);
            savedSession.setFixedDjPlayerId(firstDj.getId());
            savedSession = gameSessionRepository.save(savedSession);
            firstActive = ordered.get((ordered.indexOf(firstDj) + 1) % ordered.size());
        } else {
            firstActive = ordered.get(0);
            firstDj = ordered.get(1);
        }

        createRound(savedSession, firstActive, firstDj);
        return savedSession;
    }

    // Difficulty-Based generation preview for the admin's review step: scores the
    // internationally known verified catalog for the group's connected members and
    // returns the requested tier's set without starting anything. The confirmation
    // call is startSessionWithSongs below, validated again on the way in.
    public List<GeneratedSongPreviewDTO> generateDifficultySet(Long groupId, GenerateDifficultySetRequest request) {
        Group group = findGroup(groupId);
        requireGroupAdmin(group);
        if (request == null || request.tier() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Difficulty generation needs a tier and a target card count");
        }

        List<Long> playerIds = connectedMembers(group).stream()
                .map(member -> member.getUser().getId())
                .toList();
        List<ScoredSong> selected =
                difficultySelector.selectInternationalForGroup(playerIds, request.tier(), request.targetCardCount());
        if (selected.size() < request.targetCardCount()) {
            throw new ConflictException("Not enough internationally known verified songs for a "
                    + request.targetCardCount() + "-card " + request.tier() + " set");
        }
        return selected.stream().map(this::toPreviewDTO).toList();
    }

    // Confirms a reviewed Difficulty-Based set into a session start.
    public GroupDetailDTO startSessionWithSongs(Long groupId, StartSessionWithSongsRequest request) {
        Group group = findGroup(groupId);
        requireGroupAdmin(group);
        if (request == null || request.songIds() == null || request.songIds().isEmpty()
                || request.songIds().stream().anyMatch(songId -> songId == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Starting with songs needs a non-empty list of song ids");
        }

        List<Long> songIds = request.songIds().stream().distinct().toList();
        return startWithStagedPool(group, songIds);
    }

    // Custom-mode start from exactly one source: an accessible playlist by id, or a
    // YouTube playlist link or id pasted directly.
    public GroupDetailDTO startCustomSession(Long groupId, StartCustomSessionRequest request) {
        Group group = findGroup(groupId);
        requireGroupAdmin(group);
        boolean hasPlaylistId = request != null && request.playlistId() != null;
        boolean hasPlaylistLink = request != null && request.playlistLink() != null && !request.playlistLink().isBlank();
        if (hasPlaylistId == hasPlaylistLink) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Custom start needs exactly one of a playlist id or a pasted playlist link");
        }

        List<Long> songIds = hasPlaylistId
                ? poolFromPlaylist(request.playlistId())
                : poolFromPlaylistLink(request.playlistLink());
        return startWithStagedPool(group, songIds);
    }

    private GroupDetailDTO startWithStagedPool(Group group, List<Long> songIds) {
        List<Member> connectedMembers = connectedMembers(group);
        if (connectedMembers.size() < MINIMUM_PLAYERS) {
            throw new ConflictException("Not enough connected members to start a session");
        }
        List<Song> resolvedPool = resolvePoolSongs(songIds);
        requireEnoughSongs(resolvedPool.size(), connectedMembers.size(), group.getWinConditionCardCount());

        pendingPool.stage(group.getId(), songIds);
        try {
            return groupService.startGameSession(group.getId());
        } catch (RuntimeException startFailure) {
            pendingPool.discard(group.getId());
            throw startFailure;
        }
    }

    private List<Long> poolFromPlaylist(Long playlistId) {
        Playlist playlist = playlistRepository.findById(playlistId)
                .orElseThrow(() -> new ResourceNotFoundException(ResourceType.PLAYLIST, playlistId));
        playlistAccessService.requireRead(playlist, SecurityUtils.getCurrentUser());
        return playlist.getSongs().stream().map(Song::getId).distinct().toList();
    }

    private List<Long> poolFromPlaylistLink(String playlistLink) {
        List<String> videoIds = playlistExpansionService.expandPlaylist(playlistLink);
        List<Long> songIds = videoIds.stream()
                .map(videoId -> songRepository.findByYoutubeId(videoId).stream().findFirst())
                .filter(Optional::isPresent)
                .map(match -> match.get().getId())
                .distinct()
                .toList();
        if (songIds.isEmpty()) {
            throw new ConflictException("None of the pasted playlist's videos match a catalog song");
        }
        return songIds;
    }

    private GeneratedSongPreviewDTO toPreviewDTO(ScoredSong scoredSong) {
        Song song = scoredSong.song();
        List<String> artistNames = song.getArtists().stream()
                .sorted(Comparator.comparingInt(SongArtist::getDisplayOrder))
                .map(SongArtist::getName)
                .toList();
        return new GeneratedSongPreviewDTO(song.getId(), song.getTitle(), artistNames, song.getReleaseYear());
    }

    // One starting card per player, plus enough rounds for every player to take a turn
    // toward the win condition, so a game with good placements can actually be won.
    private void requireEnoughSongs(int songCount, int playerCount, int winConditionCardCount) {
        int requiredSongCount = playerCount * winConditionCardCount;
        if (songCount < requiredSongCount) {
            throw new ConflictException("Not enough songs to start: this group needs at least " + requiredSongCount
                    + ", one starting card per player plus enough rounds to reach the win condition");
        }
    }

    private Group findGroup(Long groupId) {
        return groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException(ResourceType.GROUP, groupId));
    }

    private List<Member> connectedMembers(Group group) {
        return group.getMembers().stream()
                .filter(Member::isConnected)
                .sorted(Comparator.comparing(Member::getJoinedAt))
                .toList();
    }

    private List<Song> defaultSongPool(Group group) {
        return group.getPlaylists().stream()
                .flatMap(playlist -> playlist.getSongs().stream())
                .distinct()
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private List<Song> resolvePoolSongs(List<Long> songIds) {
        return songIds.stream()
                .map(songId -> songRepository.findById(songId)
                        .orElseThrow(() -> new ResourceNotFoundException(ResourceType.SONG, songId)))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private void requireGroupAdmin(Group group) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        boolean isAdmin = group.getMembers().stream()
                .anyMatch(member -> member.getUser().getId().equals(currentUserId) && member.isAdmin());
        if (!isAdmin) {
            throw new AccessDeniedException("Only the group admin can do this");
        }
    }

    private Player fixedDjPlayer(Group group, List<Player> ordered) {
        Long fixedDjMemberId = group.getFixedDjMemberId();
        if (fixedDjMemberId == null) {
            return ordered.get(0);
        }
        Long fixedDjUserId = group.getMembers().stream()
                .filter(member -> member.getId().equals(fixedDjMemberId))
                .map(member -> member.getUser().getId())
                .findFirst()
                .orElse(null);
        if (fixedDjUserId == null) {
            return ordered.get(0);
        }
        return ordered.stream()
                .filter(player -> player.getUser().getId().equals(fixedDjUserId))
                .findFirst()
                .orElse(ordered.get(0));
    }

    public SessionResultsDTO completeSession(Long sessionId) {
        GameSession session = getSession(sessionId);
        session.setStatus(SessionStatus.COMPLETED);
        gameSessionRepository.save(session);

        SessionResultsDTO results = buildResults(session);
        Set<Long> playerUserIds = session.getPlayers().stream()
                .map(player -> player.getUser().getId())
                .collect(Collectors.toSet());
        resultsStore.store(session.getGroupId(), results, playerUserIds);
        eventPublisher.publishEvent(new SessionBroadcastEvent(SessionEventType.SESSION_ENDED, session.getId(), results));

        Long groupId = session.getGroupId();
        purgeSession(session);
        groupService.recordGameSessionEnded(groupId);
        return results;
    }

    public void abandonSession(Long sessionId) {
        GameSession session = getSession(sessionId);
        session.setStatus(SessionStatus.ABANDONED);
        gameSessionRepository.save(session);
        eventPublisher.publishEvent(new SessionBroadcastEvent(SessionEventType.SESSION_ENDED, session.getId(), null));

        Long groupId = session.getGroupId();
        purgeSession(session);
        groupService.recordGameSessionEnded(groupId);
    }

    // Effect method for the auto-abandon timer: callable directly in a test with no real
    // waiting involved. A no-op if anyone has reconnected since the timer was scheduled.
    public void checkAutoAbandonEffect(Long sessionId) {
        GameSession session = gameSessionRepository.findById(sessionId).orElse(null);
        if (session == null || session.getStatus() != SessionStatus.IN_PROGRESS || session.getZeroConnectedSince() == null) {
            return;
        }
        abandonSession(sessionId);
    }

    private void purgeSession(GameSession session) {
        List<Round> rounds = roundRepository.findBySessionOrderByRoundNumberAsc(session);
        roundRepository.deleteAll(rounds);
        gameSessionRepository.delete(session);
    }

    private SessionResultsDTO buildResults(GameSession session) {
        List<Player> byCardCountDescending = session.getPlayers().stream()
                .sorted(Comparator.comparingInt((Player player) -> player.getTimeline().size()).reversed())
                .toList();
        List<PlayerResultDTO> ranking = byCardCountDescending.stream()
                .map(player -> new PlayerResultDTO(player.getId(), player.getDisplayName(), player.getTimeline().size(),
                        competitionRank(byCardCountDescending, candidate -> candidate.getTimeline().size(), player)))
                .toList();

        return new SessionResultsDTO(session.getGroupId(), ranking,
                leaderboard(session, Player::getTotalArtistsGuessed),
                leaderboard(session, Player::getTotalTitlesGuessed));
    }

    private List<LeaderboardEntryDTO> leaderboard(GameSession session, ToIntFunction<Player> value) {
        List<Player> byValueDescending = session.getPlayers().stream()
                .sorted(Comparator.comparingInt(value).reversed())
                .toList();
        return byValueDescending.stream()
                .map(player -> new LeaderboardEntryDTO(player.getId(), player.getDisplayName(), value.applyAsInt(player),
                        competitionRank(byValueDescending, value, player)))
                .toList();
    }

    // Players with equal values share a place, and the next distinct value skips the
    // places they took, so two winners are both first and the next player is third.
    private int competitionRank(List<Player> players, ToIntFunction<Player> value, Player player) {
        int playerValue = value.applyAsInt(player);
        long playersAhead = players.stream().filter(candidate -> value.applyAsInt(candidate) > playerValue).count();
        return FIRST_RANK + (int) playersAhead;
    }

    // --- Round flow: placement, countdown, betting, reveal, scoring --------------

    // Relays where the active player's card currently sits so spectators can watch the
    // placement live. Silently ignored once the round has locked in, since a preview
    // racing the lock-in is harmless and not worth an error on the sender's socket.
    @Transactional(readOnly = true)
    public void previewPlacement(Long sessionId, Long userId, Integer position) {
        GameSession session = getSession(sessionId);
        Player player = findPlayerByUserId(session, userId);
        Round round = requireCurrentRound(session);

        if (!round.getActivePlayer().getId().equals(player.getId())) {
            throw new AccessDeniedException("Only the active player can preview a placement this round");
        }
        if (round.getStatus() != RoundStatus.AWAITING_PLACEMENT) {
            return;
        }
        if (position != null && (position < 0 || position > player.getTimeline().size())) {
            throw new IllegalArgumentException("Placement position is out of range for this player's timeline");
        }
        eventPublisher.publishEvent(new SessionBroadcastEvent(SessionEventType.PLACEMENT_PREVIEW, sessionId,
                new PlacementPreviewDTO(round.getId(), player.getId(), position)));
    }

    public void lockInPlacement(Long sessionId, Long userId, PlaceCardRequest request) {
        GameSession session = getSession(sessionId);
        Player player = findPlayerByUserId(session, userId);
        Round round = requireCurrentRound(session);

        if (!round.getActivePlayer().getId().equals(player.getId())) {
            throw new AccessDeniedException("Only the active player can place a card this round");
        }
        if (round.getStatus() != RoundStatus.AWAITING_PLACEMENT) {
            throw new ConflictException("This round has already locked in a placement");
        }

        boolean isCorrect = isPlacementCorrect(player, request.position(), round.getSong().getReleaseYear());
        round.setPlacedPosition(request.position());
        round.setPlacementCorrect(isCorrect);
        round.setLockedInAt(Instant.now());
        round.setStatus(RoundStatus.COUNTDOWN);
        Round savedRound = roundRepository.save(round);

        publishRoundEvent(SessionEventType.GUESS_LOCKED, session, savedRound);
        gameSessionScheduler.scheduleAfter(RoundTiming.LOCK_IN_COUNTDOWN,
                () -> self.startBettingWindowEffect(savedRound.getId()));
    }

    // Effect method for the post-countdown timer.
    public void startBettingWindowEffect(Long roundId) {
        Round round = roundRepository.findById(roundId).orElse(null);
        if (round == null || round.getStatus() != RoundStatus.COUNTDOWN) {
            return;
        }

        boolean anyEligibleBettorHasAToken = eligibleBettors(round).stream().anyMatch(bettor -> bettor.getTokenCount() > 0);
        if (!anyEligibleBettorHasAToken) {
            round.setStatus(RoundStatus.REVEALED);
            roundRepository.save(round);
            self.revealEffect(roundId);
            return;
        }

        round.setBettingWindowEndsAt(Instant.now().plus(RoundTiming.BETTING_WINDOW));
        round.setStatus(RoundStatus.BETTING);
        Round savedRound = roundRepository.save(round);
        publishRoundEvent(SessionEventType.BETTING_OPENED, savedRound.getSession(), savedRound);
        gameSessionScheduler.scheduleAfter(RoundTiming.BETTING_WINDOW, () -> self.revealEffect(roundId));
    }

    public boolean placeBet(Long sessionId, Long userId, int position) {
        GameSession session = getSession(sessionId);
        Player player = findPlayerByUserId(session, userId);
        Round round = requireCurrentRound(session);

        if (round.getStatus() != RoundStatus.BETTING) {
            throw new ConflictException("Betting is not open for this round");
        }
        boolean isEligible = eligibleBettors(round).stream().anyMatch(bettor -> bettor.getId().equals(player.getId()));
        if (!isEligible) {
            throw new AccessDeniedException("This player cannot bet on this round");
        }
        if (player.getTokenCount() <= 0) {
            throw new ConflictException("This player has no token to bet with");
        }
        if (position == round.getPlacedPosition()) {
            throw new IllegalArgumentException(
                    "Cannot bet on the gap the active player already placed their own card in");
        }
        // Validates the position is in range for the active player's own timeline, the
        // one shared timeline every bet this round is placed against; whether it's
        // actually correct is only decided at scoring time, once the song is revealed.
        isPlacementCorrect(round.getActivePlayer(), position, round.getSong().getReleaseYear());

        try {
            betRepository.insertBet(round.getId(), player.getId(), position, Instant.now());
        } catch (DataIntegrityViolationException exception) {
            return false;
        }
        playerRepository.deductToken(player.getId());

        // The token deduction clears the persistence context, so the round is read again.
        Round roundAfterBet = getRound(round.getId());
        publishRoundEvent(SessionEventType.BET_PLACED, session, roundAfterBet);
        closeBettingIfEveryoneDecided(roundAfterBet);
        return true;
    }

    // Only a player who could still bet this round can skip. The window closes early once
    // every eligible bettor holding a token has bet or skipped, so one player can't end
    // it for everyone else.
    public void skipBetting(Long sessionId, Long userId) {
        GameSession session = getSession(sessionId);
        Player player = findPlayerByUserId(session, userId);
        Round round = requireCurrentRound(session);
        if (round.getStatus() != RoundStatus.BETTING) {
            return;
        }
        boolean isEligible = eligibleBettors(round).stream().anyMatch(bettor -> bettor.getId().equals(player.getId()));
        if (!isEligible) {
            throw new AccessDeniedException("Only a player who can still bet this round can skip betting");
        }
        if (!round.getBettingSkippedPlayerIds().add(player.getId())) {
            return;
        }
        Round savedRound = roundRepository.save(round);
        publishRoundEvent(SessionEventType.BETTING_SKIP_VOTED, session, savedRound);
        closeBettingIfEveryoneDecided(savedRound);
    }

    private void closeBettingIfEveryoneDecided(Round round) {
        if (round.getStatus() != RoundStatus.BETTING) {
            return;
        }
        boolean anyTokenHolderUndecided = eligibleBettors(round).stream()
                .anyMatch(bettor -> bettor.getTokenCount() > 0 && !round.getBettingSkippedPlayerIds().contains(bettor.getId()));
        if (anyTokenHolderUndecided) {
            return;
        }
        round.setStatus(RoundStatus.REVEALED);
        roundRepository.save(round);
        self.revealEffect(round.getId());
    }

    // The current round's YouTube link-out, for the DJ to open playback on the real
    // YouTube page or app. Restricted to the round's DJ, and only before the reveal:
    // once the round is REVEALED or SCORED the song is already public and the DJ's
    // playback window is over.
    @Transactional(readOnly = true)
    public RoundLinkOutDTO getCurrentRoundLinkOut(Long sessionId, Long userId) {
        GameSession session = getSession(sessionId);
        Player player = findPlayerByUserId(session, userId);
        Round round = requireCurrentRound(session);

        if (!round.getDjPlayer().getId().equals(player.getId())) {
            throw new AccessDeniedException("Only the DJ for this round can open the YouTube link");
        }
        if (round.getStatus() == RoundStatus.REVEALED || round.getStatus() == RoundStatus.SCORED) {
            throw new ConflictException("The DJ's playback window for this round has closed");
        }

        Song song = round.getSong();
        return new RoundLinkOutDTO(
                round.getId(),
                round.getRoundNumber(),
                song.getYoutubeId(),
                YoutubeLinkParser.buildWatchUrl(song.getYoutubeId()),
                sessionMapper.artistNames(song),
                song.getTitle(),
                song.getReleaseYear(),
                song.getColor());
    }

    // Effect method for the betting-window-close timer, and called directly whenever the
    // window is skipped (no eligible bettor, or an explicit skip-betting action).
    public void revealEffect(Long roundId) {
        Round round = roundRepository.findById(roundId).orElse(null);
        if (round == null || round.getStatus() == RoundStatus.SCORED) {
            return;
        }
        if (round.getRevealedAt() == null) {
            round.setRevealedAt(Instant.now());
            round.setStatus(RoundStatus.REVEALED);
            round = roundRepository.save(round);
        }
        publishRoundEvent(SessionEventType.REVEAL_TRIGGERED, round.getSession(), round);
        self.scoreRoundEffect(roundId);
    }

    // Effect method applying the four scoring outcome rules from GAME_DESIGN.md, then
    // scheduling the next turn after the reveal hold, so every client shows the revealed
    // card before it moves on. Reaching the win condition doesn't end the game here: the
    // round plays out and advanceRound ends it once the rotation wraps. Also the reuse point for the turn-timeout and explicit-leave paths: both set
    // placementCorrect to false with no bets before calling this, which discards the
    // card exactly as a live wrong-guess-no-bets round would.
    public void scoreRoundEffect(Long roundId) {
        Round round = getRound(roundId);
        if (round.getStatus() == RoundStatus.SCORED) {
            return;
        }
        GameSession session = round.getSession();
        Player activePlayer = round.getActivePlayer();
        Player cardWinner = null;

        if (Boolean.TRUE.equals(round.getPlacementCorrect())) {
            activePlayer.insertCardAt(PlayerCard.of(round.getSong()), round.getPlacedPosition());
            cardWinner = activePlayer;
        } else {
            // Positions are unique per round, so at most one accepted bet can sit on the
            // gap that's objectively correct for the active player's own timeline.
            Bet winningBet = betRepository.findByRoundId(round.getId()).stream()
                    .filter(bet -> isPlacementCorrect(activePlayer, bet.getPosition(), round.getSong().getReleaseYear()))
                    .findFirst()
                    .orElse(null);
            if (winningBet != null) {
                Player bettor = winningBet.getPlayer();
                int insertionIndex = correctInsertionIndex(bettor, round.getSong().getReleaseYear());
                bettor.insertCardAt(PlayerCard.of(round.getSong()), insertionIndex);
                cardWinner = bettor;
            }
        }

        round.setStatus(RoundStatus.SCORED);
        round.setScoredAt(Instant.now());
        Round savedRound = roundRepository.save(round);
        playerRepository.save(activePlayer);
        if (cardWinner != null && cardWinner != activePlayer) {
            playerRepository.save(cardWinner);
        }

        publishRoundEvent(SessionEventType.ROUND_SCORED, session, savedRound);

        long remainingActivePlayers = session.getPlayers().stream()
                .filter(candidate -> candidate.getStatus() == PlayerStatus.ACTIVE)
                .count();
        if (remainingActivePlayers < MINIMUM_PLAYERS) {
            // Fewer than two players left to take the active-player and DJ roles, so the
            // round can't continue. With someone already at the win condition the game
            // completes on the current standings; otherwise it's an abandonment (no
            // results export). See DECISIONS.md.
            if (anyPlayerHasWon(session)) {
                completeSession(session.getId());
            } else {
                abandonSession(session.getId());
            }
        } else {
            gameSessionScheduler.scheduleAfter(RoundTiming.REVEAL_HOLD, () -> self.advanceRoundEffect(roundId));
        }
    }

    // Effect method for the reveal-hold timer. A no-op unless the scored round is still the
    // session's latest one and the session is still in progress, so a session that ended
    // or already moved on during the hold is left alone.
    public void advanceRoundEffect(Long roundId) {
        Round round = roundRepository.findById(roundId).orElse(null);
        if (round == null || round.getStatus() != RoundStatus.SCORED) {
            return;
        }
        GameSession session = round.getSession();
        if (session.getStatus() != SessionStatus.IN_PROGRESS) {
            return;
        }
        boolean isLatestRound = roundRepository.findTopBySessionOrderByRoundNumberDesc(session)
                .map(latestRound -> latestRound.getId().equals(round.getId()))
                .orElse(false);
        if (!isLatestRound) {
            return;
        }
        advanceRound(session, round);
    }

    private boolean isPlacementCorrect(Player player, int position, int newSongReleaseYear) {
        List<PlayerCard> timeline = player.getTimeline();
        if (position < 0 || position > timeline.size()) {
            throw new IllegalArgumentException("Placement position is out of range for this player's timeline");
        }
        boolean fitsAfterPreviousCard = position == 0 || timeline.get(position - 1).getReleaseYear() <= newSongReleaseYear;
        boolean fitsBeforeNextCard = position == timeline.size() || newSongReleaseYear <= timeline.get(position).getReleaseYear();
        return fitsAfterPreviousCard && fitsBeforeNextCard;
    }

    // Where a winning bettor's card lands on THEIR OWN timeline: the first gap whose
    // neighboring release years the new song's year fits between. Only ever called for
    // the bettor who won the card, never to validate a bet itself (isPlacementCorrect
    // does that, against the active player's timeline instead).
    private int correctInsertionIndex(Player player, int newSongReleaseYear) {
        List<PlayerCard> timeline = player.getTimeline();
        int index = 0;
        while (index < timeline.size() && timeline.get(index).getReleaseYear() <= newSongReleaseYear) {
            index++;
        }
        return index;
    }

    private List<Player> eligibleBettors(Round round) {
        Set<Long> playersWhoAlreadyBet = betRepository.findPlayerIdsByRoundId(round.getId());
        return round.getSession().getPlayers().stream()
                .filter(candidate -> candidate.getStatus() == PlayerStatus.ACTIVE)
                .filter(candidate -> !candidate.getId().equals(round.getActivePlayer().getId()))
                .filter(candidate -> !candidate.getId().equals(round.getDjPlayer().getId()))
                .filter(candidate -> !playersWhoAlreadyBet.contains(candidate.getId()))
                .toList();
    }

    // --- Artist/title guessing (independent of placement) -------------------------

    // Artists are guessed one at a time and the title once. A correct artist counts and
    // lets the player try another credited artist; a wrong one closes artist guessing for
    // the round. The active player earns one token per round for the title plus at least
    // one artist. See GAME_DESIGN.md.
    public void submitTitleArtistGuess(Long sessionId, Long userId, TitleArtistGuessRequest request) {
        GameSession session = getSession(sessionId);
        Player player = findPlayerByUserId(session, userId);
        Round round = requireCurrentRound(session);

        if (round.getDjPlayer().getId().equals(player.getId())) {
            throw new AccessDeniedException("The DJ cannot submit an artist/title guess");
        }
        if (round.getStatus() == RoundStatus.SCORED) {
            throw new ConflictException("This round has already been scored");
        }
        boolean hasArtistGuess = isNonBlank(request.guessedArtist());
        boolean hasTitleGuess = isNonBlank(request.guessedTitle());
        if (!hasArtistGuess && !hasTitleGuess) {
            throw new IllegalArgumentException("A guess needs an artist or a title");
        }

        List<Guess> earlierGuessesThisRound = playerGuesses(round, player);
        GuessStateDTO stateBefore = guessState(round, earlierGuessesThisRound, player);
        if (hasTitleGuess && stateBefore.titleGuessed()) {
            throw new ConflictException("The title was already guessed this round");
        }
        if (hasArtistGuess && stateBefore.artistGuessingClosed()) {
            throw new ConflictException("Artist guessing is closed for this round");
        }

        List<String> artistNames = songArtistNames(round.getSong());
        Set<String> alreadyGuessedArtists = correctlyGuessedArtists(earlierGuessesThisRound, artistNames);
        List<String> matchingArtists = hasArtistGuess
                ? artistNames.stream().filter(artistName -> GuessMatcher.matches(request.guessedArtist(), artistName)).toList()
                : List.of();
        boolean isArtistCorrect = matchingArtists.stream().anyMatch(artistName -> !alreadyGuessedArtists.contains(artistName));
        if (!matchingArtists.isEmpty() && !isArtistCorrect) {
            throw new ConflictException("That artist was already guessed this round");
        }
        boolean isTitleCorrect = hasTitleGuess && GuessMatcher.matches(request.guessedTitle(), round.getSong().getTitle());

        Guess guess = new Guess();
        guess.setRound(round);
        guess.setPlayer(player);
        guess.setGuessedArtist(hasArtistGuess ? request.guessedArtist() : null);
        guess.setGuessedTitle(hasTitleGuess ? request.guessedTitle() : null);
        guess.setArtistCorrect(isArtistCorrect);
        guess.setTitleCorrect(isTitleCorrect);
        guess.setCreatedAt(Instant.now());

        if (isArtistCorrect) {
            player.setTotalArtistsGuessed(player.getTotalArtistsGuessed() + 1);
        }
        if (isTitleCorrect) {
            player.setTotalTitlesGuessed(player.getTotalTitlesGuessed() + 1);
        }

        List<Guess> guessesAfter = new ArrayList<>(earlierGuessesThisRound);
        guessesAfter.add(guess);
        GuessStateDTO stateAfter = guessState(round, guessesAfter, player);
        if (stateAfter.tokenEarned() && !stateBefore.tokenEarned()) {
            player.setTokenCount(player.getTokenCount() + 1);
        }

        round.getGuesses().add(guess);
        guessRepository.save(guess);
        playerRepository.save(player);

        eventPublisher.publishEvent(new GuessResultEvent(player.getUser().getUsername(), sessionId,
                new GuessResultDTO(round.getId(), isArtistCorrect, isTitleCorrect, stateAfter)));
        if (isArtistCorrect || isTitleCorrect) {
            eventPublisher.publishEvent(new SessionBroadcastEvent(SessionEventType.GUESS_CORRECT, sessionId,
                    new CorrectGuessDTO(round.getId(), player.getId(), player.getDisplayName(),
                            isArtistCorrect, isTitleCorrect)));
        }
    }

    // The current round's guessing state for one player, so a reloaded page can restore
    // which guess fields are still open.
    @Transactional(readOnly = true)
    public GuessStateDTO getGuessState(Long sessionId, Long userId) {
        GameSession session = getSession(sessionId);
        Player player = findPlayerByUserId(session, userId);
        Round round = requireCurrentRound(session);
        return guessState(round, playerGuesses(round, player), player);
    }

    private List<Guess> playerGuesses(Round round, Player player) {
        return round.getGuesses().stream()
                .filter(existing -> existing.getPlayer().getId().equals(player.getId()))
                .sorted(Comparator.comparing(Guess::getCreatedAt))
                .toList();
    }

    private List<String> songArtistNames(Song song) {
        return song.getArtists().stream()
                .sorted(Comparator.comparingInt(SongArtist::getDisplayOrder))
                .map(SongArtist::getName)
                .toList();
    }

    // Each correct artist guess is credited to the first credited artist it matches that
    // no earlier guess this round already claimed.
    private Set<String> correctlyGuessedArtists(List<Guess> guesses, List<String> artistNames) {
        Set<String> guessedArtists = new LinkedHashSet<>();
        for (Guess guess : guesses) {
            if (!guess.isArtistCorrect()) {
                continue;
            }
            artistNames.stream()
                    .filter(artistName -> !guessedArtists.contains(artistName))
                    .filter(artistName -> GuessMatcher.matches(guess.getGuessedArtist(), artistName))
                    .findFirst()
                    .ifPresent(guessedArtists::add);
        }
        return guessedArtists;
    }

    private GuessStateDTO guessState(Round round, List<Guess> guesses, Player player) {
        List<String> artistNames = songArtistNames(round.getSong());
        int correctArtistCount = correctlyGuessedArtists(guesses, artistNames).size();
        boolean hasWrongArtistGuess = guesses.stream()
                .anyMatch(guess -> isNonBlank(guess.getGuessedArtist()) && !guess.isArtistCorrect());
        boolean titleGuessed = guesses.stream().anyMatch(guess -> isNonBlank(guess.getGuessedTitle()));
        boolean titleCorrect = guesses.stream().anyMatch(Guess::isTitleCorrect);
        boolean isActivePlayer = round.getActivePlayer().getId().equals(player.getId());
        boolean tokenEarned = isActivePlayer && titleCorrect && correctArtistCount >= MINIMUM_CORRECT_ARTISTS_FOR_TOKEN;
        return new GuessStateDTO(
                round.getId(),
                artistNames.size(),
                correctArtistCount,
                hasWrongArtistGuess || correctArtistCount >= artistNames.size(),
                titleGuessed,
                titleCorrect,
                tokenEarned);
    }

    private boolean isNonBlank(String value) {
        return value != null && !value.isBlank();
    }

    // --- Round rotation -------------------------------------------------------

    private void createRound(GameSession session, Player activePlayer, Player djPlayer) {
        Long nextSongId = popNextSongId(session);
        Song song = songRepository.findById(nextSongId)
                .orElseThrow(() -> new ResourceNotFoundException(ResourceType.SONG, nextSongId));

        Round round = new Round();
        round.setSession(session);
        round.setRoundNumber(session.getCurrentRoundNumber() + 1);
        round.setGameRoundNumber(session.getCurrentGameRoundNumber());
        round.setActivePlayer(activePlayer);
        round.setDjPlayer(djPlayer);
        round.setSong(song);
        round.setStatus(RoundStatus.AWAITING_PLACEMENT);
        round.setPlacementEndsAt(Instant.now().plus(RoundTiming.PLACEMENT_WINDOW));
        Round savedRound = roundRepository.save(round);

        session.setCurrentRoundNumber(savedRound.getRoundNumber());
        gameSessionRepository.save(session);

        publishRoundEvent(SessionEventType.ROUND_STARTED, session, savedRound);
        if (savedRound.getRoundNumber() > 1) {
            publishRoundEvent(SessionEventType.NEXT_ROUND, session, savedRound);
        }
        gameSessionScheduler.scheduleAfter(RoundTiming.PLACEMENT_WINDOW,
                () -> self.placementTimeoutEffect(savedRound.getId()));
    }

    private Long popNextSongId(GameSession session) {
        List<Long> songQueue = session.getSongQueue();
        if (songQueue.isEmpty()) {
            throw new ConflictException("No songs left in the group's playlists to continue the session");
        }
        return songQueue.remove(0);
    }

    // Rotates the active player to the next eligible player after the just-finished
    // round's active player, and resolves the next round's DJ per the session's DjMode.
    // See DECISIONS.md for the fixed-DJ-never-plays and rotating-DJ-is-next-up rules this
    // implements, resolving an ambiguity GAME_DESIGN.md left unspecified.
    private void advanceRound(GameSession session, Round finishedRound) {
        if (session.getSongQueue().isEmpty()) {
            // Out of songs before anyone reached the win condition: the game ends on the
            // current standings instead of stalling on the reveal.
            completeSession(session.getId());
            return;
        }
        int previousActiveTurnOrder = finishedRound.getActivePlayer().getTurnOrder();

        Player nextActivePlayer;
        Player nextDjPlayer;
        if (session.getDjMode() == DjMode.FIXED) {
            Player fixedDjPlayer = playerRepository.findById(session.getFixedDjPlayerId())
                    .orElseThrow(() -> new IllegalStateException("Fixed DJ player is missing from a FIXED-dj-mode session"));
            boolean isFixedDjStillActive = fixedDjPlayer.getStatus() == PlayerStatus.ACTIVE;
            Set<Long> excludedFromActiveRotation = isFixedDjStillActive ? Set.of(fixedDjPlayer.getId()) : Set.of();
            nextActivePlayer = findNextEligiblePlayer(session, previousActiveTurnOrder, excludedFromActiveRotation);
            nextDjPlayer = isFixedDjStillActive
                    ? fixedDjPlayer
                    : findNextEligiblePlayer(session, nextActivePlayer.getTurnOrder(), Set.of(nextActivePlayer.getId()));
        } else {
            nextActivePlayer = findNextEligiblePlayer(session, previousActiveTurnOrder, Set.of());
            nextDjPlayer = findNextEligiblePlayer(session, nextActivePlayer.getTurnOrder(), Set.of(nextActivePlayer.getId()));
        }

        // A round closes once the rotation reaches a player who already had a turn in it.
        // The game only ends there, so everyone still due a turn in the round a win
        // happened gets it, and players tied at the end share the win.
        boolean isRoundComplete = activePlayerIdsInCurrentRound(session).contains(nextActivePlayer.getId());
        if (isRoundComplete) {
            if (anyPlayerHasWon(session)) {
                completeSession(session.getId());
                return;
            }
            session.setCurrentGameRoundNumber(session.getCurrentGameRoundNumber() + 1);
        }

        createRound(session, nextActivePlayer, nextDjPlayer);
    }

    private Set<Long> activePlayerIdsInCurrentRound(GameSession session) {
        return roundRepository.findBySessionOrderByRoundNumberAsc(session).stream()
                .filter(round -> round.getGameRoundNumber() == session.getCurrentGameRoundNumber())
                .map(round -> round.getActivePlayer().getId())
                .collect(Collectors.toSet());
    }

    private boolean anyPlayerHasWon(GameSession session) {
        return session.getPlayers().stream().anyMatch(player -> player.hasWon(session.getWinConditionCardCount()));
    }

    private Player findNextEligiblePlayer(GameSession session, int fromTurnOrder, Set<Long> excludedPlayerIds) {
        Map<Integer, Player> playersByTurnOrder = session.getPlayers().stream()
                .collect(Collectors.toMap(Player::getTurnOrder, player -> player));
        int totalPlayers = playersByTurnOrder.size();

        for (int offset = 1; offset <= totalPlayers; offset++) {
            int candidateTurnOrder = (fromTurnOrder + offset) % totalPlayers;
            Player candidate = playersByTurnOrder.get(candidateTurnOrder);
            if (candidate != null && candidate.getStatus() == PlayerStatus.ACTIVE
                    && !excludedPlayerIds.contains(candidate.getId())) {
                return candidate;
            }
        }
        throw new ConflictException("No eligible player remains to take the next turn");
    }

    // --- Connection lifecycle: disconnect, reconnect, explicit leave, timeout ------

    public void disconnectPlayer(Long sessionId, Long userId) {
        GameSession session = getSession(sessionId);
        Player player = findPlayerByUserId(session, userId);

        player.setConnected(false);
        player.setDisconnectedAt(Instant.now());
        playerRepository.save(player);

        Round currentRound = getCurrentRound(session);
        if (currentRound != null && currentRound.getStatus() == RoundStatus.AWAITING_PLACEMENT
                && currentRound.getActivePlayer().getId().equals(player.getId())) {
            gameSessionScheduler.scheduleAfter(Duration.ofSeconds(ACTIVE_PLAYER_TURN_TIMEOUT_SECONDS),
                    () -> self.turnTimeoutEffect(player.getId()));
        }

        boolean everyPlayerDisconnected = session.getPlayers().stream().noneMatch(Player::isConnected);
        if (everyPlayerDisconnected && session.getZeroConnectedSince() == null) {
            session.setZeroConnectedSince(Instant.now());
            gameSessionRepository.save(session);
            gameSessionScheduler.scheduleAfter(Duration.ofMinutes(AUTO_ABANDON_MINUTES),
                    () -> self.checkAutoAbandonEffect(session.getId()));
        }
    }

    // A reconnect only ever restores isConnected; it never restores PlayerStatus.LEFT.
    // Once the 90-second turn timeout (or an explicit leave) has marked a player Left,
    // that is permanent for the rest of the session, matching GAME_DESIGN.md.
    public void reconnectPlayer(Long sessionId, Long userId) {
        GameSession session = getSession(sessionId);
        Player player = findPlayerByUserId(session, userId);
        if (player.isConnected() && player.getDisconnectedAt() == null && session.getZeroConnectedSince() == null) {
            return;
        }

        player.setConnected(true);
        player.setDisconnectedAt(null);
        playerRepository.save(player);

        if (session.getZeroConnectedSince() != null) {
            session.setZeroConnectedSince(null);
            gameSessionRepository.save(session);
        }
    }

    public void leavePlayer(Long sessionId, Long userId) {
        GameSession session = getSession(sessionId);
        Player player = findPlayerByUserId(session, userId);
        if (player.getStatus() == PlayerStatus.LEFT) {
            return;
        }

        player.setStatus(PlayerStatus.LEFT);
        player.setConnected(false);
        playerRepository.save(player);

        Round currentRound = getCurrentRound(session);
        if (currentRound != null && currentRound.getStatus() != RoundStatus.SCORED
                && currentRound.getActivePlayer().getId().equals(player.getId())) {
            currentRound.setPlacementCorrect(false);
            currentRound.setPlacedPosition(null);
            roundRepository.save(currentRound);
            self.scoreRoundEffect(currentRound.getId());
        }
    }

    // Effect method for the idle placement timer. The active player is still connected, so
    // unlike the turn timeout they aren't marked Left: the card is discarded exactly as a
    // wrong placement with no bets would be, and the game moves on. A no-op once the round
    // has locked in or been scored, or while the round's stored deadline hasn't passed.
    public void placementTimeoutEffect(Long roundId) {
        Round round = roundRepository.findById(roundId).orElse(null);
        if (round == null || round.getStatus() != RoundStatus.AWAITING_PLACEMENT
                || round.getSession().getStatus() != SessionStatus.IN_PROGRESS) {
            return;
        }
        if (round.getPlacementEndsAt() != null && Instant.now().isBefore(round.getPlacementEndsAt())) {
            return;
        }
        round.setPlacementCorrect(false);
        round.setPlacedPosition(null);
        roundRepository.save(round);
        self.scoreRoundEffect(roundId);
    }

    // Effect method for the active-player turn-timeout timer. A no-op if the player
    // reconnected (disconnectedAt cleared), already left, or the round has already moved
    // on by the time this fires.
    public void turnTimeoutEffect(Long playerId) {
        Player player = playerRepository.findById(playerId).orElse(null);
        if (player == null || player.getDisconnectedAt() == null || player.getStatus() == PlayerStatus.LEFT) {
            return;
        }

        GameSession session = player.getSession();
        Round currentRound = getCurrentRound(session);
        if (currentRound == null || currentRound.getStatus() != RoundStatus.AWAITING_PLACEMENT
                || !currentRound.getActivePlayer().getId().equals(playerId)) {
            return;
        }

        player.setStatus(PlayerStatus.LEFT);
        playerRepository.save(player);

        currentRound.setPlacementCorrect(false);
        currentRound.setPlacedPosition(null);
        roundRepository.save(currentRound);
        self.scoreRoundEffect(currentRound.getId());
    }

    // --- Recovery after a restart ------------------------------------------------

    // Round timers and socket presence live only in memory, so after a restart every
    // in-progress session gets its current round's timer rescheduled from the round's
    // stored timestamps, and every player starts out disconnected until one of their
    // sockets subscribes again. The auto-abandon timer covers a session nobody returns to.
    public void recoverInProgressSessions() {
        for (GameSession session : gameSessionRepository.findByStatus(SessionStatus.IN_PROGRESS)) {
            try {
                self.recoverSession(session.getId());
            } catch (RuntimeException exception) {
                log.warn("Recovery skipped for session {}: {}", session.getId(), exception.getMessage());
            }
        }
    }

    public void recoverSession(Long sessionId) {
        GameSession session = getSession(sessionId);
        Instant recoveredAt = Instant.now();
        for (Player player : session.getPlayers()) {
            if (player.isConnected()) {
                player.setConnected(false);
                player.setDisconnectedAt(recoveredAt);
                playerRepository.save(player);
            }
        }
        if (session.getZeroConnectedSince() == null) {
            session.setZeroConnectedSince(recoveredAt);
            gameSessionRepository.save(session);
        }
        gameSessionScheduler.scheduleAt(session.getZeroConnectedSince().plus(Duration.ofMinutes(AUTO_ABANDON_MINUTES)),
                () -> self.checkAutoAbandonEffect(sessionId));

        Round round = getCurrentRound(session);
        if (round != null) {
            rescheduleRoundTimer(round, recoveredAt);
        }
    }

    private void rescheduleRoundTimer(Round round, Instant recoveredAt) {
        Long roundId = round.getId();
        switch (round.getStatus()) {
            case AWAITING_PLACEMENT -> {
                Instant placementEndsAt = round.getPlacementEndsAt() != null
                        ? round.getPlacementEndsAt()
                        : recoveredAt.plus(RoundTiming.PLACEMENT_WINDOW);
                gameSessionScheduler.scheduleAt(placementEndsAt, () -> self.placementTimeoutEffect(roundId));
                Long activePlayerId = round.getActivePlayer().getId();
                gameSessionScheduler.scheduleAfter(Duration.ofSeconds(ACTIVE_PLAYER_TURN_TIMEOUT_SECONDS),
                        () -> self.turnTimeoutEffect(activePlayerId));
            }
            case COUNTDOWN -> gameSessionScheduler.scheduleAt(round.getLockedInAt().plus(RoundTiming.LOCK_IN_COUNTDOWN),
                    () -> self.startBettingWindowEffect(roundId));
            case BETTING -> gameSessionScheduler.scheduleAt(round.getBettingWindowEndsAt(), () -> self.revealEffect(roundId));
            case REVEALED -> gameSessionScheduler.scheduleAt(recoveredAt, () -> self.revealEffect(roundId));
            case SCORED -> gameSessionScheduler.scheduleAt(round.getScoredAt().plus(RoundTiming.REVEAL_HOLD),
                    () -> self.advanceRoundEffect(roundId));
        }
    }

    // --- Lookup helpers -------------------------------------------------------

    @Transactional(readOnly = true)
    public GameSession getSession(Long sessionId) {
        return gameSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Game session {id=" + sessionId + "} not found"));
    }

    @Transactional(readOnly = true)
    public GameSession getActiveSessionForGroup(Long groupId) {
        return gameSessionRepository.findByGroupId(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("No active game session found for group {id=" + groupId + "}"));
    }

    @Transactional(readOnly = true)
    public Round getCurrentRoundOrNull(GameSession session) {
        return getCurrentRound(session);
    }

    private Round getRound(Long roundId) {
        return roundRepository.findById(roundId)
                .orElseThrow(() -> new ResourceNotFoundException("Round {id=" + roundId + "} not found"));
    }

    private Round getCurrentRound(GameSession session) {
        return roundRepository.findTopBySessionOrderByRoundNumberDesc(session).orElse(null);
    }

    private Round requireCurrentRound(GameSession session) {
        Round round = getCurrentRound(session);
        if (round == null) {
            throw new ConflictException("This session has no active round");
        }
        return round;
    }

    private Player findPlayerByUserId(GameSession session, Long userId) {
        return session.getPlayers().stream()
                .filter(player -> player.getUser().getId().equals(userId))
                .findFirst()
                .orElseThrow(() -> new AccessDeniedException("This user is not a player in this session"));
    }

    private void publishRoundEvent(SessionEventType type, GameSession session, Round round) {
        eventPublisher.publishEvent(new SessionBroadcastEvent(type, session.getId(), sessionMapper.toRoundDTO(round)));
    }
}
