package org.dariusturcu.backend.history;

import lombok.RequiredArgsConstructor;
import org.dariusturcu.backend.security.util.SecurityUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class GameHistoryController {
    private static final String FIRST_PAGE = "0";
    private static final String DEFAULT_PAGE_SIZE = "20";
    private final GameHistoryService history;

    @GetMapping("/history")
    public GameHistoryPage list(@RequestParam(defaultValue = FIRST_PAGE) int page,
            @RequestParam(defaultValue = DEFAULT_PAGE_SIZE) int pageSize) {
        return history.list(SecurityUtils.getCurrentUserId(), page, pageSize);
    }

    @GetMapping("/history/{summaryId}")
    public GameSummary detail(@PathVariable long summaryId) {
        return history.detail(SecurityUtils.getCurrentUserId(), summaryId);
    }

    @GetMapping("/statistics")
    public PlayerHistoryStatistics statistics() {
        return history.statistics(SecurityUtils.getCurrentUserId());
    }
}
