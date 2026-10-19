package org.dariusturcu.backend.difficulty;

import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.List;

// Reduces the per-player predicted difficulty scores for one candidate song into a single
// group-level score, differently per tier. Easy protects the least experienced player by
// taking the lowest individual score rather than an average that a strong player would drag
// down. Hard takes a plain average with no floor to protect. Medium takes the median, a
// middle ground between easy's worst-case protection and hard's average, with no extra
// weighting factor to tune. See docs/GAME_DESIGN.md and docs/DECISIONS.md.
@Component
public class GroupDifficultyStrategy {

    private static final int EVEN_MEDIAN_PAIR_SIZE = 2;
    private static final double EVEN_MEDIAN_DIVISOR = 2.0;

    public double groupScore(DifficultyTier tier, List<Double> perPlayerScores) {
        if (perPlayerScores == null || perPlayerScores.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A group difficulty score needs at least one player's predicted score");
        }

        return switch (tier) {
            case EASY -> lowestScore(perPlayerScores);
            case MEDIUM -> medianScore(perPlayerScores);
            case HARD -> averageScore(perPlayerScores);
        };
    }

    private double lowestScore(List<Double> perPlayerScores) {
        return perPlayerScores.stream().min(Double::compare).orElseThrow();
    }

    private double averageScore(List<Double> perPlayerScores) {
        return perPlayerScores.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
    }

    private double medianScore(List<Double> perPlayerScores) {
        List<Double> sortedScores = perPlayerScores.stream().sorted().toList();
        int scoreCount = sortedScores.size();
        int upperMiddleIndex = scoreCount / EVEN_MEDIAN_PAIR_SIZE;

        boolean hasSingleMiddleElement = scoreCount % EVEN_MEDIAN_PAIR_SIZE == 1;
        if (hasSingleMiddleElement) {
            return sortedScores.get(upperMiddleIndex);
        }

        int lowerMiddleIndex = upperMiddleIndex - 1;
        return (sortedScores.get(lowerMiddleIndex) + sortedScores.get(upperMiddleIndex)) / EVEN_MEDIAN_DIVISOR;
    }
}
