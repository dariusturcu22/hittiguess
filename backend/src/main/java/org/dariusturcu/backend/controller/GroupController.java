package org.dariusturcu.backend.controller;

import jakarta.validation.Valid;
import org.dariusturcu.backend.model.group.CreateGroupRequest;
import org.dariusturcu.backend.model.group.GroupDetailDTO;
import org.dariusturcu.backend.model.group.JoinGroupRequest;
import org.dariusturcu.backend.model.group.UpdateGroupSettingsRequest;
import org.dariusturcu.backend.service.GroupService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
@Tag(name = "Group management", description = "Handles operations regarding group lobbies")
public class GroupController {
    private final GroupService groupService;

    @Operation(summary = "Create a group, the creator becomes its admin")
    @PostMapping
    public ResponseEntity<GroupDetailDTO> createGroup(
            @Valid @RequestBody CreateGroupRequest request) {
        GroupDetailDTO group = groupService.createGroup(request);
        return ResponseEntity.ok(group);
    }

    @Operation(summary = "Join a group via invite link or join code")
    @PostMapping("/join")
    public ResponseEntity<GroupDetailDTO> joinGroup(
            @Valid @RequestBody JoinGroupRequest request) {
        GroupDetailDTO group = groupService.joinGroup(request);
        return ResponseEntity.ok(group);
    }

    @Operation(summary = "Check the logged-in user's active group membership, if any")
    @GetMapping("/active")
    public ResponseEntity<GroupDetailDTO> getActiveMembership() {
        return groupService.getActiveMembership()
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @Operation(summary = "Get group information")
    @GetMapping("/{groupId}")
    public ResponseEntity<GroupDetailDTO> getGroup(
            @PathVariable Long groupId) {
        GroupDetailDTO group = groupService.getGroup(groupId);
        return ResponseEntity.ok(group);
    }

    @Operation(summary = "Update group settings, admin only")
    @PatchMapping("/{groupId}/settings")
    public ResponseEntity<GroupDetailDTO> updateGroupSettings(
            @PathVariable Long groupId,
            @Valid @RequestBody UpdateGroupSettingsRequest request) {
        GroupDetailDTO group = groupService.updateGroupSettings(groupId, request);
        return ResponseEntity.ok(group);
    }

    @Operation(summary = "Start a game session, admin only, locks the group to new members")
    @PostMapping("/{groupId}/start-session")
    public ResponseEntity<GroupDetailDTO> startGameSession(
            @PathVariable Long groupId) {
        GroupDetailDTO group = groupService.startGameSession(groupId);
        return ResponseEntity.ok(group);
    }

    @Operation(summary = "Leave a group, removing the membership entirely")
    @PostMapping("/{groupId}/leave")
    public ResponseEntity<Void> leaveGroup(
            @PathVariable Long groupId) {
        groupService.leaveGroup(groupId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Mark the current member disconnected without ending their membership")
    @PostMapping("/{groupId}/disconnect")
    public ResponseEntity<Void> disconnect(
            @PathVariable Long groupId) {
        groupService.disconnect(groupId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Mark the current member reconnected")
    @PostMapping("/{groupId}/reconnect")
    public ResponseEntity<Void> reconnect(
            @PathVariable Long groupId) {
        groupService.reconnect(groupId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Promote another member to admin, admin only")
    @PostMapping("/{groupId}/members/{memberId}/promote")
    public ResponseEntity<GroupDetailDTO> promoteMember(
            @PathVariable Long groupId,
            @PathVariable Long memberId) {
        GroupDetailDTO group = groupService.promoteMember(groupId, memberId);
        return ResponseEntity.ok(group);
    }

    @Operation(summary = "Join the group's voice room")
    @PostMapping("/{groupId}/voice/join")
    public ResponseEntity<Void> joinVoice(
            @PathVariable Long groupId) {
        groupService.joinVoice(groupId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Leave the group's voice room")
    @PostMapping("/{groupId}/voice/leave")
    public ResponseEntity<Void> leaveVoice(
            @PathVariable Long groupId) {
        groupService.leaveVoice(groupId);
        return ResponseEntity.noContent().build();
    }
}
