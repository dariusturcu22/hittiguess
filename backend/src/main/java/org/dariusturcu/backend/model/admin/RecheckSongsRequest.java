package org.dariusturcu.backend.model.admin;

import java.util.List;

public record RecheckSongsRequest(List<Long> songIds, Boolean all) {
}
