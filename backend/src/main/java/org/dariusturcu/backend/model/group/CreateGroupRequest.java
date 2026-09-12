package org.dariusturcu.backend.model.group;

// displayName and avatarUrl are optional: the creator's per-group identity
// defaults to their account's own values when omitted, same as a joining member.
public record CreateGroupRequest(
        String displayName,
        String avatarUrl) {
}
