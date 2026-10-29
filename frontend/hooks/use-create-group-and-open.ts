"use client";

import { useQueryClient } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { toast } from "sonner";

import {
  getGetActiveMembershipQueryKey,
  useCreateGroup,
  useLeaveGroup,
} from "@/hooks/generated/group-management/group-management";

const API_ERROR_FALLBACK_MESSAGE = "Something went wrong. Try again.";

interface ApiErrorBody {
  message?: string;
}

function apiErrorMessage(error: unknown): string {
  const response = (error as { response?: { data?: ApiErrorBody } }).response;
  return response?.data?.message ?? API_ERROR_FALLBACK_MESSAGE;
}

// Creates a group from the same defaults everywhere and opens its lobby, optionally
// preselecting a playlist. Leaving the current group is exposed as one action so the
// caller only has to disable it while that group is in a game.
export function useCreateGroupAndOpen() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const createGroup = useCreateGroup();
  const leaveGroup = useLeaveGroup();

  function openGroup(groupId: number | undefined, playlistId?: number): void {
    if (groupId === undefined) return;
    void queryClient.invalidateQueries({ queryKey: getGetActiveMembershipQueryKey() });
    router.push(playlistId === undefined ? `/groups/${groupId}` : `/groups/${groupId}?playlist=${playlistId}`);
  }

  function createGroupAndOpen(playlistId?: number): void {
    createGroup.mutate(
      { data: {} },
      {
        onSuccess: (group) => openGroup(group.id, playlistId),
        onError: (error) => toast.error(apiErrorMessage(error)),
      },
    );
  }

  function leaveGroupAndCreateGroup(groupId: number, playlistId: number): void {
    leaveGroup.mutate(
      { groupId },
      {
        onSuccess: () => createGroupAndOpen(playlistId),
        onError: (error) => toast.error(apiErrorMessage(error)),
      },
    );
  }

  return {
    openGroup,
    createGroupAndOpen,
    leaveGroupAndCreateGroup,
    isCreatingGroup: createGroup.isPending,
    isLeavingGroup: leaveGroup.isPending,
  };
}
