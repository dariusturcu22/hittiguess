package org.dariusturcu.backend.model.group;

import java.util.Set;

// A partial update: any null field is left unchanged. playlistIds, when
// present, replaces the group's whole playlist set rather than merging into it.
public record UpdateGroupSettingsRequest(
        Set<Long> playlistIds,
        DjMode djMode,
        Integer winConditionCardCount) {
}
