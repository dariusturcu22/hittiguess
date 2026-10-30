package org.dariusturcu.backend.model.group;

import jakarta.validation.constraints.NotNull;

public record SwitchToFixedDjRequest(@NotNull Long memberId) {
}
