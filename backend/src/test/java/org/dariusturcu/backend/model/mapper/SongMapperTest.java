package org.dariusturcu.backend.model.mapper;

import org.dariusturcu.backend.model.song.CreateSongRequest;
import org.dariusturcu.backend.model.song.Song;
import org.dariusturcu.backend.model.song.SongDTO;
import org.dariusturcu.backend.model.song.UpdateSongRequest;
import org.dariusturcu.backend.model.user.User;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SongMapperTest {

    private final SongMapper songMapper = new SongMapper();

    private Song songWithGenre(String genre) {
        Song song = new Song();
        song.setId(1L);
        song.setTitle("Title");
        song.setYoutubeId("dQw4w9WgXcQ");
        song.setGenre(genre);
        User addedBy = new User();
        addedBy.setId(10L);
        addedBy.setUsername("added-by-user");
        song.setAddedBy(addedBy);
        return song;
    }

    private CreateSongRequest anyCreateRequest() {
        return new CreateSongRequest("Artist", "Title", 2000, "dQw4w9WgXcQ", "abcdef", "abcdef", null);
    }

    private UpdateSongRequest anyUpdateRequest() {
        return new UpdateSongRequest("Artist", "Title", 2000, "dQw4w9WgXcQ", "abcdef", "abcdef", null);
    }

    @Test
    void toDTOMapsGenreStraightThrough() {
        Song song = songWithGenre("Alternative Rock");

        SongDTO dto = songMapper.toDTO(song);

        assertThat(dto.genre()).isEqualTo("Alternative Rock");
    }

    @Test
    void toDTOMapsANullGenreAsNull() {
        Song song = songWithGenre(null);

        SongDTO dto = songMapper.toDTO(song);

        assertThat(dto.genre()).isNull();
    }

    @Test
    void toEntityNeverSetsAGenreSinceCreateSongRequestHasNoGenreField() {
        Song newSong = songMapper.toEntity(anyCreateRequest());

        assertThat(newSong.getGenre()).isNull();
    }

    @Test
    void updateEntityLeavesAnExistingGenreUntouchedSinceUpdateSongRequestHasNoGenreField() {
        Song song = songWithGenre("Alternative Rock");

        Song updatedSong = songMapper.updateEntity(song, anyUpdateRequest());

        assertThat(updatedSong.getGenre()).isEqualTo("Alternative Rock");
    }
}
