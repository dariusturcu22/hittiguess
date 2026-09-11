package org.dariusturcu.backend.model.mapper;

import org.dariusturcu.backend.model.song.*;

import org.dariusturcu.backend.model.user.UserSummaryDTO;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class SongMapper {
    public SongDTO toDTO(Song song) {
        return new SongDTO(
                song.getId(),
                song.getArtists().stream().map(artist -> new SongArtistDTO(artist.getName(), artist.getRole())).toList(),
                song.getTitle(),
                song.getReleaseYear(),
                song.getYoutubeId(),
                song.getGradientColor1(),
                song.getGradientColor2(),
                song.getTags(),
                song.getCountry() != null ? song.getCountry() : Country.NONE,
                song.getVerificationStatus(),
                song.getConfidence(),
                new UserSummaryDTO(
                        song.getAddedBy().getId(),
                        song.getAddedBy().getUsername()
                )
        );
    }

    public Song toEntity(CreateSongRequest request) {
        Song newSong = new Song();
        setSingleMainArtist(newSong, request.artist());
        newSong.setTitle(request.title());
        newSong.setReleaseYear(request.releaseYear());
        newSong.setYoutubeId(request.youtubeId());
        newSong.setGradientColor1(request.gradientColor1());
        newSong.setGradientColor2(request.gradientColor2());
        newSong.setTags(request.tags() != null ? request.tags() : Set.of());
        newSong.setCountry(request.country() != null ? request.country() : Country.NONE);

        return newSong;
    }

    public Song updateEntity(Song song, UpdateSongRequest request) {
        // artist is @NotBlank on the request, always present, no need to guess whether it
        // was omitted; today's submission flow has no multi-artist entry, so an edit still
        // replaces the whole list with a single MAIN artist rather than adjusting one entry.
        setSingleMainArtist(song, request.artist());
        if (request.title() != null) {
            song.setTitle(request.title());
        }
        // releaseYear is required and @Min-validated on the request, always present by the time
        // it gets here, no need to guess whether it was omitted the way a 0 check would.
        song.setReleaseYear(request.releaseYear());
        if (request.youtubeId() != null) {
            song.setYoutubeId(request.youtubeId());
        }
        if (request.gradientColor1() != null) {
            song.setGradientColor1(request.gradientColor1());
        }
        if (request.gradientColor2() != null) {
            song.setGradientColor2(request.gradientColor2());
        }
        if (request.tags() != null) {
            song.setTags(request.tags());
        }
        if (request.country() != null) {
            song.setCountry(request.country());
        }
        return song;
    }

    private void setSingleMainArtist(Song song, String artistName) {
        SongArtist artist = new SongArtist();
        artist.setSong(song);
        artist.setName(artistName);
        artist.setRole(ArtistRole.MAIN);
        song.setArtists(List.of(artist));
    }
}
