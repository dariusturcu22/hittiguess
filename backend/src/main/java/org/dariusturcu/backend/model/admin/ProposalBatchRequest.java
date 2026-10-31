package org.dariusturcu.backend.model.admin;

import java.util.List;

public record ProposalBatchRequest(List<Long> songIds) {
}
