package org.dariusturcu.backend.model.mapper;

import org.dariusturcu.backend.model.playlist.Playlist;
import org.dariusturcu.backend.model.playlist.PlaylistInvitePreviewDTO;
import org.dariusturcu.backend.model.playlist.PlaylistMembership;
import org.dariusturcu.backend.model.song.Song;
import org.dariusturcu.backend.model.user.User;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class PlaylistMapperTest {

    private static final Long OWNER_ID = 10L;
    private static final Long MEMBER_ID = 20L;

    private final PlaylistMapper playlistMapper = new PlaylistMapper(new SongMapper());

    private PlaylistMembership membershipFor(User user, String displayName) {
        PlaylistMembership membership = new PlaylistMembership();
        membership.setUser(user);
        membership.setDisplayName(displayName);
        membership.setCanRead(true);
        membership.setCanWrite(true);
        membership.setCanDelete(true);
        membership.setJoinedAt(Instant.now());
        return membership;
    }

    @Test
    void toInvitePreviewDTOCarriesTheNameColorSongCountAndFlagsEachMembersOwnerStatus() {
        User owner = new User();
        owner.setId(OWNER_ID);
        owner.setUsername("owner-user");

        User member = new User();
        member.setId(MEMBER_ID);
        member.setUsername("member-user");

        Playlist playlist = new Playlist();
        playlist.setName("Midnight Radio");
        playlist.setColor("cba6f7");
        playlist.setOwner(owner);
        playlist.getSongs().add(new Song());
        playlist.getSongs().add(new Song());
        playlist.getMemberships().add(membershipFor(owner, "Owner"));
        playlist.getMemberships().add(membershipFor(member, "Member"));

        PlaylistInvitePreviewDTO preview = playlistMapper.toInvitePreviewDTO(playlist);

        assertThat(preview.name()).isEqualTo("Midnight Radio");
        assertThat(preview.color()).isEqualTo("cba6f7");
        assertThat(preview.songCount()).isEqualTo(2);
        assertThat(preview.members()).hasSize(2);
        assertThat(preview.members().get(0).owner()).isTrue();
        assertThat(preview.members().get(1).owner()).isFalse();
    }
}
