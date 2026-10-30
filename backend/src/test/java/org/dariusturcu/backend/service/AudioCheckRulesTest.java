package org.dariusturcu.backend.service;

import org.dariusturcu.backend.model.group.AudioCheckDTO;
import org.dariusturcu.backend.model.group.AudioCheckStatus;
import org.dariusturcu.backend.model.group.DjMode;
import org.dariusturcu.backend.model.group.Group;
import org.dariusturcu.backend.model.group.GroupStatus;
import org.dariusturcu.backend.model.group.Member;
import org.dariusturcu.backend.model.user.User;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AudioCheckRulesTest {

    private static final long FIRST_USER_ID = 1L;
    private final Instant firstJoinedAt = Instant.parse("2026-10-30T10:00:00Z");

    private Group group(DjMode djMode) {
        Group group = new Group();
        group.setId(10L);
        group.setStatus(GroupStatus.AUDIO_CHECK);
        group.setDjMode(djMode);
        return group;
    }

    private Member member(Group group, long userId, AudioCheckStatus status) {
        User user = new User();
        user.setId(userId);
        user.setUsername("user-" + userId);
        Member member = new Member();
        member.setId(userId + 100);
        member.setUser(user);
        member.setDisplayName("Player " + userId);
        member.setConnected(true);
        member.setJoinedAt(firstJoinedAt.plusSeconds(userId));
        member.setAudioCheckStatus(status);
        group.addMember(member);
        return member;
    }

    @Test
    void aRotatingGameNeedsEveryConnectedMemberToPass() {
        Group group = group(DjMode.ROTATING);
        Member first = member(group, FIRST_USER_ID, AudioCheckStatus.OK);
        Member second = member(group, 2L, AudioCheckStatus.TESTING);

        assertThat(AudioCheckRules.requiredMembers(group)).containsExactly(first, second);
        assertThat(AudioCheckRules.blockingReason(group)).contains("Player 2");

        second.setAudioCheckStatus(AudioCheckStatus.OK);

        assertThat(AudioCheckRules.blockingReason(group)).isNull();
    }

    @Test
    void aDisconnectedMemberDropsOutOfTheRequirement() {
        Group group = group(DjMode.ROTATING);
        member(group, FIRST_USER_ID, AudioCheckStatus.OK);
        member(group, 2L, AudioCheckStatus.OK);
        Member gone = member(group, 3L, AudioCheckStatus.NOT_SHARED);
        gone.setConnected(false);

        assertThat(AudioCheckRules.requiredMembers(group)).doesNotContain(gone);
        assertThat(AudioCheckRules.blockingReason(group)).isNull();
    }

    @Test
    void aFixedGameNeedsOnlyItsChosenDj() {
        Group group = group(DjMode.FIXED);
        member(group, FIRST_USER_ID, AudioCheckStatus.NOT_SHARED);
        Member chosenDj = member(group, 2L, AudioCheckStatus.OK);
        member(group, 3L, AudioCheckStatus.FAILED).setCanShareAudio(false);
        group.setFixedDjMemberId(chosenDj.getId());

        assertThat(AudioCheckRules.requiredMembers(group)).containsExactly(chosenDj);
        assertThat(AudioCheckRules.blockingReason(group)).isNull();
    }

    @Test
    void aFixedGameWithoutAConnectedChosenDjFallsBackToTheEarliestJoiner() {
        Group group = group(DjMode.FIXED);
        Member earliest = member(group, FIRST_USER_ID, AudioCheckStatus.NOT_SHARED);
        Member chosenDj = member(group, 2L, AudioCheckStatus.OK);
        chosenDj.setConnected(false);
        member(group, 3L, AudioCheckStatus.OK);
        group.setFixedDjMemberId(chosenDj.getId());

        assertThat(AudioCheckRules.requiredMembers(group)).containsExactly(earliest);
        assertThat(AudioCheckRules.blockingReason(group)).contains("Player 1");
    }

    @Test
    void aMemberWhoCannotShareBlocksARotatingStart() {
        Group group = group(DjMode.ROTATING);
        member(group, FIRST_USER_ID, AudioCheckStatus.OK);
        Member firefoxPlayer = member(group, 2L, AudioCheckStatus.OK);
        firefoxPlayer.setCanShareAudio(false);

        assertThat(firefoxPlayer.effectiveAudioCheckStatus()).isEqualTo(AudioCheckStatus.CANNOT_SHARE);
        assertThat(AudioCheckRules.blockingReason(group)).contains("Player 2");
        assertThat(AudioCheckRules.nobodyCanShare(group)).isFalse();
    }

    @Test
    void nobodyAbleToShareBlocksEveryStart() {
        Group group = group(DjMode.FIXED);
        member(group, FIRST_USER_ID, AudioCheckStatus.NOT_SHARED).setCanShareAudio(false);
        member(group, 2L, AudioCheckStatus.NOT_SHARED).setCanShareAudio(false);

        assertThat(AudioCheckRules.nobodyCanShare(group)).isTrue();
        assertThat(AudioCheckRules.blockingReason(group)).contains("Nobody");
    }

    @Test
    void aSinglePlayerCannotStart() {
        Group group = group(DjMode.ROTATING);
        member(group, FIRST_USER_ID, AudioCheckStatus.OK);

        assertThat(AudioCheckRules.blockingReason(group)).contains("Not enough");
    }

    @Test
    void theSummaryCountsReadyRequiredMembers() {
        Group group = group(DjMode.ROTATING);
        member(group, FIRST_USER_ID, AudioCheckStatus.OK);
        member(group, 2L, AudioCheckStatus.FAILED);
        member(group, 3L, AudioCheckStatus.OK);

        AudioCheckDTO summary = AudioCheckRules.toDTO(group);

        assertThat(summary.requiredMemberIds()).containsExactly(101L, 102L, 103L);
        assertThat(summary.readyCount()).isEqualTo(2);
        assertThat(summary.canStart()).isFalse();
        assertThat(summary.nobodyCanShare()).isFalse();
    }
}
