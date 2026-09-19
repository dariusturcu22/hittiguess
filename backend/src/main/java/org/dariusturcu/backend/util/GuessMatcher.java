package org.dariusturcu.backend.util;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

// Typo-tolerant string matching for in-round artist/title guesses, see DECISIONS.md's
// "Typo-tolerance threshold for in-round title/artist guesses" entry. Normalization
// (lowercase, strip diacritics, strip punctuation, collapse whitespace) runs first, then
// the normalized strings are compared with Damerau-Levenshtein edit distance, where an
// adjacent-letter transposition counts as one edit rather than two, against a flat
// budget of one regardless of string length.
public final class GuessMatcher {

    private static final int EDIT_DISTANCE_BUDGET = 1;
    private static final String NON_LETTER_OR_DIGIT_PATTERN = "[^\\p{L}\\p{Nd}\\s]";
    private static final String COMBINING_DIACRITICAL_MARK_PATTERN = "\\p{M}";
    private static final String WHITESPACE_RUN_PATTERN = "\\s+";

    private GuessMatcher() {
    }

    public static String normalize(String input) {
        if (input == null) {
            return "";
        }
        String withoutDiacritics = Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll(COMBINING_DIACRITICAL_MARK_PATTERN, "");
        String lowercased = withoutDiacritics.toLowerCase(Locale.ROOT);
        String withoutPunctuation = lowercased.replaceAll(NON_LETTER_OR_DIGIT_PATTERN, "");
        return withoutPunctuation.trim().replaceAll(WHITESPACE_RUN_PATTERN, " ");
    }

    public static boolean matches(String guess, String canonicalAnswer) {
        String normalizedGuess = normalize(guess);
        String normalizedAnswer = normalize(canonicalAnswer);
        if (normalizedGuess.isEmpty()) {
            return false;
        }
        return damerauLevenshteinDistance(normalizedGuess, normalizedAnswer) <= EDIT_DISTANCE_BUDGET;
    }

    // A song with more than one credited artist (main or featured) is matched if the
    // guess matches any single one of them.
    public static boolean matchesAnyArtist(String guess, List<String> artistNames) {
        return artistNames.stream().anyMatch(artistName -> matches(guess, artistName));
    }

    // Optimal string alignment (restricted Damerau-Levenshtein): standard Levenshtein
    // insertion/deletion/substitution, plus a transposition step that lets two adjacent,
    // swapped characters collapse into a single edit instead of two substitutions.
    // Sufficient for a budget of one: distinguishing it from the unrestricted algorithm
    // only matters at distances this rule never accepts anyway.
    private static int damerauLevenshteinDistance(String firstString, String secondString) {
        int firstLength = firstString.length();
        int secondLength = secondString.length();
        int[][] distance = new int[firstLength + 1][secondLength + 1];

        for (int rowIndex = 0; rowIndex <= firstLength; rowIndex++) {
            distance[rowIndex][0] = rowIndex;
        }
        for (int columnIndex = 0; columnIndex <= secondLength; columnIndex++) {
            distance[0][columnIndex] = columnIndex;
        }

        for (int rowIndex = 1; rowIndex <= firstLength; rowIndex++) {
            for (int columnIndex = 1; columnIndex <= secondLength; columnIndex++) {
                int substitutionCost = firstString.charAt(rowIndex - 1) == secondString.charAt(columnIndex - 1) ? 0 : 1;
                int deletion = distance[rowIndex - 1][columnIndex] + 1;
                int insertion = distance[rowIndex][columnIndex - 1] + 1;
                int substitution = distance[rowIndex - 1][columnIndex - 1] + substitutionCost;
                int bestSoFar = Math.min(Math.min(deletion, insertion), substitution);

                boolean canTranspose = rowIndex > 1 && columnIndex > 1
                        && firstString.charAt(rowIndex - 1) == secondString.charAt(columnIndex - 2)
                        && firstString.charAt(rowIndex - 2) == secondString.charAt(columnIndex - 1);
                if (canTranspose) {
                    int transposition = distance[rowIndex - 2][columnIndex - 2] + 1;
                    bestSoFar = Math.min(bestSoFar, transposition);
                }

                distance[rowIndex][columnIndex] = bestSoFar;
            }
        }

        return distance[firstLength][secondLength];
    }
}
