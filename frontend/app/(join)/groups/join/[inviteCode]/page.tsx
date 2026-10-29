"use client";

import React, { use, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useQueryClient } from "@tanstack/react-query";
import type { AxiosError } from "axios";
import { Pencil, UsersRound } from "lucide-react";

import { Button } from "@/components/shadcn/button";
import { Input } from "@/components/shadcn/input";
import { Label } from "@/components/shadcn/label";
import { LogoIcon } from "@/components/logo";
import { useGetCurrentUser } from "@/hooks/generated/user-management/user-management";
import { useGroupInvitePreview } from "@/hooks/use-group-invite-preview";
import {
  getGetActiveMembershipQueryKey,
  useGetActiveMembership,
  useJoinGroup,
} from "@/hooks/generated/group-management/group-management";

interface PageProps {
  params: Promise<{ inviteCode: string }>;
}

interface JoinFailure {
  status: number;
  message: string;
}

interface JoinErrorBody {
  message?: string;
  displayName?: string;
  avatarUrl?: string;
}

const BAD_REQUEST_STATUS = 400;
const NOT_FOUND_STATUS = 404;
const CONFLICT_STATUS = 409;
const DISPLAY_NAME_MAX_LENGTH = 30;
const INVALID_INVITE_MESSAGE = "That invite link isn't valid.";
const GENERIC_JOIN_ERROR_MESSAGE = "Something went wrong. Try again.";

// A caller who belongs to another group is refused by the cross-group check before
// the removed, started, and full checks, so a conflict only carries a leave
// action when the caller has a membership to leave.
function joinFailureFromError(joinError: unknown): JoinFailure {
  const response = (joinError as AxiosError<JoinErrorBody>).response;
  const status = response?.status;
  if (status === BAD_REQUEST_STATUS) {
    const identityProblem = response?.data?.displayName ?? response?.data?.avatarUrl;
    if (identityProblem) {
      return { status, message: identityProblem };
    }
  }
  if (status === CONFLICT_STATUS) {
    return { status, message: response?.data?.message ?? GENERIC_JOIN_ERROR_MESSAGE };
  }
  return { status: status ?? NOT_FOUND_STATUS, message: INVALID_INVITE_MESSAGE };
}

export default function JoinGroupPage({ params }: PageProps) {
  const { inviteCode } = use(params);
  const router = useRouter();
  const queryClient = useQueryClient();

  const { data: currentUser, isLoading: isCurrentUserLoading, isError: isCurrentUserError } = useGetCurrentUser({
    query: { retry: false },
    request: { skipAuthRedirect: true },
  });
  const { mutate: joinGroup, isPending } = useJoinGroup();
  const [isReplacingGroup, setIsReplacingGroup] = useState(false);
  const isLeavingGroup = isPending && isReplacingGroup;
  const { data: activeMembership } = useGetActiveMembership({
    query: { enabled: Boolean(currentUser), retry: false },
    request: { skipAuthRedirect: true },
  });
  const { data: invitePreview, isError: isInvitePreviewError } = useGroupInvitePreview(inviteCode);

  const [displayName, setDisplayName] = useState("");
  const [avatarUrl, setAvatarUrl] = useState("");
  const [isEditingIdentity, setIsEditingIdentity] = useState(false);
  const [joinFailure, setJoinFailure] = useState<JoinFailure | null>(null);

  React.useEffect(() => {
    if (currentUser?.username && !displayName) {
      setDisplayName(currentUser.username);
    }
    if (currentUser?.imageUrl && !avatarUrl) {
      setAvatarUrl(currentUser.imageUrl);
    }
    // Only seed once the account's own values arrive; further edits are the
    // user's own and shouldn't be overwritten by a refetch.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [currentUser]);

  const submitJoin = (replaceCurrentGroup = false) => {
    const trimmedName = displayName.trim();
    const changedFromAccountName =
      trimmedName && trimmedName !== currentUser?.username;
    const changedFromAccountAvatar =
      avatarUrl.trim() && avatarUrl.trim() !== currentUser?.imageUrl;

    setIsReplacingGroup(replaceCurrentGroup);
    joinGroup(
      {
        data: {
          inviteCode,
          displayName: changedFromAccountName ? trimmedName : undefined,
          avatarUrl: changedFromAccountAvatar ? avatarUrl.trim() : undefined,
          ...(replaceCurrentGroup ? { replaceCurrentGroup: true } : {}),
        },
      },
      {
        onSuccess: (group) => {
          setIsReplacingGroup(false);
          setJoinFailure(null);
          queryClient.invalidateQueries({
            queryKey: getGetActiveMembershipQueryKey(),
          });
          router.push(`/groups/${group.id}`);
        },
        onError: (joinError) => {
          setIsReplacingGroup(false);
          setJoinFailure(joinFailureFromError(joinError));
        },
      },
    );
  };

  const handleJoin = () => {
    if (!inviteCode.trim()) {
      setJoinFailure({ status: NOT_FOUND_STATUS, message: INVALID_INVITE_MESSAGE });
      return;
    }
    setJoinFailure(null);
    submitJoin(false);
  };

  const handleLeaveAndRetry = () => {
    const currentGroupId = activeMembership?.id;
    if (currentGroupId === undefined) {
      return;
    }
    submitJoin(true);
  };

  const avatarInitial =
    displayName.trim().charAt(0).toUpperCase() ||
    currentUser?.username?.trim().charAt(0).toUpperCase() ||
    "?";

  if (isInvitePreviewError) {
    return (
      <div className="flex h-full items-center justify-center p-6">
        <div className="flex w-full max-w-[460px] flex-col items-center rounded-2xl border-[3px] border-border-strong bg-card p-8 shadow-lg sm:p-11">
          <div className="mb-6">
            <LogoIcon />
          </div>

          <h1
            className="mb-5 font-display text-xl text-accent"
            style={{ textShadow: "3px 3px 0 var(--text-shadow-on-card)" }}
          >
            This invite link is no longer valid
          </h1>

          <p className="mb-6 text-center text-sm text-muted-foreground">
            The group may have ended or the link revoked. Ask the host for a fresh one.
          </p>

          <button
            type="button"
            onClick={() => router.push("/")}
            className="text-[13px] text-muted-foreground underline underline-offset-4"
          >
            Back to home
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="flex h-full items-center justify-center p-6">
      <div className="flex w-full max-w-[460px] flex-col items-center rounded-2xl border-[3px] border-border-strong bg-card p-8 shadow-lg sm:p-11">
        <div className="mb-6">
          <LogoIcon />
        </div>

        <h1
          className="mb-5.5 font-display text-xl text-accent"
          style={{ textShadow: "3px 3px 0 var(--text-shadow-on-card)" }}
        >
          You&apos;re invited
        </h1>

        <div className="mb-5.5 flex w-full flex-col items-center gap-3 rounded-2xl border-2 border-border bg-background p-4.5 text-center sm:flex-row sm:gap-4 sm:text-left">
          <div className="flex size-[76px] shrink-0 items-center justify-center rounded-2xl border-[3px] border-border-strong bg-secondary">
            <UsersRound className="size-8 text-accent-foreground/40" />
          </div>
          <div className="min-w-0 flex-1">
            <div className="font-display text-base text-foreground">Group Lobby</div>
            <div className="mt-1 text-xs text-muted-foreground">
              {invitePreview ? `${invitePreview.memberCount ?? 0} members · ` : ""}
              Join this group to play together
            </div>
          </div>
        </div>

        <div className="mb-6 w-full">
          <Label className="mb-1.5 text-[11px] font-bold tracking-wide text-muted-foreground uppercase">
            Join as
          </Label>
          <div className="flex items-center gap-3.5">
            <button
              type="button"
              onClick={() => setIsEditingIdentity((prev) => !prev)}
              title="Customize how you appear in this group"
              className="relative size-16 shrink-0 overflow-hidden rounded-full border-[3px] border-border-strong"
            >
              {avatarUrl ? (
                // eslint-disable-next-line @next/next/no-img-element
                <img
                  src={avatarUrl}
                  alt=""
                  className="size-full object-cover"
                />
              ) : (
                <div className="avatar-initial flex size-full items-center justify-center bg-primary font-display text-xl text-primary-foreground">
                  {avatarInitial}
                </div>
              )}
              <div className="absolute inset-0 flex items-center justify-center bg-black/45 opacity-0 transition-opacity hover:opacity-100">
                <Pencil className="size-4 text-white" />
              </div>
            </button>
            <Input
              value={displayName}
              onChange={(event) => setDisplayName(event.target.value)}
              maxLength={DISPLAY_NAME_MAX_LENGTH}
              className="flex-1"
            />
          </div>
          {isEditingIdentity && (
            <div className="mt-3 grid gap-1.5">
              <Label className="text-[11px] text-muted-foreground">
                Avatar URL (optional)
              </Label>
              <Input
                value={avatarUrl}
                onChange={(event) => setAvatarUrl(event.target.value)}
                placeholder="https://..."
              />
            </div>
          )}
          <p className="mt-2 text-[11px] leading-relaxed text-muted-foreground">
            Defaults to your account name and picture. Change either one to
            join under a different identity just for this group.
          </p>
        </div>

        {joinFailure && (
          <p className="mb-4 text-sm text-destructive">{joinFailure.message}</p>
        )}

        {joinFailure?.status === CONFLICT_STATUS && activeMembership?.id !== undefined && (
          <Button
            variant="secondary"
            className="mb-4 w-full"
            onClick={handleLeaveAndRetry}
            disabled={isLeavingGroup}
          >
            {isLeavingGroup ? "Leaving..." : "Leave your current group and join this one"}
          </Button>
        )}

        {isCurrentUserError && !isCurrentUserLoading ? (
          <Link
            href={`/login?returnTo=${encodeURIComponent(`/groups/join/${inviteCode}`)}`}
            className="mb-4 inline-flex w-full items-center justify-center rounded-full bg-primary px-4 py-3 font-display text-xs text-primary-foreground"
          >
            Log in to join
          </Link>
        ) : (
          <Button
            className="mb-4 w-full"
            onClick={handleJoin}
            disabled={isPending || isCurrentUserLoading || isLeavingGroup}
          >
            {isPending ? "Joining..." : "Join group"}
          </Button>
        )}

        <button
          type="button"
          onClick={() => router.push("/playlists")}
          className="text-[13px] text-muted-foreground underline underline-offset-4"
        >
          Not now
        </button>
      </div>
    </div>
  );
}
