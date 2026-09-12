package org.dariusturcu.backend.controller;

import org.dariusturcu.backend.exception.ResourceNotFoundException;
import org.dariusturcu.backend.model.mapper.SessionMapper;
import org.dariusturcu.backend.model.session.GameSession;
import org.dariusturcu.backend.model.session.GameSessionDTO;
import org.dariusturcu.backend.model.session.SessionResultsDTO;
import org.dariusturcu.backend.security.util.SecurityUtils;
import org.dariusturcu.backend.service.GameSessionService;
import org.dariusturcu.backend.service.SessionResultsStore;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
@Tag(name = "Game session", description = "Handles the round-by-round game session for a group")
public class GameSessionController {

    private final GameSessionService gameSessionService;
    private final SessionMapper sessionMapper;
    private final SessionResultsStore resultsStore;

    @Operation(summary = "Get the current state of a game session, must be a player in it")
    @GetMapping("/{sessionId}")
    public ResponseEntity<GameSessionDTO> getSession(@PathVariable Long sessionId) {
        GameSession session = gameSessionService.getSession(sessionId);
        requirePlayerMembership(session);
        return ResponseEntity.ok(sessionMapper.toSessionDTO(session, gameSessionService.getCurrentRoundOrNull(session)));
    }

    @Operation(summary = "Get the most recently completed session's results export for a group")
    @GetMapping("/groups/{groupId}/results")
    public ResponseEntity<SessionResultsDTO> getResults(@PathVariable Long groupId) {
        return resultsStore.get(groupId)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResourceNotFoundException("No completed session results found for group {id=" + groupId + "}"));
    }

    private void requirePlayerMembership(GameSession session) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        boolean isPlayer = session.getPlayers().stream()
                .anyMatch(player -> player.getUser().getId().equals(currentUserId));
        if (!isPlayer) {
            throw new AccessDeniedException("You are not a player in this session");
        }
    }

}
