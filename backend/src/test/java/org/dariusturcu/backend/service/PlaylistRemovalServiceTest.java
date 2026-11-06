package org.dariusturcu.backend.service;

import org.dariusturcu.backend.model.playlist.Playlist;
import org.dariusturcu.backend.repository.PlaylistBanRepository;
import org.dariusturcu.backend.repository.PlaylistRepository;
import org.dariusturcu.backend.repository.SavedPlaylistRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.inOrder;

@ExtendWith(MockitoExtension.class)
class PlaylistRemovalServiceTest {

    private static final Long PLAYLIST_ID = 1L;

    @Mock
    private PlaylistRepository playlistRepository;
    @Mock
    private SavedPlaylistRepository savedPlaylistRepository;
    @Mock
    private PlaylistBanRepository playlistBanRepository;

    @InjectMocks
    private PlaylistRemovalService playlistRemovalService;

    @Test
    void deleteWithDependentsRemovesGroupLinksSavesAndBansBeforeThePlaylistItself() {
        Playlist playlist = new Playlist();
        playlist.setId(PLAYLIST_ID);

        playlistRemovalService.deleteWithDependents(playlist);

        InOrder removalOrder = inOrder(playlistRepository, savedPlaylistRepository, playlistBanRepository);
        removalOrder.verify(playlistRepository).deleteGroupPlaylistLinks(PLAYLIST_ID);
        removalOrder.verify(savedPlaylistRepository).deleteByPlaylistId(PLAYLIST_ID);
        removalOrder.verify(playlistBanRepository).deleteByPlaylistId(PLAYLIST_ID);
        removalOrder.verify(playlistRepository).delete(playlist);
    }
}
