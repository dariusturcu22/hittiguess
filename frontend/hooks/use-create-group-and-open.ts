"use client";

import { useQueryClient } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { toast } from "sonner";

import {
  getGetActiveMembershipQueryKey,
  useCreateGroup,
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
// preselecting a playlist. Leaving the current group and creating a new one happens
// in one server transaction through replaceCurrentGroup.
export function useCreateGroupAndOpen() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const createGroup = useCreateGroup();

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

  function leaveGroupAndCreateGroup(playlistId?: number): void {
    createGroup.mutate(
      { data: { replaceCurrentGroup: true } },
      {
        onSuccess: (group) => openGroup(group.id, playlistId),
        onError: (error) => toast.error(apiErrorMessage(error)),
      },
    );
  }

  return {
    openGroup,
    createGroupAndOpen,
    leaveGroupAndCreateGroup,
    isCreatingGroup: createGroup.isPending,
    isLeavingGroup: createGroup.isPending,
  };
}
