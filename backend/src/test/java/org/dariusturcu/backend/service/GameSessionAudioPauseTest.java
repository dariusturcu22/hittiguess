package org.dariusturcu.backend.service;

import org.dariusturcu.backend.difficulty.DifficultyTunedSongSelector;
import org.dariusturcu.backend.exception.ConflictException;
import org.dariusturcu.backend.model.group.AudioCheckStatus;
import org.dariusturcu.backend.model.group.DjMode;
import org.dariusturcu.backend.model.group.Group;
import org.dariusturcu.backend.model.group.Member;
import org.dariusturcu.backend.model.mapper.SessionMapper;
import org.dariusturcu.backend.model.session.GameSession;
import org.dariusturcu.backend.model.session.GameSessionDTO;
import org.dariusturcu.backend.model.session.PlaceCardRequest;
import org.dariusturcu.backend.model.session.Player;
import org.dariusturcu.backend.model.session.PlayerCard;
import org.dariusturcu.backend.model.session.PlayerStatus;
import org.dariusturcu.backend.model.session.Round;
import org.dariusturcu.backend.model.session.RoundStatus;
import org.dariusturcu.backend.model.session.RoundTiming;
import org.dariusturcu.backend.model.session.SessionStatus;
import org.dariusturcu.backend.model.song.ArtistRole;
import org.dariusturcu.backend.model.song.Song;
import org.dariusturcu.backend.model.song.SongArtist;
import org.dariusturcu.backend.model.user.Role;
import org.dariusturcu.backend.model.user.User;
import org.dariusturcu.backend.repository.BetRepository;
import org.dariusturcu.backend.repository.GameSessionRepository;
import org.dariusturcu.backend.repository.GroupRepository;
import org.dariusturcu.backend.repository.GuessRepository;
import org.dariusturcu.backend.repository.PlayerRepository;
import org.dariusturcu.backend.repository.PlaylistRepository;
import org.dariusturcu.backend.repository.RoundRepository;
import org.dariusturcu.backend.repository.SongRepository;
import org.dariusturcu.backend.repository.StoredSessionResultsRepository;
import org.dariusturcu.backend.scheduling.GameSessionScheduler;
import org.dariusturcu.backend.security.UserPrincipal;
import org.dariusturcu.backend.websocket.SessionBroadcastEvent;
import org.dariusturcu.backend.websocket.SessionEventType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GameSessionAudioPauseTest {

    private static final long SESSION_ID = 1L;
    private static final long GROUP_ID = 50L;
    private static final long ROUND_ID = 10L;
    private static final long FIRST_SONG_ID = 900L;
    private static final int WIN_CONDITION_CARD_COUNT = 5;
    private static final int STARTABLE_SONG_COUNT = 10;

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
    private PlaylistRepository playlistRepository;
    @Mock
    private GroupService groupService;
    @Mock
    private PlaylistAccessService playlistAccessService;
    @Mock
    private PlaylistExpansionService playlistExpansionService;
    @Mock
    private DifficultyTunedSongSelector difficultySelector;
    @Mock
    private GameSessionScheduler gameSessionScheduler;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private StoredSessionResultsRepository storedSessionResultsRepository;

    private final PendingSessionSongPool pendingPool = new PendingSessionSongPool();
    private SessionMapper sessionMapper;
    private GameSessionService gameSessionService;

    @BeforeEach
    void setUp() {
        sessionMapper = new SessionMapper(betRepository);
        gameSessionService = new GameSessionService(
                gameSessionRepository, playerRepository, roundRepository, guessRepository, betRepository,
                groupRepository, songRepository, playlistRepository, groupService, playlistAccessService,
                playlistExpansionService, difficultySelector, pendingPool, sessionMapper,
                new SessionResultsStore(storedSessionResultsRepository, JsonMapper.builder().build()),
                gameSessionScheduler, eventPublisher, new GameSongList(new Random(1L)), new PlacementPreviewGate());
        ReflectionTestUtils.setField(gameSessionService, "self", gameSessionService);
        lenient().when(gameSessionRepository.save(any(GameSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(playerRepository.save(any(Player.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(roundRepository.save(any(Round.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(betRepository.findPlayerIdsByRoundId(anyLong())).thenReturn(Set.of());
        lenient().when(betRepository.findByRoundId(anyLong())).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        pendingPool.discard(GROUP_ID);
    }

    private GameSession session(DjMode djMode) {
        GameSession session = new GameSession();
        session.setId(SESSION_ID);
        session.setGroupId(GROUP_ID);
        session.setStatus(SessionStatus.IN_PROGRESS);
        session.setDjMode(djMode);
        session.setWinConditionCardCount(WIN_CONDITION_CARD_COUNT);
        session.setCurrentRoundNumber(1);
        session.setCreatedAt(Instant.now());
        session.setSongQueue(new ArrayList<>(List.of(FIRST_SONG_ID + 1)));
        lenient().when(gameSessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        lenient().when(roundRepository.findBySessionOrderByRoundNumberAsc(session)).thenReturn(new ArrayList<>());
        return session;
    }

    private Song song(long id, int year) {
        Song song = new Song();
        song.setId(id);
        song.setTitle("Song " + id);
        song.setReleaseYear(year);
        SongArtist artist = new SongArtist();
        artist.setName("Artist " + id);
        artist.setRole(ArtistRole.MAIN);
        artist.setSong(song);
        song.getArtists().add(artist);
        lenient().when(songRepository.findById(id)).thenReturn(Optional.of(song));
        return song;
    }

    private Player player(GameSession session, long id, int turnOrder, AudioCheckStatus audioStatus) {
        User user = new User();
        user.setId(id);
        user.setUsername("user-" + id);
        Player player = new Player();
        player.setId(id);
        player.setUser(user);
        player.setDisplayName("Player " + id);
        player.setTurnOrder(turnOrder);
        player.setStatus(PlayerStatus.ACTIVE);
        player.setConnected(true);
        player.setAudioStatus(audioStatus);
        player.addCard(PlayerCard.of(song(id + 500, 1990)));
        session.addPlayer(player);
        return player;
    }

    private Round round(GameSession session, Player activePlayer, Player djPlayer) {
        Round round = new Round();
        round.setId(ROUND_ID);
        round.setSession(session);
        round.setRoundNumber(1);
        round.setGameRoundNumber(1);
        round.setActivePlayer(activePlayer);
        round.setDjPlayer(djPlayer);
        round.setSong(song(FIRST_SONG_ID, 2000));
        round.setStatus(RoundStatus.AWAITING_PLACEMENT);
        round.setPlacementEndsAt(Instant.now().plus(RoundTiming.PLACEMENT_WINDOW));
        round.setGuesses(new ArrayList<>());
        lenient().when(roundRepository.findById(ROUND_ID)).thenReturn(Optional.of(round));
        lenient().when(roundRepository.findTopBySessionOrderByRoundNumberDesc(session)).thenReturn(Optional.of(round));
        return round;
    }

    private List<SessionEventType> publishedSessionEvents() {
        ArgumentCaptor<Object> events = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, org.mockito.Mockito.atLeast(0)).publishEvent(events.capture());
        return events.getAllValues().stream()
                .filter(SessionBroadcastEvent.class::isInstance)
                .map(event -> ((SessionBroadcastEvent) event).type())
                .toList();
    }

    @Test
    void aRotatingPlayerWhoseShareStopsPausesTheGame() {
        GameSession session = session(DjMode.ROTATING);
        Player active = player(session, 1L, 0, AudioCheckStatus.OK);
        Player dj = player(session, 2L, 1, AudioCheckStatus.OK);
        round(session, active, dj);

        gameSessionService.reportPlayerAudioStatus(SESSION_ID, active.getUser().getId(), AudioCheckStatus.NOT_SHARED);

        assertThat(session.getPausedAt()).isNotNull();
        assertThat(publishedSessionEvents()).containsExactly(SessionEventType.SESSION_PAUSED);
        GameSessionDTO dto = sessionMapper.toSessionDTO(session, null);
        assertThat(dto.pausedForPlayerIds()).containsExactly(active.getId());
        assertThat(dto.audioRequiredPlayerIds()).containsExactly(active.getId(), dj.getId());
    }

    @Test
    void placementIsRefusedAndTimersDoNothingWhilePaused() {
        GameSession session = session(DjMode.ROTATING);
        Player active = player(session, 1L, 0, AudioCheckStatus.OK);
        Player dj = player(session, 2L, 1, AudioCheckStatus.NOT_SHARED);
        Round round = round(session, active, dj);
        round.setPlacementEndsAt(Instant.now().minusSeconds(1));
        session.setPausedAt(Instant.now());

        assertThatThrownBy(() -> gameSessionService.lockInPlacement(SESSION_ID, active.getUser().getId(), new PlaceCardRequest(0)))
                .isInstanceOf(ConflictException.class);
        gameSessionService.placementTimeoutEffect(ROUND_ID);

        assertThat(round.getStatus()).isEqualTo(RoundStatus.AWAITING_PLACEMENT);
        verify(roundRepository, never()).save(any(Round.class));
    }

    @Test
    void aPassedShareResumesTheGameWithAFreshPlacementDeadline() {
        GameSession session = session(DjMode.ROTATING);
        Player active = player(session, 1L, 0, AudioCheckStatus.OK);
        Player dj = player(session, 2L, 1, AudioCheckStatus.TESTING);
        Round round = round(session, active, dj);
        Instant staleDeadline = Instant.now().minusSeconds(30);
        round.setPlacementEndsAt(staleDeadline);
        session.setPausedAt(Instant.now().minusSeconds(60));

        gameSessionService.reportPlayerAudioStatus(SESSION_ID, dj.getUser().getId(), AudioCheckStatus.OK);

        assertThat(session.getPausedAt()).isNull();
        assertThat(round.getPlacementEndsAt()).isAfter(Instant.now().plus(RoundTiming.PLACEMENT_WINDOW).minusSeconds(5));
        verify(gameSessionScheduler).scheduleAt(any(Instant.class), any(Runnable.class));
        assertThat(publishedSessionEvents()).containsExactly(SessionEventType.SESSION_RESUMED);
    }

    @Test
    void aTestingShareKeepsTheGamePausedAndIsBroadcast() {
        GameSession session = session(DjMode.ROTATING);
        Player active = player(session, 1L, 0, AudioCheckStatus.OK);
        Player dj = player(session, 2L, 1, AudioCheckStatus.NOT_SHARED);
        round(session, active, dj);
        session.setPausedAt(Instant.now());

        gameSessionService.reportPlayerAudioStatus(SESSION_ID, dj.getUser().getId(), AudioCheckStatus.TESTING);

        assertThat(session.getPausedAt()).isNotNull();
        assertThat(publishedSessionEvents()).containsExactly(SessionEventType.AUDIO_STATUS_CHANGED);
    }

    @Test
    void inAFixedGameOnlyTheDjsShareMatters() {
        GameSession session = session(DjMode.FIXED);
        Player active = player(session, 1L, 0, null);
        Player fixedDj = player(session, 2L, 1, AudioCheckStatus.OK);
        player(session, 3L, 2, null);
        session.setFixedDjPlayerId(fixedDj.getId());
        round(session, active, fixedDj);

        gameSessionService.reportPlayerAudioStatus(SESSION_ID, active.getUser().getId(), AudioCheckStatus.NOT_SHARED);
        assertThat(session.getPausedAt()).isNull();

        gameSessionService.reportPlayerAudioStatus(SESSION_ID, fixedDj.getUser().getId(), AudioCheckStatus.NOT_SHARED);
        assertThat(session.getPausedAt()).isNotNull();
    }

    @Test
    void aPlayerWhoNeverSharedDoesNotHoldTheGameUp() {
        GameSession session = session(DjMode.ROTATING);
        Player active = player(session, 1L, 0, null);
        Player dj = player(session, 2L, 1, null);
        round(session, active, dj);

        assertThat(SessionAudioRules.blockingSharers(session)).isEmpty();
    }

    @Test
    void aPausedRotatingPlayerWhoLeavesResumesTheGame() {
        GameSession session = session(DjMode.ROTATING);
        Player active = player(session, 1L, 0, AudioCheckStatus.OK);
        Player dj = player(session, 2L, 1, AudioCheckStatus.OK);
        Player stopped = player(session, 3L, 2, AudioCheckStatus.NOT_SHARED);
        round(session, active, dj);
        session.setPausedAt(Instant.now());

        gameSessionService.leavePlayer(SESSION_ID, stopped.getUser().getId());

        assertThat(session.getPausedAt()).isNull();
        assertThat(publishedSessionEvents()).contains(SessionEventType.SESSION_RESUMED, SessionEventType.PLAYER_LEFT);
    }

    @Test
    void aFixedDjWhoLeavesHandsTheRoleToAPlayerWithAPassedShare() {
        GameSession session = session(DjMode.FIXED);
        Player active = player(session, 1L, 0, null);
        Player fixedDj = player(session, 2L, 1, AudioCheckStatus.OK);
        Player withoutShare = player(session, 3L, 2, null);
        Player withShare = player(session, 4L, 3, AudioCheckStatus.OK);
        session.setFixedDjPlayerId(fixedDj.getId());
        Round round = round(session, active, fixedDj);

        gameSessionService.leavePlayer(SESSION_ID, fixedDj.getUser().getId());

        assertThat(session.getFixedDjPlayerId()).isEqualTo(withShare.getId());
        assertThat(round.getDjPlayer()).isEqualTo(withShare);
        assertThat(withoutShare.getAudioStatus()).isNull();
        assertThat(session.getPausedAt()).isNull();
    }

    @Test
    void aFixedDjWhoLeavesWithNobodySharingPausesForTheirReplacement() {
        GameSession session = session(DjMode.FIXED);
        Player active = player(session, 1L, 0, null);
        Player fixedDj = player(session, 2L, 1, AudioCheckStatus.OK);
        Player replacement = player(session, 3L, 2, null);
        session.setFixedDjPlayerId(fixedDj.getId());
        Round round = round(session, active, fixedDj);

        gameSessionService.leavePlayer(SESSION_ID, fixedDj.getUser().getId());

        assertThat(session.getFixedDjPlayerId()).isEqualTo(replacement.getId());
        assertThat(round.getDjPlayer()).isEqualTo(replacement);
        assertThat(replacement.getAudioStatus()).isEqualTo(AudioCheckStatus.NOT_SHARED);
        assertThat(session.getPausedAt()).isNotNull();
        assertThat(publishedSessionEvents()).contains(SessionEventType.SESSION_PAUSED);
    }

    @Test
    void aSessionStartCarriesOverPassedShares() {
        Group group = new Group();
        group.setId(GROUP_ID);
        group.setDjMode(DjMode.ROTATING);
        group.setWinConditionCardCount(WIN_CONDITION_CARD_COUNT);
        Member passed = member(group, 1L, AudioCheckStatus.OK);
        member(group, 2L, AudioCheckStatus.TESTING);
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));
        List<Long> songIds = new ArrayList<>();
        for (long songId = FIRST_SONG_ID; songId < FIRST_SONG_ID + STARTABLE_SONG_COUNT; songId++) {
            song(songId, 2000);
            songIds.add(songId);
        }
        pendingPool.stage(GROUP_ID, songIds);
        when(gameSessionRepository.save(any(GameSession.class))).thenAnswer(invocation -> {
            GameSession saved = invocation.getArgument(0);
            saved.setId(SESSION_ID);
            long nextPlayerId = 1L;
            for (Player player : saved.getPlayers()) {
                if (player.getId() == null) {
                    player.setId(nextPlayerId++);
                }
            }
            return saved;
        });

        GameSession started = gameSessionService.startSession(GROUP_ID);

        assertThat(started.getPlayers()).extracting(Player::getAudioStatus).containsExactly(AudioCheckStatus.OK, null);
        assertThat(started.getPlayers().getFirst().getUser()).isEqualTo(passed.getUser());
        assertThat(pendingPool.peek(GROUP_ID)).isEmpty();
    }

    @Test
    void theLobbysStartChecksTheSongsAndOpensTheAudioCheck() {
        Group group = new Group();
        group.setId(GROUP_ID);
        group.setDjMode(DjMode.ROTATING);
        group.setWinConditionCardCount(WIN_CONDITION_CARD_COUNT);
        member(group, 1L, AudioCheckStatus.NOT_SHARED).setAdmin(true);
        member(group, 2L, AudioCheckStatus.NOT_SHARED);
        authenticateAs(group.getMembers().getFirst().getUser());
        when(groupRepository.findByIdForUpdate(GROUP_ID)).thenReturn(Optional.of(group));

        assertThatThrownBy(() -> gameSessionService.beginAudioCheck(GROUP_ID)).isInstanceOf(ConflictException.class);
        verify(groupService, never()).beginAudioCheck(GROUP_ID);

        org.dariusturcu.backend.model.playlist.Playlist playlist = new org.dariusturcu.backend.model.playlist.Playlist();
        for (long songId = FIRST_SONG_ID; songId < FIRST_SONG_ID + STARTABLE_SONG_COUNT; songId++) {
            playlist.getSongs().add(song(songId, 2000));
        }
        group.getPlaylists().add(playlist);

        gameSessionService.beginAudioCheck(GROUP_ID);

        verify(groupService).beginAudioCheck(GROUP_ID);
    }

    @Test
    void cancellingTheCheckDropsAStagedSongList() {
        pendingPool.stage(GROUP_ID, List.of(FIRST_SONG_ID));

        gameSessionService.cancelAudioCheck(GROUP_ID);

        verify(groupService).cancelAudioCheck(GROUP_ID);
        assertThat(pendingPool.peek(GROUP_ID)).isEmpty();
    }

    private Member member(Group group, long userId, AudioCheckStatus status) {
        User user = new User();
        user.setId(userId);
        user.setUsername("user-" + userId);
        user.setRole(Role.USER);
        Member member = new Member();
        member.setId(userId + 100);
        member.setUser(user);
        member.setDisplayName("Player " + userId);
        member.setConnected(true);
        member.setJoinedAt(Instant.now().plusSeconds(userId));
        member.setAudioCheckStatus(status);
        group.addMember(member);
        return member;
    }

    private void authenticateAs(User user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new UserPrincipal(user), null, null));
    }
}
