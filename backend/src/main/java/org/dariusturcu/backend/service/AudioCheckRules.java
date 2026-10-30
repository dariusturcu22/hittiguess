package org.dariusturcu.backend.service;

import org.dariusturcu.backend.model.group.AudioCheckDTO;
import org.dariusturcu.backend.model.group.AudioCheckStatus;
import org.dariusturcu.backend.model.group.DjMode;
import org.dariusturcu.backend.model.group.Group;
import org.dariusturcu.backend.model.group.Member;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

// Who has to share audio before a game can start. Only connected members count, the same
// members the session snapshots as players. A rotating game needs every one of them,
// because everyone DJs equally often; a fixed game needs only its DJ, resolved the way the
// session resolves it: the stored fixed DJ when connected, otherwise the earliest joiner.
public final class AudioCheckRules {

    public static final int MINIMUM_PLAYERS = 2;

    private AudioCheckRules() {
    }

    public static List<Member> candidates(Group group) {
        return group.getMembers().stream()
                .filter(Member::isConnected)
                .sorted(Comparator.comparing(Member::getJoinedAt))
                .toList();
    }

    public static Optional<Member> resolvedFixedDj(Group group) {
        List<Member> candidates = candidates(group);
        Long fixedDjMemberId = group.getFixedDjMemberId();
        return candidates.stream()
                .filter(member -> member.getId().equals(fixedDjMemberId))
                .findFirst()
                .or(() -> candidates.stream().findFirst());
    }

    public static List<Member> requiredMembers(Group group) {
        if (group.getDjMode() == DjMode.FIXED) {
            return resolvedFixedDj(group).map(List::of).orElse(List.of());
        }
        return candidates(group);
    }

    public static boolean nobodyCanShare(Group group) {
        List<Member> candidates = candidates(group);
        return !candidates.isEmpty() && candidates.stream().noneMatch(Member::isCanShareAudio);
    }

    // Null when the game can start, otherwise the reason it cannot, worded for the host.
    public static String blockingReason(Group group) {
        List<Member> candidates = candidates(group);
        if (candidates.size() < MINIMUM_PLAYERS) {
            return "Not enough connected members to start a session";
        }
        if (nobodyCanShare(group)) {
            return "Nobody in this group can share audio, so the game cannot start";
        }
        List<Member> notReady = requiredMembers(group).stream()
                .filter(member -> member.effectiveAudioCheckStatus() != AudioCheckStatus.OK)
                .toList();
        if (notReady.isEmpty()) {
            return null;
        }
        String names = String.join(", ", notReady.stream().map(Member::getDisplayName).toList());
        return "Waiting for a working audio share from " + names;
    }

    public static AudioCheckDTO toDTO(Group group) {
        List<Member> required = requiredMembers(group);
        int readyCount = (int) required.stream()
                .filter(member -> member.effectiveAudioCheckStatus() == AudioCheckStatus.OK)
                .count();
        return new AudioCheckDTO(
                required.stream().map(Member::getId).toList(),
                readyCount,
                blockingReason(group) == null,
                nobodyCanShare(group));
    }
}
