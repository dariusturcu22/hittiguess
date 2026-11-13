package org.dariusturcu.backend.model.admin;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ProposalBatchRequest(
        @NotEmpty(message = "Song IDs cannot be empty")
        @Size(max = ProposalBatchRequest.MAX_SONG_IDS, message = "At most 500 song IDs can be processed at once")
        List<Long> songIds) {

    public static final int MAX_SONG_IDS = 500;
}
