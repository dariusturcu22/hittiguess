package org.dariusturcu.backend.service;

import org.dariusturcu.backend.exception.ConflictException;
import org.dariusturcu.backend.exception.ResourceNotFoundException;
import org.dariusturcu.backend.exception.ResourceType;
import org.dariusturcu.backend.model.group.DjMode;
import org.dariusturcu.backend.model.group.Group;
import org.dariusturcu.backend.model.group.Member;
import org.dariusturcu.backend.model.mapper.SessionMapper;
import org.dariusturcu.backend.model.session.GameSession;
import org.dariusturcu.backend.model.session.Guess;
import org.dariusturcu.backend.model.session.LeaderboardEntryDTO;
import org.dariusturcu.backend.model.session.PlaceCardRequest;
import org.dariusturcu.backend.model.session.Player;
import org.dariusturcu.backend.model.session.PlayerCard;
import org.dariusturcu.backend.model.session.PlayerResultDTO;
import org.dariusturcu.backend.model.session.PlayerStatus;
import org.dariusturcu.backend.model.session.Round;
import org.dariusturcu.backend.model.session.RoundStatus;
import org.dariusturcu.backend.model.session.SessionResultsDTO;
import org.dariusturcu.backend.model.session.SessionStatus;
import org.dariusturcu.backend.model.session.TitleArtistGuessRequest;
import org.dariusturcu.backend.model.song.Song;
import org.dariusturcu.backend.model.song.SongArtist;
import org.dariusturcu.backend.repository.GameSessionRepository;
import org.dariusturcu.backend.repository.GroupRepository;
import org.dariusturcu.backend.repository.GuessRepository;
import org.dariusturcu.backend.repository.PlayerRepository;
import org.dariusturcu.backend.repository.RoundRepository;
import org.dariusturcu.backend.repository.SongRepository;
import org.dariusturcu.backend.scheduling.GameSessionScheduler;
import org.dariusturcu.backend.util.GuessMatcher;
import org.dariusturcu.backend.websocket.SessionBroadcastEvent;
import org.dariusturcu.backend.websocket.SessionEventType;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

// Orchestrates the whole round-by-round game session lifecycle: init from the group's
// settings, round rotation, placement/betting/reveal/scoring, disconnect/leave/timeout
// handling, win condition, and the purge-on-completion cleanup. See DECISIONS.md for the
// round-rotation (fixed vs rotating DJ), timer-scheduling, and betting-concurrency design
// choices this class implements.
@Service
@RequiredArgsConstructor
@Transactional
public class GameSessionService {

    private static final int MINIMUM_PLAYERS = 2;
    private static final int LOCK_IN_COUNTDOWN_SECONDS = 4;
    private static final int BETTING_WINDOW_SECONDS = 15;
    private static final int ACTIVE_PLAYER_TURN_TIMEOUT_SECONDS = 90;
    private static final int AUTO_ABANDON_MINUTES = 10;

    private final GameSessionRepository gameSessionRepository;
    private final PlayerRepository playerRepository;
    private final RoundRepository roundRepository;
    private final GuessRepository guessRepository;
    private final GroupRepository groupRepository;
    private final SongRepository songRepository;
    private final GroupService groupService;
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
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException(ResourceType.GROUP, groupId));

        List<Member> connectedMembers = group.getMembers().stream()
                .filter(Member::isConnected)
                .sorted(Comparator.comparing(Member::getJoinedAt))
                .toList();
        if (connectedMembers.size() < MINIMUM_PLAYERS) {
            throw new ConflictException("Not enough connected members to start a session");
        }

        List<Song> songPool = group.getPlaylists().stream()
                .flatMap(playlist -> playlist.getSongs().stream())
                .distinct()
                .collect(Collectors.toCollection(ArrayList::new));
        if (songPool.size() < connectedMembers.size() + 1) {
            throw new ConflictException("Not enough songs across the group's playlists to start a session");
        }

        GameSession session = new GameSession();
        session.setGroupId(groupId);
        session.setStatus(SessionStatus.IN_PROGRESS);
        session.setDjMode(group.getDjMode());
        session.setWinConditionCardCount(group.getWinConditionCardCount());
        session.setCurrentRoundNumber(0);
        session.setCreatedAt(Instant.now());

        int turnOrder = 0;
        for (Member member : connectedMembers) {
            Player player = new Player();
            player.setUser(member.getUser());
            player.setDisplayName(member.getDisplayName());
            player.setAvatarUrl(member.getAvatarUrl());
            player.setTurnOrder(turnOrder++);
            player.setTokenCount(0);
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
            firstDj = ordered.get(0);
            savedSession.setFixedDjPlayerId(firstDj.getId());
            savedSession = gameSessionRepository.save(savedSession);
            firstActive = ordered.get(1);
        } else {
            firstActive = ordered.get(0);
            firstDj = ordered.get(1);
        }

        createRound(savedSession, firstActive, firstDj);
        return savedSession;
    }

    public SessionResultsDTO completeSession(Long sessionId) {
        GameSession session = getSession(sessionId);
        session.setStatus(SessionStatus.COMPLETED);
        gameSessionRepository.save(session);

        SessionResultsDTO results = buildResults(session);
        resultsStore.store(session.getGroupId(), results);
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
        List<PlayerResultDTO> ranking = new ArrayList<>();
        int rank = 1;
        for (Player player : byCardCountDescending) {
            ranking.add(new PlayerResultDTO(player.getId(), player.getDisplayName(), player.getTimeline().size(), rank++));
        }

        List<LeaderboardEntryDTO> mostArtistsGuessed = session.getPlayers().stream()
                .sorted(Comparator.comparingInt(Player::getTotalArtistsGuessed).reversed())
                .map(player -> new LeaderboardEntryDTO(player.getId(), player.getDisplayName(), player.getTotalArtistsGuessed()))
                .toList();
        List<LeaderboardEntryDTO> mostTitlesGuessed = session.getPlayers().stream()
                .sorted(Comparator.comparingInt(Player::getTotalTitlesGuessed).reversed())
                .map(player -> new LeaderboardEntryDTO(player.getId(), player.getDisplayName(), player.getTotalTitlesGuessed()))
                .toList();

        return new SessionResultsDTO(session.getGroupId(), ranking, mostArtistsGuessed, mostTitlesGuessed);
    }

    // --- Round flow: placement, countdown, betting, reveal, scoring --------------

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
        gameSessionScheduler.scheduleAfter(Duration.ofSeconds(LOCK_IN_COUNTDOWN_SECONDS),
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

        round.setBettingWindowEndsAt(Instant.now().plusSeconds(BETTING_WINDOW_SECONDS));
        round.setStatus(RoundStatus.BETTING);
        roundRepository.save(round);
        gameSessionScheduler.scheduleAfter(Duration.ofSeconds(BETTING_WINDOW_SECONDS), () -> self.revealEffect(roundId));
    }

    public boolean placeBet(Long sessionId, Long userId) {
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

        int betsAccepted = roundRepository.tryAcceptBet(round.getId(), player, Instant.now());
        if (betsAccepted == 0) {
            return false;
        }
        playerRepository.deductToken(player.getId());

        Round updatedRound = getRound(round.getId());
        publishRoundEvent(SessionEventType.BET_PLACED, session, updatedRound);
        return true;
    }

    public void skipBetting(Long sessionId, Long userId) {
        GameSession session = getSession(sessionId);
        findPlayerByUserId(session, userId);
        Round round = requireCurrentRound(session);
        if (round.getStatus() != RoundStatus.BETTING) {
            return;
        }
        round.setStatus(RoundStatus.REVEALED);
        roundRepository.save(round);
        self.revealEffect(round.getId());
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
    // either completing the session (win condition reached) or advancing to the next
    // round. Also the reuse point for the turn-timeout and explicit-leave paths: both set
    // placementCorrect to false with no bettor before calling this, which discards the
    // card exactly as a live wrong-guess-no-bet round would.
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
        } else if (round.hasBettor()) {
            Player bettor = round.getBettorPlayer();
            int insertionIndex = correctInsertionIndex(bettor, round.getSong().getReleaseYear());
            bettor.insertCardAt(PlayerCard.of(round.getSong()), insertionIndex);
            cardWinner = bettor;
        }

        round.setStatus(RoundStatus.SCORED);
        round.setScoredAt(Instant.now());
        Round savedRound = roundRepository.save(round);
        playerRepository.save(activePlayer);
        if (cardWinner != null && cardWinner != activePlayer) {
            playerRepository.save(cardWinner);
        }

        publishRoundEvent(SessionEventType.ROUND_SCORED, session, savedRound);

        if (cardWinner != null && cardWinner.hasWon(session.getWinConditionCardCount())) {
            completeSession(session.getId());
            return;
        }

        long remainingActivePlayers = session.getPlayers().stream()
                .filter(candidate -> candidate.getStatus() == PlayerStatus.ACTIVE)
                .count();
        if (remainingActivePlayers < MINIMUM_PLAYERS) {
            // Fewer than two players left to take the active-player and DJ roles: the
            // round can't continue and no one reached the win condition, so this is an
            // abandonment (no results export) rather than a normal completion. See
            // DECISIONS.md.
            abandonSession(session.getId());
        } else {
            advanceRound(session, savedRound);
        }
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

    private int correctInsertionIndex(Player player, int newSongReleaseYear) {
        List<PlayerCard> timeline = player.getTimeline();
        int index = 0;
        while (index < timeline.size() && timeline.get(index).getReleaseYear() <= newSongReleaseYear) {
            index++;
        }
        return index;
    }

    private List<Player> eligibleBettors(Round round) {
        return round.getSession().getPlayers().stream()
                .filter(candidate -> candidate.getStatus() == PlayerStatus.ACTIVE)
                .filter(candidate -> !candidate.getId().equals(round.getActivePlayer().getId()))
                .filter(candidate -> !candidate.getId().equals(round.getDjPlayer().getId()))
                .toList();
    }

    // --- Artist/title guessing (independent of placement) -------------------------

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

        List<String> artistNames = round.getSong().getArtists().stream().map(SongArtist::getName).toList();
        boolean isArtistCorrect = isNonBlank(request.guessedArtist())
                && GuessMatcher.matchesAnyArtist(request.guessedArtist(), artistNames);
        boolean isTitleCorrect = isNonBlank(request.guessedTitle())
                && GuessMatcher.matches(request.guessedTitle(), round.getSong().getTitle());

        Guess guess = new Guess();
        guess.setRound(round);
        guess.setPlayer(player);
        guess.setGuessedArtist(request.guessedArtist());
        guess.setGuessedTitle(request.guessedTitle());
        guess.setArtistCorrect(isArtistCorrect);
        guess.setTitleCorrect(isTitleCorrect);
        guess.setCreatedAt(Instant.now());

        if (isArtistCorrect) {
            player.setTotalArtistsGuessed(player.getTotalArtistsGuessed() + 1);
        }
        if (isTitleCorrect) {
            player.setTotalTitlesGuessed(player.getTotalTitlesGuessed() + 1);
        }

        boolean isActivePlayer = round.getActivePlayer().getId().equals(player.getId());
        boolean alreadyAwardedThisRound = round.getGuesses().stream()
                .anyMatch(existing -> existing.getPlayer().getId().equals(player.getId()) && existing.isFullyCorrect());
        if (isActivePlayer && isArtistCorrect && isTitleCorrect && !alreadyAwardedThisRound) {
            player.setTokenCount(player.getTokenCount() + 1);
        }

        round.getGuesses().add(guess);
        guessRepository.save(guess);
        playerRepository.save(player);
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
        round.setActivePlayer(activePlayer);
        round.setDjPlayer(djPlayer);
        round.setSong(song);
        round.setStatus(RoundStatus.AWAITING_PLACEMENT);
        Round savedRound = roundRepository.save(round);

        session.setCurrentRoundNumber(savedRound.getRoundNumber());
        gameSessionRepository.save(session);

        publishRoundEvent(SessionEventType.ROUND_STARTED, session, savedRound);
        if (savedRound.getRoundNumber() > 1) {
            publishRoundEvent(SessionEventType.NEXT_ROUND, session, savedRound);
        }
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

        createRound(session, nextActivePlayer, nextDjPlayer);
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

    // --- Lookup helpers -------------------------------------------------------

    @Transactional(readOnly = true)
    public GameSession getSession(Long sessionId) {
        return gameSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Game session {id=" + sessionId + "} not found"));
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
