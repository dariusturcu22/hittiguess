package org.dariusturcu.backend.difficulty;

import org.springframework.stereotype.Component;

// Turns the available per-song signals into one difficulty score in the closed range
// [DIFFICULTY_SCORE_EASIEST, DIFFICULTY_SCORE_HARDEST], where a lower score is an easier
// song (placed correctly more often, more widely recognized) and a higher score is a harder
// one. Two signals feed it: the play-derived correct-placement fraction, and the Wikidata
// sitelinks popularity proxy for songs with little or no play history.
//
// Every weight and threshold here is a named constant carrying an initial heuristic value,
// not an empirically fitted one. No real play data exists to fit them against yet, so they
// are the starting point to be tuned once real rounds accumulate; see docs/DECISIONS.md.
@Component
public class SongDifficultyScorer {

    static final double DIFFICULTY_SCORE_EASIEST = 0.0;
    static final double DIFFICULTY_SCORE_HARDEST = 1.0;

    // The score assigned when no signal is available at all: a song with no play history and
    // no sitelinks count sits exactly in the middle rather than being called easy or hard.
    static final double NEUTRAL_DIFFICULTY_SCORE = 0.5;

    // How many scored rounds a song needs before its play-derived signal is trusted on its
    // own. Below this the play signal is blended with the sitelinks proxy in proportion to
    // how much history exists, so a song with a single lucky or unlucky round is not called
    // trivially easy or brutally hard off one data point.
    static final long PLAY_HISTORY_FULL_TRUST_ROUND_COUNT = 20L;

    // The sitelinks count at or above which a song counts as maximally well-known for scoring,
    // mapping to the easiest end of the popularity proxy. Songs above it are clamped to it.
    static final int SITELINKS_WIDELY_KNOWN_COUNT = 40;

    // Initial value only, to be replaced once catalog data can be checked against it.
    static final int SITELINKS_MINIMUM_COUNT = 0;

    public double score(SongDifficultySignals signals) {
        double sitelinksDifficulty = sitelinksDifficulty(signals);

        if (!signals.hasPlacementHistory()) {
            return sitelinksDifficulty;
        }

        double playDifficulty = playDerivedDifficulty(signals.placementStats());
        double playTrustWeight = playTrustWeight(signals.placementStats().scoredRoundCount());
        double blended = playDifficulty * playTrustWeight + sitelinksDifficulty * (1.0 - playTrustWeight);
        return clampToDifficultyRange(blended);
    }

    // An easier song is placed correctly more often, so difficulty is the complement of the
    // correct-placement fraction: a song placed correctly every time scores easiest, a song
    // placed correctly never scores hardest.
    private double playDerivedDifficulty(SongPlacementStats placementStats) {
        return clampToDifficultyRange(DIFFICULTY_SCORE_HARDEST - placementStats.correctPlacementFraction());
    }

    private double playTrustWeight(long scoredRoundCount) {
        if (scoredRoundCount >= PLAY_HISTORY_FULL_TRUST_ROUND_COUNT) {
            return 1.0;
        }
        return (double) scoredRoundCount / (double) PLAY_HISTORY_FULL_TRUST_ROUND_COUNT;
    }

    // A more widely covered song (more Wikipedia language editions) is more recognizable and
    // easier to place, so a higher sitelinks count maps to a lower difficulty. With no
    // sitelinks count available, the proxy contributes the neutral midpoint rather than
    // guessing a song is easy or hard.
    private double sitelinksDifficulty(SongDifficultySignals signals) {
        if (signals.wikidataSitelinksCount().isEmpty()) {
            return NEUTRAL_DIFFICULTY_SCORE;
        }

        int sitelinks = clampSitelinks(signals.wikidataSitelinksCount().get());
        double popularityFraction = (double) (sitelinks - SITELINKS_MINIMUM_COUNT)
                / (double) (SITELINKS_WIDELY_KNOWN_COUNT - SITELINKS_MINIMUM_COUNT);
        return clampToDifficultyRange(DIFFICULTY_SCORE_HARDEST - popularityFraction);
    }

    private int clampSitelinks(int sitelinks) {
        if (sitelinks < SITELINKS_MINIMUM_COUNT) {
            return SITELINKS_MINIMUM_COUNT;
        }
        if (sitelinks > SITELINKS_WIDELY_KNOWN_COUNT) {
            return SITELINKS_WIDELY_KNOWN_COUNT;
        }
        return sitelinks;
    }

    private double clampToDifficultyRange(double value) {
        if (value < DIFFICULTY_SCORE_EASIEST) {
            return DIFFICULTY_SCORE_EASIEST;
        }
        if (value > DIFFICULTY_SCORE_HARDEST) {
            return DIFFICULTY_SCORE_HARDEST;
        }
        return value;
    }
}
