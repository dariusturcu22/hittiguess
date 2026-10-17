package org.dariusturcu.backend.model.group;

import jakarta.validation.constraints.NotNull;

// What the member's browser reports about itself: only desktop Chromium browsers can
// capture audio, so a player on any other browser cannot act as DJ.
public record AudioCapabilityRequest(@NotNull Boolean canShareAudio) {
}
