package org.dariusturcu.backend.model.admin;

import jakarta.validation.constraints.Size;
import java.util.List;

public record RecheckSongsRequest(
        @Size(max = 1000, message = "At most 1000 song IDs can be rechecked at once")
        List<Long> songIds,
        Boolean all) {
}
