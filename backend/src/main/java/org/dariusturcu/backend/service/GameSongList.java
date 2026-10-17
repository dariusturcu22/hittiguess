package org.dariusturcu.backend.service;

import org.dariusturcu.backend.model.song.Song;
import org.dariusturcu.backend.model.song.SongArtist;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Builds the song list a session plays from: one entry per distinct song, keyed by a
 * normalized primary artist and title so catalog rows for the same recording collapse into a
 * single card, in a shuffled order. The same recording can appear in several catalog rows, so
 * deduplication compares normalized text rather than row identity. The list exists only inside
 * the started session; it is never written back as a playlist.
 */
@Component
public class GameSongList {

    static final String MISSING_VALUE = "";
    static final String NORMALIZED_KEY_SEPARATOR = "\u0001";
    private static final String NORMALIZED_WHITESPACE = " ";

    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern BRACKETED_QUALIFIER = Pattern.compile("\\([^)]*\\)|\\[[^\\]]*\\]|\\{[^}]*\\}");
    private static final Pattern DASH_SUFFIX_QUALIFIER = Pattern.compile("\\s+[-\\u2013\\u2014]\\s+.*");
    private static final Pattern PUNCTUATION = Pattern.compile("\\p{P}");
    private static final Pattern WHITESPACE_RUN = Pattern.compile("\\s+");

    private final Random random;

    public GameSongList() {
        this(new Random());
    }

    public GameSongList(Random random) {
        this.random = random;
    }

    public List<Song> build(List<Song> songs) {
        List<Song> deduplicated = distinct(songs);
        Collections.shuffle(deduplicated, random);
        return deduplicated;
    }

    // Keeps the first song for each normalized key, preserving the incoming order.
    public List<Song> distinct(List<Song> songs) {
        Set<String> seenKeys = new LinkedHashSet<>();
        List<Song> deduplicated = new ArrayList<>();
        for (Song song : songs) {
            if (seenKeys.add(normalizedKey(song))) {
                deduplicated.add(song);
            }
        }
        return deduplicated;
    }

    static String normalizedKey(Song song) {
        return normalize(primaryArtistName(song)) + NORMALIZED_KEY_SEPARATOR + normalize(song.getTitle());
    }

    private static String primaryArtistName(Song song) {
        return song.getArtists().stream()
                .min(Comparator.comparingInt(SongArtist::getDisplayOrder))
                .map(SongArtist::getName)
                .orElse(MISSING_VALUE);
    }

    // Case, diacritics, bracketed or dash-suffixed qualifiers, and punctuation are dropped so
    // two spellings of the same artist and title normalize to one key; whitespace runs collapse
    // to single spaces so a reordered or respaced title still matches.
    private static String normalize(String value) {
        if (value == null) {
            return MISSING_VALUE;
        }
        String caseAndDiacriticsFolded =
                COMBINING_MARKS.matcher(Normalizer.normalize(value.toLowerCase(Locale.ROOT), Normalizer.Form.NFD))
                        .replaceAll(MISSING_VALUE);
        String qualifiersRemoved = BRACKETED_QUALIFIER.matcher(caseAndDiacriticsFolded).replaceAll(MISSING_VALUE);
        String dashSuffixRemoved = DASH_SUFFIX_QUALIFIER.matcher(qualifiersRemoved).replaceAll(MISSING_VALUE);
        String punctuationRemoved = PUNCTUATION.matcher(dashSuffixRemoved).replaceAll(MISSING_VALUE);
        return WHITESPACE_RUN.matcher(punctuationRemoved).replaceAll(NORMALIZED_WHITESPACE).trim();
    }
}
