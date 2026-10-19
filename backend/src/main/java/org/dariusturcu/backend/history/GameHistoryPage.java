package org.dariusturcu.backend.history;

import java.util.List;

public record GameHistoryPage(List<GameSummary> items, int page, int pageSize, long total) {}
