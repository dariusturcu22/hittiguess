package org.dariusturcu.backend.difficulty;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class GroupDifficultyStrategyTest {

    private final GroupDifficultyStrategy groupDifficultyStrategy = new GroupDifficultyStrategy();

    private static final double SCORE_TOLERANCE = 1.0e-9;

    @Test
    void easyTakesTheLowestScoreToProtectTheLeastExperiencedPlayer() {
        List<Double> mixedGroup = List.of(0.1, 0.5, 0.9);

        double score = groupDifficultyStrategy.groupScore(DifficultyTier.EASY, mixedGroup);

        assertThat(score).isCloseTo(0.1, within(SCORE_TOLERANCE));
    }

    @Test
    void hardTakesThePlainAverageWithNoFloorToProtect() {
        List<Double> mixedGroup = List.of(0.1, 0.5, 0.9);

        double score = groupDifficultyStrategy.groupScore(DifficultyTier.HARD, mixedGroup);

        assertThat(score).isCloseTo(0.5, within(SCORE_TOLERANCE));
    }

    @Test
    void mediumTakesTheMedianOfAnOddSizedGroup() {
        List<Double> threePlayers = List.of(0.2, 0.8, 0.5);

        double score = groupDifficultyStrategy.groupScore(DifficultyTier.MEDIUM, threePlayers);

        assertThat(score).isCloseTo(0.5, within(SCORE_TOLERANCE));
    }

    @Test
    void mediumAveragesTheTwoMiddleValuesOfAnEvenSizedGroup() {
        List<Double> fourPlayers = List.of(0.1, 0.4, 0.6, 0.9);

        double score = groupDifficultyStrategy.groupScore(DifficultyTier.MEDIUM, fourPlayers);

        assertThat(score).isCloseTo(0.5, within(SCORE_TOLERANCE));
    }

    @Test
    void easyProtectsAFirstTimePlayerAmongExperiencedOnes() {
        double experiencedPlayerFindsItEasy = 0.15;
        double firstTimePlayerFindsItHard = 0.85;
        List<Double> mixedExperience = List.of(experiencedPlayerFindsItEasy, firstTimePlayerFindsItHard);

        double easyScore = groupDifficultyStrategy.groupScore(DifficultyTier.EASY, mixedExperience);
        double hardScore = groupDifficultyStrategy.groupScore(DifficultyTier.HARD, mixedExperience);

        assertThat(easyScore).isLessThan(hardScore);
        assertThat(easyScore).isCloseTo(experiencedPlayerFindsItEasy, within(SCORE_TOLERANCE));
    }

    @Test
    void aSinglePlayerGroupReturnsThatPlayersScoreForEveryTier() {
        List<Double> soloPlayer = List.of(0.42);

        assertThat(groupDifficultyStrategy.groupScore(DifficultyTier.EASY, soloPlayer)).isCloseTo(0.42, within(SCORE_TOLERANCE));
        assertThat(groupDifficultyStrategy.groupScore(DifficultyTier.MEDIUM, soloPlayer)).isCloseTo(0.42, within(SCORE_TOLERANCE));
        assertThat(groupDifficultyStrategy.groupScore(DifficultyTier.HARD, soloPlayer)).isCloseTo(0.42, within(SCORE_TOLERANCE));
    }

    @Test
    void anEmptyGroupIsRejected() {
        assertThatThrownBy(() -> groupDifficultyStrategy.groupScore(DifficultyTier.MEDIUM, List.of()))
                .isInstanceOf(ResponseStatusException.class);
    }
}
