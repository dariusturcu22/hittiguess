package org.dariusturcu.backend.model.group;

import java.util.List;

// The audio check's requirement as the server judges it, present only while the group is
// in AUDIO_CHECK. readyCount counts required members whose share passed.
public record AudioCheckDTO(
        List<Long> requiredMemberIds,
        int readyCount,
        boolean canStart,
        boolean nobodyCanShare) {
}
