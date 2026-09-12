package org.dariusturcu.backend.controller;

import org.dariusturcu.backend.exception.ConflictException;
import org.dariusturcu.backend.exception.GlobalExceptionHandler;
import org.dariusturcu.backend.exception.ResourceNotFoundException;
import org.dariusturcu.backend.model.group.GroupDetailDTO;
import org.dariusturcu.backend.model.group.GroupStatus;
import org.dariusturcu.backend.model.group.DjMode;
import org.dariusturcu.backend.service.GroupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class GroupControllerTest {

    @Mock
    private GroupService groupService;

    private MockMvc mockMvc;

    private static final Long GROUP_ID = 1L;
    private static final Long MEMBER_ID = 2L;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new GroupController(groupService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private GroupDetailDTO sampleGroup() {
        return new GroupDetailDTO(GROUP_ID, "INV1", "JOIN1", GroupStatus.OPEN, DjMode.ROTATING, 10, List.of(), List.of(), null);
    }

    @Test
    void createGroupReturnsTheNewGroup() throws Exception {
        when(groupService.createGroup(any())).thenReturn(sampleGroup());

        mockMvc.perform(post("/api/groups")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    void joinGroupReturnsConflictWhenBothInviteAndJoinCodesAreGiven() throws Exception {
        when(groupService.joinGroup(any())).thenThrow(new ConflictException("Provide either an invite code or a join code, not both"));

        mockMvc.perform(post("/api/groups/join")
                        .contentType("application/json")
                        .content("""
                                {"inviteCode":"INV1","joinCode":"JOIN1"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void getActiveMembershipReturnsNoContentWhenTheUserHasNoActiveGroup() throws Exception {
        when(groupService.getActiveMembership()).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/groups/active"))
                .andExpect(status().isNoContent());
    }

    @Test
    void getActiveMembershipReturnsTheGroupWhenOneExists() throws Exception {
        when(groupService.getActiveMembership()).thenReturn(Optional.of(sampleGroup()));

        mockMvc.perform(get("/api/groups/active"))
                .andExpect(status().isOk());
    }

    @Test
    void getGroupReturnsNotFoundForAMissingGroup() throws Exception {
        when(groupService.getGroup(GROUP_ID)).thenThrow(new ResourceNotFoundException("Group not found"));

        mockMvc.perform(get("/api/groups/{groupId}", GROUP_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    void startGameSessionRejectsANonAdminMember() throws Exception {
        when(groupService.startGameSession(GROUP_ID))
                .thenThrow(new AccessDeniedException("Only the group admin can start a session"));

        mockMvc.perform(post("/api/groups/{groupId}/start-session", GROUP_ID))
                .andExpect(status().isBadRequest());
    }

    @Test
    void promoteMemberDelegatesToTheServiceWithBothIds() throws Exception {
        when(groupService.promoteMember(GROUP_ID, MEMBER_ID)).thenReturn(sampleGroup());

        mockMvc.perform(post("/api/groups/{groupId}/members/{memberId}/promote", GROUP_ID, MEMBER_ID))
                .andExpect(status().isOk());

        verify(groupService).promoteMember(GROUP_ID, MEMBER_ID);
    }

    @Test
    void leaveGroupReturnsNoContent() throws Exception {
        mockMvc.perform(post("/api/groups/{groupId}/leave", GROUP_ID))
                .andExpect(status().isNoContent());

        verify(groupService).leaveGroup(GROUP_ID);
    }
}
