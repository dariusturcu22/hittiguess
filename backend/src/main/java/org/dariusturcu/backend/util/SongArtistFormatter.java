package org.dariusturcu.backend.util;

import org.dariusturcu.backend.model.song.ArtistRole;
import org.dariusturcu.backend.model.song.Song;
import org.dariusturcu.backend.model.song.SongArtist;

import java.util.stream.Collectors;

public final class SongArtistFormatter {

    private SongArtistFormatter() {
    }

    // A physical card prints the main artist(s), then "featuring" the featured ones
    // (see docs/GAME_DESIGN.md); role is a display concern only, guessing treats every
    // artist on the list identically.
    public static String formatCredit(Song song) {
        String mainArtists = song.getArtists().stream()
                .filter(artist -> artist.getRole() == ArtistRole.MAIN)
                .map(SongArtist::getName)
                .collect(Collectors.joining(" & "));

        String featuredArtists = song.getArtists().stream()
                .filter(artist -> artist.getRole() == ArtistRole.FEATURED)
                .map(SongArtist::getName)
                .collect(Collectors.joining(", "));

        return featuredArtists.isEmpty() ? mainArtists : mainArtists + " (feat. " + featuredArtists + ")";
    }
}
