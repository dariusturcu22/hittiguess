package org.dariusturcu.backend.service;

import org.dariusturcu.backend.model.group.DjMode;
import org.dariusturcu.backend.model.mapper.SessionMapper;
import org.dariusturcu.backend.model.session.Bet;
import org.dariusturcu.backend.model.session.GameSession;
import org.dariusturcu.backend.model.session.PlaceCardRequest;
import org.dariusturcu.backend.model.session.Player;
import org.dariusturcu.backend.model.session.PlayerCard;
import org.dariusturcu.backend.model.session.PlayerStatus;
import org.dariusturcu.backend.model.session.Round;
import org.dariusturcu.backend.model.session.RoundStatus;
import org.dariusturcu.backend.model.session.SessionStatus;
import org.dariusturcu.backend.model.session.TitleArtistGuessRequest;
import org.dariusturcu.backend.model.song.ArtistRole;
import org.dariusturcu.backend.model.song.Song;
import org.dariusturcu.backend.model.song.SongArtist;
import org.dariusturcu.backend.model.user.User;
import org.dariusturcu.backend.repository.BetRepository;
import org.dariusturcu.backend.repository.GameSessionRepository;
import org.dariusturcu.backend.repository.GroupRepository;
import org.dariusturcu.backend.repository.GuessRepository;
import org.dariusturcu.backend.repository.PlayerRepository;
import org.dariusturcu.backend.repository.RoundRepository;
import org.dariusturcu.backend.repository.SongRepository;
import org.dariusturcu.backend.scheduling.GameSessionScheduler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GameSessionServiceTest {

    @Mock
    private GameSessionRepository gameSessionRepository;
    @Mock
    private PlayerRepository playerRepository;
    @Mock
    private RoundRepository roundRepository;
    @Mock
    private GuessRepository guessRepository;
    @Mock
    private BetRepository betRepository;
    @Mock
    private GroupRepository groupRepository;
    @Mock
    private SongRepository songRepository;
    @Mock
    private GroupService groupService;
    @Mock
    private GameSessionScheduler gameSessionScheduler;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private final SessionResultsStore resultsStore = new SessionResultsStore();

    // Built in setUp(), not as a field initializer: SessionMapper needs the betRepository
    // mock, which Mockito injects only after this instance's fields are constructed.
    private SessionMapper sessionMapper;
    private GameSessionService gameSessionService;
    private final Map<Long, Song> songsById = new HashMap<>();

    @BeforeEach
    void setUp() {
        sessionMapper = new SessionMapper(betRepository);
        gameSessionService = new GameSessionService(
                gameSessionRepository, playerRepository, roundRepository, guessRepository, betRepository,
                groupRepository, songRepository, groupService, sessionMapper, resultsStore,
                gameSessionScheduler, eventPublisher);
        // Self-injection (see GameSessionService's @Lazy self field): the production
        // context resolves this through the Spring proxy, a plain unit test wires it
        // directly to the instance under test.
        ReflectionTestUtils.setField(gameSessionService, "self", gameSessionService);

        lenient().when(gameSessionRepository.save(any(GameSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(playerRepository.save(any(Player.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(roundRepository.save(any(Round.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(songRepository.findById(anyLong()))
                .thenAnswer(invocation -> Optional.ofNullable(songsById.get((Long) invocation.getArgument(0))));
        // No player has an existing bet on any round unless a test says otherwise.
        lenient().when(betRepository.findPlayerIdsByRoundId(anyLong())).thenReturn(Set.of());
        lenient().when(betRepository.findByRoundId(anyLong())).thenReturn(List.of());
    }

    private Song song(long id, String title, int year, SongArtist... artists) {
        Song song = new Song();
        song.setId(id);
        song.setTitle(title);
        song.setReleaseYear(year);
        for (SongArtist artist : artists) {
            artist.setSong(song);
        }
        song.getArtists().addAll(List.of(artists));
        songsById.put(id, song);
        return song;
    }

    private SongArtist artist(String name, ArtistRole role, int displayOrder) {
        SongArtist songArtist = new SongArtist();
        songArtist.setName(name);
        songArtist.setRole(role);
        songArtist.setDisplayOrder(displayOrder);
        return songArtist;
    }

    private Player player(GameSession session, long id, int turnOrder, PlayerStatus status) {
        Player player = new Player();
        player.setId(id);
        player.setSession(session);
        User user = new User();
        user.setId(id);
        player.setUser(user);
        player.setDisplayName("player-" + id);
        player.setTurnOrder(turnOrder);
        player.setTokenCount(0);
        player.setStatus(status);
        player.setConnected(true);
        player.setTotalArtistsGuessed(0);
        player.setTotalTitlesGuessed(0);
        player.setTimeline(new ArrayList<>());
        session.getPlayers().add(player);
        return player;
    }

    private PlayerCard anchorCard(Player player, Song song) {
        PlayerCard card = PlayerCard.of(song);
        player.addCard(card);
        return card;
    }

    // Adds an already-accepted bet directly to a round, bypassing placeBet: used by the
    // scoring tests, which start from the round already in its post-betting-window state.
    // scoreRoundEffect reads accepted bets through BetRepository, not Round's own lazy
    // association, so this keeps that stub in sync rather than mutating the entity.
    private Bet bet(Round round, Player bettor, int position) {
        Bet placedBet = new Bet();
        placedBet.setRound(round);
        placedBet.setPlayer(bettor);
        placedBet.setPosition(position);
        placedBet.setPlacedAt(Instant.now());
        round.getBets().add(placedBet);
        lenient().when(betRepository.findByRoundId(round.getId())).thenAnswer(invocation -> round.getBets());
        return placedBet;
    }

    private GameSession session(DjMode djMode, int winConditionCardCount) {
        GameSession session = new GameSession();
        session.setId(1L);
        session.setGroupId(50L);
        session.setStatus(SessionStatus.IN_PROGRESS);
        session.setDjMode(djMode);
        session.setWinConditionCardCount(winConditionCardCount);
        session.setCurrentRoundNumber(1);
        session.setCreatedAt(Instant.now());
        session.setPlayers(new ArrayList<>());
        session.setSongQueue(new ArrayList<>(List.of(900L, 901L, 902L)));
        song(900, "Filler Song One", 2000);
        song(901, "Filler Song Two", 2001);
        song(902, "Filler Song Three", 2002);
        lenient().when(gameSessionRepository.findById(1L)).thenReturn(Optional.of(session));
        lenient().when(roundRepository.findBySessionOrderByRoundNumberAsc(session)).thenReturn(new ArrayList<>());
        return session;
    }

    private Round round(GameSession session, long id, int roundNumber, Player activePlayer, Player djPlayer, Song song) {
        Round round = new Round();
        round.setId(id);
        round.setSession(session);
        round.setRoundNumber(roundNumber);
        round.setActivePlayer(activePlayer);
        round.setDjPlayer(djPlayer);
        round.setSong(song);
        round.setStatus(RoundStatus.AWAITING_PLACEMENT);
        round.setGuesses(new ArrayList<>());
        lenient().when(roundRepository.findById(id)).thenReturn(Optional.of(round));
        lenient().when(roundRepository.findTopBySessionOrderByRoundNumberDesc(session)).thenReturn(Optional.of(round));
        return round;
    }

    // --- Scoring outcome rules ------------------------------------------------

    @Test
    void correctPlacementKeepsTheCardEvenOnATiedReleaseYear() {
        GameSession session = session(DjMode.ROTATING, 2);
        Player active = player(session, 1L, 0, PlayerStatus.ACTIVE);
        Player dj = player(session, 2L, 1, PlayerStatus.ACTIVE);
        Song tiedYearAnchor = song(100, "Anchor", 1999);
        anchorCard(active, tiedYearAnchor);
        Song tiedYearRoundSong = song(101, "Round Song", 1999);
        Round round = round(session, 10L, 1, active, dj, tiedYearRoundSong);

        // Placing the tied-year song after the existing 1999 card is still correct:
        // either relative order of equal years counts.
        gameSessionService.lockInPlacement(session.getId(), active.getUser().getId(), new PlaceCardRequest(1));

        assertThat(round.getPlacementCorrect()).isTrue();
    }

    @Test
    void correctPlacementAwardsTheCardToTheActivePlayerAndEveryBetIsLostRegardless() {
        GameSession session = session(DjMode.ROTATING, 2);
        Player active = player(session, 1L, 0, PlayerStatus.ACTIVE);
        Player dj = player(session, 2L, 1, PlayerStatus.ACTIVE);
        Player bettor = player(session, 3L, 2, PlayerStatus.ACTIVE);
        anchorCard(active, song(100, "Active Anchor", 1990));
        anchorCard(bettor, song(101, "Bettor Anchor", 1990));
        anchorCard(dj, song(102, "DJ Anchor", 1990));
        Song roundSong = song(200, "Round Song", 2000);
        Round round = round(session, 10L, 1, active, dj, roundSong);
        round.setPlacementCorrect(true);
        round.setPlacedPosition(1);
        bet(round, bettor, 0);
        bettor.setTokenCount(0); // already spent placing the bet

        gameSessionService.scoreRoundEffect(round.getId());

        assertThat(active.getTimeline()).hasSize(2);
        assertThat(bettor.getTimeline()).hasSize(1);
        assertThat(bettor.getTokenCount()).isZero();
        verify(groupService).recordGameSessionEnded(session.getGroupId());
    }

    @Test
    void wrongPlacementWithABetOnTheObjectivelyCorrectGapGivesTheCardToThatBettorsOwnTimeline() {
        GameSession session = session(DjMode.ROTATING, 2);
        Player active = player(session, 1L, 0, PlayerStatus.ACTIVE);
        Player dj = player(session, 2L, 1, PlayerStatus.ACTIVE);
        Player bettor = player(session, 3L, 2, PlayerStatus.ACTIVE);
        anchorCard(active, song(100, "Active Anchor", 1990));
        anchorCard(bettor, song(101, "Bettor Anchor", 1995));
        anchorCard(dj, song(102, "DJ Anchor", 1990));
        Song roundSong = song(200, "Round Song", 2000);
        Round round = round(session, 10L, 1, active, dj, roundSong);
        round.setPlacementCorrect(false);
        // The active player wrongly placed before their own 1990 anchor; gap 1 (after it)
        // is objectively correct for the 2000 round song, and that's what the bettor bet.
        round.setPlacedPosition(0);
        bet(round, bettor, 1);

        gameSessionService.scoreRoundEffect(round.getId());

        assertThat(active.getTimeline()).hasSize(1);
        assertThat(bettor.getTimeline()).hasSize(2);
        // Computed automatically on the bettor's OWN timeline, not the gap they bet on.
        assertThat(bettor.getTimeline().get(1).getReleaseYear()).isEqualTo(2000);
        verify(groupService).recordGameSessionEnded(session.getGroupId());
    }

    @Test
    void wrongPlacementWithNoBetOnTheCorrectGapDiscardsTheCard() {
        GameSession session = session(DjMode.ROTATING, 10);
        Player active = player(session, 1L, 0, PlayerStatus.ACTIVE);
        Player dj = player(session, 2L, 1, PlayerStatus.ACTIVE);
        Player bettor = player(session, 3L, 2, PlayerStatus.ACTIVE);
        anchorCard(active, song(100, "Active Anchor One", 1980));
        anchorCard(active, song(103, "Active Anchor Two", 2010));
        anchorCard(bettor, song(101, "Bettor Anchor", 1990));
        anchorCard(dj, song(102, "DJ Anchor", 1990));
        Song roundSong = song(200, "Round Song", 2000);
        Round round = round(session, 10L, 1, active, dj, roundSong);
        round.setPlacementCorrect(false);
        // Gap 1 (between 1980 and 2010) is objectively correct; the active player wrongly
        // placed at gap 0, and the bettor bet gap 2, both wrong, so the card is discarded.
        round.setPlacedPosition(0);
        bet(round, bettor, 2);

        gameSessionService.scoreRoundEffect(round.getId());

        assertThat(active.getTimeline()).hasSize(2);
        assertThat(bettor.getTimeline()).hasSize(1);
    }

    @Test
    void wrongPlacementWithNoBetsAtAllDiscardsTheCard() {
        GameSession session = session(DjMode.ROTATING, 10);
        Player active = player(session, 1L, 0, PlayerStatus.ACTIVE);
        Player dj = player(session, 2L, 1, PlayerStatus.ACTIVE);
        anchorCard(active, song(100, "Active Anchor", 1990));
        anchorCard(dj, song(101, "DJ Anchor", 1990));
        Song roundSong = song(200, "Round Song", 2000);
        Round round = round(session, 10L, 1, active, dj, roundSong);
        round.setPlacementCorrect(false);
        round.setPlacedPosition(0);

        gameSessionService.scoreRoundEffect(round.getId());

        assertThat(active.getTimeline()).hasSize(1);
        assertThat(dj.getTimeline()).hasSize(1);
        verify(groupService, never()).recordGameSessionEnded(anyLong());
    }

    @Test
    void wrongPlacementWithSeveralBetsOnlyTheObjectivelyCorrectBettorWinsTheCard() {
        GameSession session = session(DjMode.ROTATING, 10);
        Player active = player(session, 1L, 0, PlayerStatus.ACTIVE);
        Player dj = player(session, 2L, 1, PlayerStatus.ACTIVE);
        Player correctBettor = player(session, 3L, 2, PlayerStatus.ACTIVE);
        Player wrongBettor = player(session, 4L, 3, PlayerStatus.ACTIVE);
        anchorCard(active, song(100, "Active Anchor One", 1980));
        anchorCard(active, song(103, "Active Anchor Two", 2010));
        anchorCard(correctBettor, song(101, "Correct Bettor Anchor", 1995));
        anchorCard(wrongBettor, song(104, "Wrong Bettor Anchor", 1970));
        anchorCard(dj, song(102, "DJ Anchor", 1990));
        Song roundSong = song(200, "Round Song", 2000);
        Round round = round(session, 10L, 1, active, dj, roundSong);
        round.setPlacementCorrect(false);
        round.setPlacedPosition(0);
        bet(round, correctBettor, 1); // between 1980 and 2010: objectively correct
        bet(round, wrongBettor, 2); // after 2010: wrong

        gameSessionService.scoreRoundEffect(round.getId());

        assertThat(active.getTimeline()).hasSize(2);
        assertThat(correctBettor.getTimeline()).hasSize(2);
        assertThat(correctBettor.getTimeline().get(1).getReleaseYear()).isEqualTo(2000);
        assertThat(wrongBettor.getTimeline()).hasSize(1);
    }

    // --- Betting -----------------------------------------------------------

    @Test
    void placingABetAtAValidGapInsertsItAndDeductsAToken() {
        GameSession session = session(DjMode.ROTATING, 10);
        Player active = player(session, 1L, 0, PlayerStatus.ACTIVE);
        Player dj = player(session, 2L, 1, PlayerStatus.ACTIVE);
        Player bettor = player(session, 3L, 2, PlayerStatus.ACTIVE);
        anchorCard(active, song(100, "Active Anchor", 1990));
        bettor.setTokenCount(1);
        Song roundSong = song(200, "Round Song", 2000);
        Round round = round(session, 10L, 1, active, dj, roundSong);
        round.setStatus(RoundStatus.BETTING);
        round.setPlacedPosition(1);

        boolean accepted = gameSessionService.placeBet(session.getId(), bettor.getUser().getId(), 0);

        assertThat(accepted).isTrue();
        verify(betRepository).insertBet(eq(round.getId()), eq(bettor.getId()), eq(0), any());
        verify(playerRepository).deductToken(bettor.getId());
    }

    @Test
    void twoEligiblePlayersCanBetOnDifferentGapsInTheSameRound() {
        GameSession session = session(DjMode.ROTATING, 10);
        Player active = player(session, 1L, 0, PlayerStatus.ACTIVE);
        Player dj = player(session, 2L, 1, PlayerStatus.ACTIVE);
        Player bettorOne = player(session, 3L, 2, PlayerStatus.ACTIVE);
        Player bettorTwo = player(session, 4L, 3, PlayerStatus.ACTIVE);
        anchorCard(active, song(100, "Active Anchor One", 1980));
        anchorCard(active, song(103, "Active Anchor Two", 2010));
        bettorOne.setTokenCount(1);
        bettorTwo.setTokenCount(1);
        Song roundSong = song(200, "Round Song", 2000);
        Round round = round(session, 10L, 1, active, dj, roundSong);
        round.setStatus(RoundStatus.BETTING);
        round.setPlacedPosition(1);

        boolean bettorOneAccepted = gameSessionService.placeBet(session.getId(), bettorOne.getUser().getId(), 0);
        boolean bettorTwoAccepted = gameSessionService.placeBet(session.getId(), bettorTwo.getUser().getId(), 2);

        assertThat(bettorOneAccepted).isTrue();
        assertThat(bettorTwoAccepted).isTrue();
        verify(betRepository).insertBet(eq(round.getId()), eq(bettorOne.getId()), eq(0), any());
        verify(betRepository).insertBet(eq(round.getId()), eq(bettorTwo.getId()), eq(2), any());
        verify(playerRepository).deductToken(bettorOne.getId());
        verify(playerRepository).deductToken(bettorTwo.getId());
    }

    @Test
    void twoEligiblePlayersBettingOnTheSameGapOnlyOneSucceedsAndTheLoserKeepsTheirToken() {
        GameSession session = session(DjMode.ROTATING, 10);
        Player active = player(session, 1L, 0, PlayerStatus.ACTIVE);
        Player dj = player(session, 2L, 1, PlayerStatus.ACTIVE);
        Player bettorOne = player(session, 3L, 2, PlayerStatus.ACTIVE);
        Player bettorTwo = player(session, 4L, 3, PlayerStatus.ACTIVE);
        anchorCard(active, song(100, "Active Anchor", 1990));
        bettorOne.setTokenCount(1);
        bettorTwo.setTokenCount(1);
        Song roundSong = song(200, "Round Song", 2000);
        Round round = round(session, 10L, 1, active, dj, roundSong);
        round.setStatus(RoundStatus.BETTING);
        round.setPlacedPosition(1);
        // Simulates the bets_round_position unique constraint: the second INSERT for the
        // same round and gap is a genuine conflict, mirroring what Postgres itself rejects.
        lenient().doThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"))
                .when(betRepository).insertBet(eq(round.getId()), eq(bettorTwo.getId()), eq(0), any());

        boolean bettorOneAccepted = gameSessionService.placeBet(session.getId(), bettorOne.getUser().getId(), 0);
        boolean bettorTwoAccepted = gameSessionService.placeBet(session.getId(), bettorTwo.getUser().getId(), 0);

        assertThat(bettorOneAccepted).isTrue();
        assertThat(bettorTwoAccepted).isFalse();
        verify(playerRepository).deductToken(bettorOne.getId());
        verify(playerRepository, never()).deductToken(bettorTwo.getId());
    }

    @Test
    void bettingOnTheGapTheActivePlayerAlreadyLockedInIsRejected() {
        GameSession session = session(DjMode.ROTATING, 10);
        Player active = player(session, 1L, 0, PlayerStatus.ACTIVE);
        Player dj = player(session, 2L, 1, PlayerStatus.ACTIVE);
        Player bettor = player(session, 3L, 2, PlayerStatus.ACTIVE);
        anchorCard(active, song(100, "Active Anchor", 1990));
        bettor.setTokenCount(1);
        Song roundSong = song(200, "Round Song", 2000);
        Round round = round(session, 10L, 1, active, dj, roundSong);
        round.setStatus(RoundStatus.BETTING);
        round.setPlacedPosition(1);

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                gameSessionService.placeBet(session.getId(), bettor.getUser().getId(), 1))
                .isInstanceOf(IllegalArgumentException.class);

        verify(betRepository, never()).insertBet(anyLong(), anyLong(), anyInt(), any());
    }

    @Test
    void aPlayerCannotBetTwiceInTheSameRound() {
        GameSession session = session(DjMode.ROTATING, 10);
        Player active = player(session, 1L, 0, PlayerStatus.ACTIVE);
        Player dj = player(session, 2L, 1, PlayerStatus.ACTIVE);
        Player bettor = player(session, 3L, 2, PlayerStatus.ACTIVE);
        anchorCard(active, song(100, "Active Anchor", 1990));
        bettor.setTokenCount(1);
        Song roundSong = song(200, "Round Song", 2000);
        Round round = round(session, 10L, 1, active, dj, roundSong);
        round.setStatus(RoundStatus.BETTING);
        round.setPlacedPosition(1);
        lenient().when(betRepository.findPlayerIdsByRoundId(round.getId())).thenReturn(Set.of(bettor.getId()));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                gameSessionService.placeBet(session.getId(), bettor.getUser().getId(), 0))
                .isInstanceOf(AccessDeniedException.class);

        verify(betRepository, never()).insertBet(anyLong(), anyLong(), anyInt(), any());
    }

    @Test
    void placingABetAtAnOutOfRangeGapForTheActivePlayersTimelineIsRejected() {
        GameSession session = session(DjMode.ROTATING, 10);
        Player active = player(session, 1L, 0, PlayerStatus.ACTIVE);
        Player dj = player(session, 2L, 1, PlayerStatus.ACTIVE);
        Player bettor = player(session, 3L, 2, PlayerStatus.ACTIVE);
        anchorCard(active, song(100, "Active Anchor", 1990));
        bettor.setTokenCount(1);
        Song roundSong = song(200, "Round Song", 2000);
        Round round = round(session, 10L, 1, active, dj, roundSong);
        round.setStatus(RoundStatus.BETTING);
        round.setPlacedPosition(1);

        // The active player's timeline has one card, so 0 and 1 are the only in-range gaps.
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                gameSessionService.placeBet(session.getId(), bettor.getUser().getId(), 2))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- Leaderboard tallies ---------------------------------------------------

    @Test
    void aNonActivePlayersGuessUpdatesTalliesWithoutTouchingTokensOrPlacement() {
        GameSession session = session(DjMode.ROTATING, 10);
        Player active = player(session, 1L, 0, PlayerStatus.ACTIVE);
        Player dj = player(session, 2L, 1, PlayerStatus.ACTIVE);
        Player bystander = player(session, 3L, 2, PlayerStatus.ACTIVE);
        Song roundSong = song(200, "Bohemian Rhapsody",
                1975, artist("Queen", ArtistRole.MAIN, 0));
        Round round = round(session, 10L, 1, active, dj, roundSong);

        gameSessionService.submitTitleArtistGuess(session.getId(), bystander.getUser().getId(),
                new TitleArtistGuessRequest("Queen", "Bohemian Rhapsody"));

        assertThat(bystander.getTotalArtistsGuessed()).isEqualTo(1);
        assertThat(bystander.getTotalTitlesGuessed()).isEqualTo(1);
        assertThat(bystander.getTokenCount()).isZero();
        assertThat(round.getPlacedPosition()).isNull();
    }

    @Test
    void aFeaturedArtistCorrectNameCountsTheSameAsAMainArtistName() {
        GameSession session = session(DjMode.ROTATING, 10);
        Player active = player(session, 1L, 0, PlayerStatus.ACTIVE);
        Player dj = player(session, 2L, 1, PlayerStatus.ACTIVE);
        Song roundSong = song(200, "Under Pressure", 1981,
                artist("Queen", ArtistRole.MAIN, 0),
                artist("David Bowie", ArtistRole.FEATURED, 1));
        round(session, 10L, 1, active, dj, roundSong);

        gameSessionService.submitTitleArtistGuess(session.getId(), active.getUser().getId(),
                new TitleArtistGuessRequest("David Bowie", "Under Pressure"));

        assertThat(active.getTotalArtistsGuessed()).isEqualTo(1);
        assertThat(active.getTotalTitlesGuessed()).isEqualTo(1);
        // A fully correct guess from the active player awards a token.
        assertThat(active.getTokenCount()).isEqualTo(1);
    }

    @Test
    void theDjCannotSubmitAnArtistTitleGuess() {
        GameSession session = session(DjMode.ROTATING, 10);
        Player active = player(session, 1L, 0, PlayerStatus.ACTIVE);
        Player dj = player(session, 2L, 1, PlayerStatus.ACTIVE);
        Song roundSong = song(200, "Song", 2000, artist("Artist", ArtistRole.MAIN, 0));
        round(session, 10L, 1, active, dj, roundSong);

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                gameSessionService.submitTitleArtistGuess(session.getId(), dj.getUser().getId(),
                        new TitleArtistGuessRequest("Artist", "Song")))
                .isInstanceOf(AccessDeniedException.class);
    }

    // --- Round rotation ---------------------------------------------------

    @Test
    void fixedDjModeNeverRotatesTheDesignatedDjIntoTheActivePlayerRole() {
        GameSession session = session(DjMode.FIXED, 10);
        Player fixedDj = player(session, 1L, 0, PlayerStatus.ACTIVE);
        Player playerB = player(session, 2L, 1, PlayerStatus.ACTIVE);
        Player playerC = player(session, 3L, 2, PlayerStatus.ACTIVE);
        session.setFixedDjPlayerId(fixedDj.getId());
        lenient().when(playerRepository.findById(fixedDj.getId())).thenReturn(Optional.of(fixedDj));
        anchorCard(fixedDj, song(100, "Anchor A", 1990));
        anchorCard(playerB, song(101, "Anchor B", 1990));
        anchorCard(playerC, song(102, "Anchor C", 1990));
        Song roundSong = song(200, "Round Song", 2000);
        Round round = round(session, 10L, 1, playerB, fixedDj, roundSong);
        round.setPlacementCorrect(false);

        gameSessionService.scoreRoundEffect(round.getId());

        org.mockito.ArgumentCaptor<Round> captor = org.mockito.ArgumentCaptor.forClass(Round.class);
        verify(roundRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        Round nextRound = captor.getAllValues().stream()
                .filter(candidate -> candidate.getRoundNumber() == 2)
                .findFirst().orElseThrow();

        assertThat(nextRound.getActivePlayer().getId()).isEqualTo(playerC.getId());
        assertThat(nextRound.getDjPlayer().getId()).isEqualTo(fixedDj.getId());
    }

    @Test
    void rotatingDjModeAssignsTheNextPlayerUpAsDj() {
        GameSession session = session(DjMode.ROTATING, 10);
        Player playerA = player(session, 1L, 0, PlayerStatus.ACTIVE);
        Player playerB = player(session, 2L, 1, PlayerStatus.ACTIVE);
        Player playerC = player(session, 3L, 2, PlayerStatus.ACTIVE);
        anchorCard(playerA, song(100, "Anchor A", 1990));
        anchorCard(playerB, song(101, "Anchor B", 1990));
        anchorCard(playerC, song(102, "Anchor C", 1990));
        Song roundSong = song(200, "Round Song", 2000);
        Round round = round(session, 10L, 1, playerA, playerB, roundSong);
        round.setPlacementCorrect(false);

        gameSessionService.scoreRoundEffect(round.getId());

        org.mockito.ArgumentCaptor<Round> captor = org.mockito.ArgumentCaptor.forClass(Round.class);
        verify(roundRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        Round nextRound = captor.getAllValues().stream()
                .filter(candidate -> candidate.getRoundNumber() == 2)
                .findFirst().orElseThrow();

        assertThat(nextRound.getActivePlayer().getId()).isEqualTo(playerB.getId());
        assertThat(nextRound.getDjPlayer().getId()).isEqualTo(playerC.getId());
    }

    @Test
    void roundRotationSkipsPlayersMarkedLeft() {
        GameSession session = session(DjMode.ROTATING, 10);
        Player playerA = player(session, 1L, 0, PlayerStatus.ACTIVE);
        Player playerB = player(session, 2L, 1, PlayerStatus.LEFT);
        Player playerC = player(session, 3L, 2, PlayerStatus.ACTIVE);
        anchorCard(playerA, song(100, "Anchor A", 1990));
        anchorCard(playerB, song(101, "Anchor B", 1990));
        anchorCard(playerC, song(102, "Anchor C", 1990));
        Song roundSong = song(200, "Round Song", 2000);
        Round round = round(session, 10L, 1, playerA, playerC, roundSong);
        round.setPlacementCorrect(false);

        gameSessionService.scoreRoundEffect(round.getId());

        org.mockito.ArgumentCaptor<Round> captor = org.mockito.ArgumentCaptor.forClass(Round.class);
        verify(roundRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        Round nextRound = captor.getAllValues().stream()
                .filter(candidate -> candidate.getRoundNumber() == 2)
                .findFirst().orElseThrow();

        // With only A and C active, rotation must skip Left player B entirely.
        assertThat(nextRound.getActivePlayer().getId()).isEqualTo(playerC.getId());
        assertThat(nextRound.getDjPlayer().getId()).isEqualTo(playerA.getId());
    }

    // --- Turn timeout ---------------------------------------------------

    @Test
    void turnTimeoutMarksTheActivePlayerLeftAndAdvancesTheRound() {
        GameSession session = session(DjMode.ROTATING, 10);
        Player active = player(session, 1L, 0, PlayerStatus.ACTIVE);
        Player dj = player(session, 2L, 1, PlayerStatus.ACTIVE);
        Player bystander = player(session, 3L, 2, PlayerStatus.ACTIVE);
        anchorCard(active, song(100, "Anchor A", 1990));
        anchorCard(dj, song(101, "Anchor B", 1990));
        anchorCard(bystander, song(103, "Anchor C", 1990));
        active.setDisconnectedAt(Instant.now().minusSeconds(90));
        Song roundSong = song(200, "Round Song", 2000);
        round(session, 10L, 1, active, dj, roundSong);
        lenient().when(playerRepository.findById(active.getId())).thenReturn(Optional.of(active));

        gameSessionService.turnTimeoutEffect(active.getId());

        assertThat(active.getStatus()).isEqualTo(PlayerStatus.LEFT);
    }

    @Test
    void turnTimeoutIsANoOpWhenThePlayerHasAlreadyReconnected() {
        GameSession session = session(DjMode.ROTATING, 10);
        Player active = player(session, 1L, 0, PlayerStatus.ACTIVE);
        Player dj = player(session, 2L, 1, PlayerStatus.ACTIVE);
        active.setDisconnectedAt(null);
        Song roundSong = song(200, "Round Song", 2000);
        round(session, 10L, 1, active, dj, roundSong);
        lenient().when(playerRepository.findById(active.getId())).thenReturn(Optional.of(active));

        gameSessionService.turnTimeoutEffect(active.getId());

        assertThat(active.getStatus()).isEqualTo(PlayerStatus.ACTIVE);
    }

    // --- Automatic reveal / betting window skip ---------------------------------------------------

    @Test
    void bettingWindowIsSkippedEntirelyWhenNoPlayerHoldsAToken() {
        GameSession session = session(DjMode.ROTATING, 10);
        Player active = player(session, 1L, 0, PlayerStatus.ACTIVE);
        Player dj = player(session, 2L, 1, PlayerStatus.ACTIVE);
        Player bystander = player(session, 3L, 2, PlayerStatus.ACTIVE);
        bystander.setTokenCount(0);
        anchorCard(active, song(100, "Anchor A", 1990));
        anchorCard(dj, song(101, "Anchor B", 1990));
        anchorCard(bystander, song(102, "Anchor C", 1990));
        Song roundSong = song(200, "Round Song", 2000);
        Round round = round(session, 10L, 1, active, dj, roundSong);
        round.setStatus(RoundStatus.COUNTDOWN);
        round.setPlacementCorrect(false);

        gameSessionService.startBettingWindowEffect(round.getId());

        // No token holders: the window is skipped, reveal and scoring both fire
        // automatically with nothing else triggering them.
        assertThat(round.getStatus()).isEqualTo(RoundStatus.SCORED);
        assertThat(round.getRevealedAt()).isNotNull();
    }

    @Test
    void bettingWindowOpensWhenAnEligibleBettorHoldsAToken() {
        GameSession session = session(DjMode.ROTATING, 10);
        Player active = player(session, 1L, 0, PlayerStatus.ACTIVE);
        Player dj = player(session, 2L, 1, PlayerStatus.ACTIVE);
        Player bystander = player(session, 3L, 2, PlayerStatus.ACTIVE);
        bystander.setTokenCount(1);
        Song roundSong = song(200, "Round Song", 2000);
        Round round = round(session, 10L, 1, active, dj, roundSong);
        round.setStatus(RoundStatus.COUNTDOWN);

        gameSessionService.startBettingWindowEffect(round.getId());

        assertThat(round.getStatus()).isEqualTo(RoundStatus.BETTING);
        assertThat(round.getBettingWindowEndsAt()).isNotNull();
        verify(gameSessionScheduler).scheduleAfter(any(), any());
    }
}
