package org.dariusturcu.backend.model.admin;

import java.util.List;

public record ProposalBatchResultDTO(int affected, List<Long> skippedSongIds) {
}
