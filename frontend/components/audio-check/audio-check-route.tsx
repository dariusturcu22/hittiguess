"use client";

import { useCallback, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";

import { AudioCheckScreen } from "@/components/audio-check/audio-check-screen";
import {
  getGetActiveMembershipQueryKey,
  getGetGroupQueryKey,
  useCancelAudioCheck,
  useGetGroup,
  useReportAudioCheckStatus,
  useStartGameAfterAudioCheck,
  useSwitchToFixedDj,
} from "@/hooks/generated/group-management/group-management";
import { useGetCurrentUser } from "@/hooks/generated/user-management/user-management";
import type { GroupDetailDTO } from "@/hooks/models/groupDetailDTO";
import { useAudioShareTest } from "@/hooks/use-audio-share-test";
import { useGroupRealtime } from "@/hooks/use-group-realtime";
import { CANNOT_SHARE_STATUS, memberAudioStatus, OK_STATUS, type ReportableAudioShareStatus } from "@/lib/audio-check";
import { describeBrowser, stopSongCapture } from "@/lib/song-capture";

const FALLBACK_ERROR_MESSAGE = "Something went wrong. Try again.";

function mutationErrorMessage(error: unknown): string {
  if (typeof error === "object" && error !== null && "response" in error) {
    const response = (error as { response?: { data?: { message?: string } } }).response;
    if (response?.data?.message) return response.data.message;
  }
  return FALLBACK_ERROR_MESSAGE;
}

// The lobby route while its group checks audio. Every member sees it; a member who has to
// share runs the share and the sound test here, and every status change is reported to
// the server, which broadcasts it so everyone's screen updates live.
export function AudioCheckRoute({ groupId }: { groupId: number }) {
  const groupQuery = useGetGroup(groupId, { query: { retry: false } });
  useGroupRealtime(groupId);
  const { data: currentUser } = useGetCurrentUser();
  if (!groupQuery.data || !currentUser) return null;
  return <AudioCheckForMember groupId={groupId} group={groupQuery.data} currentUserId={currentUser.id} />;
}

// Mounted once the group and the account are known, so a share that passed before a
// remount is recognized from the first render instead of being tested again.
function AudioCheckForMember({ groupId, group, currentUserId }: { groupId: number; group: GroupDetailDTO; currentUserId: number | undefined }) {
  const queryClient = useQueryClient();
  const { mutate: reportAudioCheckStatus } = useReportAudioCheckStatus();
  const startGame = useStartGameAfterAudioCheck();
  const switchToFixedDj = useSwitchToFixedDj();
  const cancelAudioCheck = useCancelAudioCheck();
  const [errorMessage, setErrorMessage] = useState("");

  const currentMember = group.members?.find((member) => member.userId === currentUserId);
  const currentStatus = memberAudioStatus(currentMember);
  const isRequired = currentMember?.id !== undefined && (group.audioCheck?.requiredMemberIds ?? []).includes(currentMember.id);

  const applyGroup = useCallback((updatedGroup: GroupDetailDTO) => {
    queryClient.setQueryData(getGetGroupQueryKey(groupId), updatedGroup);
  }, [groupId, queryClient]);

  const reportStatus = useCallback((status: ReportableAudioShareStatus) => {
    reportAudioCheckStatus({ groupId, data: { status } }, { onSuccess: applyGroup });
  }, [applyGroup, groupId, reportAudioCheckStatus]);

  const shareTest = useAudioShareTest({
    isActive: isRequired && currentStatus !== CANNOT_SHARE_STATUS,
    isAlreadyPassed: currentStatus === OK_STATUS,
    onStatus: reportStatus,
  });

  return <AudioCheckScreen
    group={group}
    currentMemberId={currentMember?.id}
    ownBrowserName={typeof navigator === "undefined" ? undefined : describeBrowser(navigator.userAgent)}
    shareTest={shareTest}
    isStarting={startGame.isPending}
    isBusy={switchToFixedDj.isPending || cancelAudioCheck.isPending}
    errorMessage={errorMessage}
    onStart={() => {
      setErrorMessage("");
      startGame.mutate({ groupId }, {
        onSuccess: (startedGroup) => {
          applyGroup(startedGroup);
          void queryClient.invalidateQueries({ queryKey: getGetActiveMembershipQueryKey() });
        },
        onError: (error) => setErrorMessage(mutationErrorMessage(error)),
      });
    }}
    onSwitchToFixedDj={(memberId) => {
      setErrorMessage("");
      switchToFixedDj.mutate({ groupId, data: { memberId } }, {
        onSuccess: applyGroup,
        onError: (error) => setErrorMessage(mutationErrorMessage(error)),
      });
    }}
    onBackToLobby={() => {
      setErrorMessage("");
      cancelAudioCheck.mutate({ groupId }, {
        onSuccess: (lobbyGroup) => {
          stopSongCapture();
          applyGroup(lobbyGroup);
        },
        onError: (error) => setErrorMessage(mutationErrorMessage(error)),
      });
    }}
  />;
}
