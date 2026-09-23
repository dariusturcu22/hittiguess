package org.dariusturcu.backend.service;

import org.dariusturcu.backend.exception.ConflictException;
import org.dariusturcu.backend.exception.ResourceNotFoundException;
import org.dariusturcu.backend.exception.ResourceType;
import org.dariusturcu.backend.model.mapper.PlaylistMapper;
import org.dariusturcu.backend.model.mapper.SongMapper;
import org.dariusturcu.backend.model.playlist.Playlist;
import org.dariusturcu.backend.model.playlist.PlaylistBan;
import org.dariusturcu.backend.model.playlist.PlaylistDetailDTO;
import org.dariusturcu.backend.model.playlist.PlaylistInvitePreviewDTO;
import org.dariusturcu.backend.model.playlist.PlaylistMemberDTO;
import org.dariusturcu.backend.model.playlist.PlaylistMembership;
import org.dariusturcu.backend.model.playlist.PublicPlaylistSummaryDTO;
import org.dariusturcu.backend.model.playlist.SavedPlaylist;
import org.dariusturcu.backend.model.playlist.UpdateMembershipGrantsRequest;
import org.dariusturcu.backend.model.playlist.UpdatePlaylistRequest;
import org.dariusturcu.backend.model.song.CreateSongRequest;
import org.dariusturcu.backend.model.song.Song;
import org.dariusturcu.backend.model.song.SongDTO;
import org.dariusturcu.backend.model.song.UpdateSongRequest;
import org.dariusturcu.backend.model.song.VerificationStatus;
import org.dariusturcu.backend.model.ai.AiResponse;
import org.dariusturcu.backend.model.ai.SongMetadataResponse;
import org.dariusturcu.backend.model.user.User;
import org.dariusturcu.backend.repository.PlaylistBanRepository;
import org.dariusturcu.backend.repository.PlaylistMembershipRepository;
import org.dariusturcu.backend.repository.PlaylistRepository;
import org.dariusturcu.backend.repository.SavedPlaylistRepository;

import org.dariusturcu.backend.repository.SongRepository;
import org.dariusturcu.backend.security.util.SecurityUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@Service
@Transactional

public class PlaylistService {

    private static final String MAIN_ARTIST_MATCH_SEPARATOR = " & ";

    private final PlaylistRepository playlistRepository;
    private final SongRepository songRepository;
    private final PlaylistMapper playlistMapper;
    private final SongMapper songMapper;
    private final PlaylistAccessService playlistAccessService;
    private final PlaylistMembershipRepository playlistMembershipRepository;
    private final PlaylistBanRepository playlistBanRepository;
    private final SavedPlaylistRepository savedPlaylistRepository;
    private final CatalogSeedingService catalogSeedingService;
    private final SongMetadataService songMetadataService;

    @Autowired
    public PlaylistService(
            PlaylistRepository playlistRepository,
            SongRepository songRepository,
            PlaylistMapper playlistMapper,
            SongMapper songMapper,
            PlaylistAccessService playlistAccessService,
            PlaylistMembershipRepository playlistMembershipRepository,
            PlaylistBanRepository playlistBanRepository,
            SavedPlaylistRepository savedPlaylistRepository,
            CatalogSeedingService catalogSeedingService,
            SongMetadataService songMetadataService) {
        this.playlistRepository = playlistRepository;
        this.songRepository = songRepository;
        this.playlistMapper = playlistMapper;
        this.songMapper = songMapper;
        this.playlistAccessService = playlistAccessService;
        this.playlistMembershipRepository = playlistMembershipRepository;
        this.playlistBanRepository = playlistBanRepository;
        this.savedPlaylistRepository = savedPlaylistRepository;
        this.catalogSeedingService = catalogSeedingService;
        this.songMetadataService = songMetadataService;
    }

    public PlaylistService(
            PlaylistRepository playlistRepository,
            SongRepository songRepository,
            PlaylistMapper playlistMapper,
            SongMapper songMapper,
            PlaylistAccessService playlistAccessService,
            PlaylistMembershipRepository playlistMembershipRepository,
            PlaylistBanRepository playlistBanRepository,
            SavedPlaylistRepository savedPlaylistRepository,
            CatalogSeedingService catalogSeedingService) {
        this(playlistRepository, songRepository, playlistMapper, songMapper, playlistAccessService,
                playlistMembershipRepository, playlistBanRepository, savedPlaylistRepository,
                catalogSeedingService, null);
    }

    // VERIFIED is a pipeline-established lock and NEEDS_REVIEW is an LLM-reconciled year;
    // hand-editing either undermines the trust tier the pipeline already assigned it.
    // UNVERIFIED hasn't been through the pipeline at all yet, so there's
    // no established trust tier to protect there, same as the least-trusted MANUAL_ENTRY tier.
    private static final Set<VerificationStatus> EDITABLE_VERIFICATION_STATUSES =
            Set.of(VerificationStatus.UNVERIFIED, VerificationStatus.MANUAL_ENTRY);

    private Playlist findPlaylist(Long playlistId) {
        return playlistRepository.findById(playlistId)
                .orElseThrow(() -> new ResourceNotFoundException(ResourceType.PLAYLIST, playlistId));
    }

    private Playlist findPlaylistByInviteCode(String inviteCode) {
        return playlistRepository.findPlaylistByInviteCode(inviteCode)
                .orElseThrow(() -> new ResourceNotFoundException("Invite code {" + inviteCode + "} not found"));
    }

    private Song findSong(Long songId) {
        return songRepository.findById(songId)
                .orElseThrow(() -> new ResourceNotFoundException(ResourceType.SONG, songId));
    }

    private PlaylistMembership findMembership(Long playlistId, Long userId) {
        return playlistMembershipRepository.findByPlaylistIdAndUserId(playlistId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(ResourceType.PLAYLIST_MEMBER, userId));
    }

    private void checkSongBelongsToPlaylist(Song song, Long playlistId) {
        if (!songRepository.existsByIdAndPlaylistsId(song.getId(), playlistId)) {
            throw new ResourceNotFoundException(ResourceType.SONG_NOT_IN_PLAYLIST, song.getId(), playlistId);
        }
    }

    private void checkSongEditable(Song song) {
        if (!EDITABLE_VERIFICATION_STATUSES.contains(song.getVerificationStatus())) {
            throw new AccessDeniedException("This song has been verified and can no longer be edited directly");
        }
    }

    private void checkTargetIsNotOwner(Playlist playlist, Long targetUserId, String action) {
        if (playlist.getOwner().getId().equals(targetUserId)) {
            throw new ConflictException("The playlist owner can't " + action);
        }
    }

    @Transactional(readOnly = true)
    public PlaylistDetailDTO getPlaylist(
            Long playlistId) {

        Playlist playlist = findPlaylist(playlistId);
        playlistAccessService.requireRead(playlist, SecurityUtils.getCurrentUser());
        return playlistMapper.toDetailDTO(playlist);
    }

    @Transactional(readOnly = true)
    public PlaylistInvitePreviewDTO getInvitePreview(String inviteCode) {
        Playlist playlist = findPlaylistByInviteCode(inviteCode);
        return playlistMapper.toInvitePreviewDTO(playlist);
    }

    public PlaylistDetailDTO updatePlaylist(
            Long playlistId,
            UpdatePlaylistRequest request) {

        Playlist playlist = findPlaylist(playlistId);

        playlistAccessService.requireOwner(playlist, SecurityUtils.getCurrentUser());
        playlist = playlistMapper.updateEntity(playlist, request);

        playlistRepository.save(playlist);

        return playlistMapper.toDetailDTO(playlist);
    }

    public void deletePlaylist(Long playlistId) {
        Playlist playlist = findPlaylist(playlistId);
        playlistAccessService.requireOwner(playlist, SecurityUtils.getCurrentUser());
        playlistRepository.deleteGroupPlaylistLinks(playlistId);
        savedPlaylistRepository.deleteByPlaylistId(playlistId);
        playlistBanRepository.deleteByPlaylistId(playlistId);
        playlist.getSongs().clear();
        playlistRepository.delete(playlist);
    }

    @Transactional(readOnly = true)
    public SongDTO getSong(
            Long playlistId,
            Long songId) {

        Playlist playlist = findPlaylist(playlistId);

        playlistAccessService.requireRead(playlist, SecurityUtils.getCurrentUser());
        Song song = findSong(songId);

        checkSongBelongsToPlaylist(song, playlistId);

        return songMapper.toDTO(song);
    }

    // A manually-submitted YouTube id may already belong to a song the catalog resolved
    // through another path (bulk import, another user's own manual add), so this checks
    // for that before inserting rather than creating a duplicate row for the same video.
    public SongDTO createSong(
            Long playlistId,
            CreateSongRequest request) {

        Playlist playlist = findPlaylist(playlistId);
        User user = SecurityUtils.getCurrentUser();
        playlistAccessService.requireWrite(playlist, user);

        List<Song> existingSongs = songRepository.findByYoutubeId(request.youtubeId());
        if (!existingSongs.isEmpty()) {
            Song existingSong = existingSongs.get(0);
            if (!songRepository.existsByIdAndPlaylistsId(existingSong.getId(), playlistId)) {
                playlist.addSong(existingSong);
                playlistRepository.save(playlist);
            }
            return songMapper.toDTO(existingSong);
        }

        Song newSong = songMapper.toEntity(request);
        newSong.setAddedBy(user);
        applyConfirmedMetadata(newSong, request);

        Song savedSong = songRepository.save(newSong);
        playlist.addSong(savedSong);
        playlistRepository.save(playlist);
        catalogSeedingService.reEnqueueForPatientReprocessing(request.youtubeId());

        return songMapper.toDTO(savedSong);
    }

    private void applyConfirmedMetadata(Song song, CreateSongRequest request) {
        if (!Boolean.TRUE.equals(request.metadataConfirmed()) || songMetadataService == null) {
            return;
        }

        AiResponse response = songMetadataService.findCachedPreview(request.youtubeId())
                .orElseGet(() -> songMetadataService.resolveByYoutubeId(request.youtubeId()));
        if (!"SUCCESS".equals(response.status()) || response.content() == null) {
            return;
        }

        SongMetadataResponse metadata = response.content();
        if (!matchesSubmittedSong(metadata, request) || metadata.verificationStatus() == null) {
            return;
        }

        song.setConfidence(metadata.confidence());
        song.setVerificationStatus(VerificationStatus.valueOf(metadata.verificationStatus()));
        song.setWikidataSitelinksCount(metadata.sitelinksCount());
    }

    private boolean matchesSubmittedSong(SongMetadataResponse metadata, CreateSongRequest request) {
        String metadataArtists = metadata.mainArtists() == null
                ? ""
                : String.join(MAIN_ARTIST_MATCH_SEPARATOR, metadata.mainArtists());
        return normalized(metadata.title()).equals(normalized(request.title()))
                && normalized(metadataArtists).equals(normalized(request.artist()))
                && metadata.releaseYear() != null
                && metadata.releaseYear() == request.releaseYear();
    }

    private String normalized(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    public SongDTO updateSong(
            Long playlistId,
            Long songId,
            UpdateSongRequest request) {

        Playlist playlist = findPlaylist(playlistId);
        playlistAccessService.requireWrite(playlist, SecurityUtils.getCurrentUser());
        Song song = findSong(songId);

        checkSongBelongsToPlaylist(song, playlistId);
        checkSongEditable(song);

        song = songMapper.updateEntity(song, request);

        playlistRepository.save(playlist);

        return songMapper.toDTO(song);
    }

    public void deleteSong(
            Long playlistId,
            Long songId) {

        Playlist playlist = findPlaylist(playlistId);
        playlistAccessService.requireDelete(playlist, SecurityUtils.getCurrentUser());
        Song song = findSong(songId);

        checkSongBelongsToPlaylist(song, playlistId);

        // Unlinking only, never a real delete: a song is independent of any playlist it happens
        // to belong to, a song with zero playlists is a valid, permanent state, not a signal to
        // remove the row (see DECISIONS.md's 2026-09 "Song deletion" entries).
        playlist.removeSong(song);
        playlistRepository.save(playlist);
    }

    @Transactional(readOnly = true)
    public List<PlaylistMemberDTO> getMembers(Long playlistId) {
        Playlist playlist = findPlaylist(playlistId);
        playlistAccessService.requireRead(playlist, SecurityUtils.getCurrentUser());

        Long ownerId = playlist.getOwner().getId();
        return playlist.getMemberships().stream()
                .map(membership -> playlistMapper.toMemberDTO(membership, membership.getUser().getId().equals(ownerId)))
                .toList();
    }

    public PlaylistMemberDTO updateMemberGrants(Long playlistId, Long targetUserId, UpdateMembershipGrantsRequest request) {
        Playlist playlist = findPlaylist(playlistId);
        playlistAccessService.requireOwner(playlist, SecurityUtils.getCurrentUser());
        checkTargetIsNotOwner(playlist, targetUserId, "have their own grants changed");

        PlaylistMembership membership = findMembership(playlistId, targetUserId);

        if (request.canRead() != null) {
            membership.setCanRead(request.canRead());
        }
        if (request.canWrite() != null) {
            membership.setCanWrite(request.canWrite());
        }
        if (request.canDelete() != null) {
            membership.setCanDelete(request.canDelete());
        }

        PlaylistMembership savedMembership = playlistMembershipRepository.save(membership);
        return playlistMapper.toMemberDTO(savedMembership, false);
    }

    public void kickMember(Long playlistId, Long targetUserId) {
        Playlist playlist = findPlaylist(playlistId);
        playlistAccessService.requireOwner(playlist, SecurityUtils.getCurrentUser());
        checkTargetIsNotOwner(playlist, targetUserId, "be kicked");

        PlaylistMembership membership = findMembership(playlistId, targetUserId);

        playlist.removeMembership(membership);
        playlistRepository.save(playlist);
    }

    public PlaylistDetailDTO transferOwnership(Long playlistId, Long newOwnerId) {
        Playlist playlist = findPlaylist(playlistId);
        playlistAccessService.requireOwner(playlist, SecurityUtils.getCurrentUser());

        if (playlist.getOwner().getId().equals(newOwnerId)) {
            throw new ConflictException("This user is already the playlist owner");
        }

        PlaylistMembership newOwnerMembership = findMembership(playlistId, newOwnerId);
        playlist.setOwner(newOwnerMembership.getUser());

        Playlist savedPlaylist = playlistRepository.save(playlist);
        return playlistMapper.toDetailDTO(savedPlaylist);
    }

    public void banMember(Long playlistId, Long targetUserId) {
        Playlist playlist = findPlaylist(playlistId);
        playlistAccessService.requireOwner(playlist, SecurityUtils.getCurrentUser());
        checkTargetIsNotOwner(playlist, targetUserId, "be banned");

        PlaylistMembership membership = findMembership(playlistId, targetUserId);
        User bannedUser = membership.getUser();

        playlist.removeMembership(membership);
        playlistRepository.save(playlist);

        if (!playlistBanRepository.existsByPlaylistIdAndUserId(playlistId, targetUserId)) {
            PlaylistBan ban = new PlaylistBan();
            ban.setPlaylist(playlist);
            ban.setUser(bannedUser);
            ban.setBannedAt(Instant.now());
            playlistBanRepository.save(ban);
        }
    }

    public PlaylistDetailDTO publishPlaylist(Long playlistId) {
        Playlist playlist = findPlaylist(playlistId);
        playlistAccessService.requireOwner(playlist, SecurityUtils.getCurrentUser());

        playlist.setPublic(true);
        Playlist savedPlaylist = playlistRepository.save(playlist);

        return playlistMapper.toDetailDTO(savedPlaylist);
    }

    public PlaylistDetailDTO unpublishPlaylist(Long playlistId) {
        Playlist playlist = findPlaylist(playlistId);
        playlistAccessService.requireOwner(playlist, SecurityUtils.getCurrentUser());

        playlist.setPublic(false);
        Playlist savedPlaylist = playlistRepository.save(playlist);

        return playlistMapper.toDetailDTO(savedPlaylist);
    }

    @Transactional(readOnly = true)
    public List<PublicPlaylistSummaryDTO> getPublicPlaylists() {
        User currentUser = SecurityUtils.getCurrentUser();
        return playlistRepository.findByIsPublicTrue().stream()
                .filter(playlist -> !playlist.isOwnedBy(currentUser) && !isMember(playlist, currentUser))
                .map(playlistMapper::toPublicSummaryDTO)
                .toList();
    }

    private boolean isMember(Playlist playlist, User user) {
        if (user == null || user.getId() == null || playlist.getId() == null) {
            return false;
        }
        return playlistMembershipRepository.existsByPlaylistIdAndUserId(playlist.getId(), user.getId());
    }

    public PublicPlaylistSummaryDTO savePlaylist(Long playlistId) {
        User user = SecurityUtils.getCurrentUser();
        Playlist playlist = findPlaylist(playlistId);

        if (!playlist.isPublic()) {
            throw new ConflictException("Only a publicly published playlist can be saved");
        }
        if (playlist.isOwnedBy(user)) {
            throw new ConflictException("The playlist owner already has it in their own library");
        }
        if (savedPlaylistRepository.existsByUserIdAndPlaylistId(user.getId(), playlistId)) {
            throw new ConflictException("This playlist is already saved");
        }

        SavedPlaylist savedPlaylist = new SavedPlaylist();
        savedPlaylist.setUser(user);
        savedPlaylist.setPlaylist(playlist);
        savedPlaylist.setSavedAt(Instant.now());
        savedPlaylistRepository.save(savedPlaylist);

        return playlistMapper.toPublicSummaryDTO(playlist);
    }

    public void unsavePlaylist(Long playlistId) {
        User user = SecurityUtils.getCurrentUser();

        SavedPlaylist savedPlaylist = savedPlaylistRepository.findByUserIdAndPlaylistId(user.getId(), playlistId)
                .orElseThrow(() -> new ResourceNotFoundException(ResourceType.SAVED_PLAYLIST, playlistId));

        savedPlaylistRepository.delete(savedPlaylist);
    }
}
