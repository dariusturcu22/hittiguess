"use client";

import React, { use } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { Crown, Eye, Plus, Trash2, UserX, Ban, Copy, Pencil } from "lucide-react";

import {
  useGetPlaylist,
  useGetMembers,
  useUpdatePlaylist,
  useUpdateMemberGrants,
  useKickMember,
  useBanMember,
  usePublishPlaylist,
  useUnpublishPlaylist,
  getGetMembersQueryKey,
  getGetPlaylistQueryKey,
} from "@/hooks/generated/playlist-management/playlist-management";
import type { PlaylistMemberDTO } from "@/hooks/models/playlistMemberDTO";
import { PlaylistCoverMosaic } from "@/components/playlist-cover-mosaic";
import { DEFAULT_PLAYLIST_COLOR, PLAYLIST_COLOR_PRESETS } from "@/lib/playlist-colors";
import { getGetUserPlaylistsQueryKey } from "@/hooks/generated/user-management/user-management";
import { useQueryClient } from "@tanstack/react-query";
import { AXIOS_INSTANCE } from "@/lib/axios-instance";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from "@/components/shadcn/alert-dialog";

interface PageProps {
  params: Promise<{ playlistId: string }>;
}

type GrantKey = "canRead" | "canWrite" | "canDelete";

// Six preset cover colours from the mockup, stored as the 6-hex form the
// UpdatePlaylistRequest pattern requires (no leading #).

const GRANT_DEFINITIONS: { key: GrantKey; label: string; icon: React.ReactNode }[] =
  [
    { key: "canRead", label: "Read", icon: <Eye className="size-[13px]" /> },
    { key: "canWrite", label: "Add songs", icon: <Plus className="size-[13px]" /> },
    {
      key: "canDelete",
      label: "Remove songs",
      icon: <Trash2 className="size-[13px]" />,
    },
  ];

function memberInitial(member: PlaylistMemberDTO): string {
  const source = member.displayName || member.username || "?";
  return source.charAt(0).toUpperCase();
}

function joinedLabel(joinedAt?: string): string {
  if (!joinedAt) {
    return "Member";
  }
  return `Joined ${new Intl.DateTimeFormat("en", { month: "short", year: "numeric" }).format(new Date(joinedAt))}`;
}

function MemberRow({
  playlistId,
  member,
}: {
  playlistId: number;
  member: PlaylistMemberDTO;
}) {
  const queryClient = useQueryClient();
  const updateGrants = useUpdateMemberGrants();
  const kickMember = useKickMember();
  const banMember = useBanMember();

  const userId = member.userId;

  function invalidate() {
    queryClient.invalidateQueries({
      queryKey: getGetMembersQueryKey(playlistId),
    });
  }

  function toggleGrant(key: GrantKey) {
    if (userId == null) {
      return;
    }
    updateGrants.mutate(
      {
        playlistId,
        userId,
        data: {
          canRead: member.canRead,
          canWrite: member.canWrite,
          canDelete: member.canDelete,
          [key]: !member[key],
        },
      },
      { onSuccess: invalidate },
    );
  }

  if (member.owner) {
    return (
      <div className="flex items-center gap-3 px-5 py-[11px] border-b-2 border-background last:border-b-0">
        <span className="w-9 h-9 rounded-full flex items-center justify-center font-display text-[13px] text-primary-foreground bg-primary shrink-0">
          {memberInitial(member)}
        </span>
        <span className="flex-1 min-w-0">
          <span className="flex items-center gap-1.5 font-semibold text-[13px] text-card-foreground">
            {member.displayName || member.username}
            <Crown className="size-[13px] text-warning" />
          </span>
          <span className="block text-[10.5px] text-muted-foreground mt-0.5">
            Playlist admin
          </span>
        </span>
      </div>
    );
  }

  return (
    <div className="flex items-center gap-3 px-5 py-[11px] border-b-2 border-background last:border-b-0">
      <span className="w-9 h-9 rounded-full flex items-center justify-center font-display text-[13px] text-accent-foreground bg-accent shrink-0">
        {memberInitial(member)}
      </span>
      <span className="flex-1 min-w-0">
        <span className="block font-semibold text-[13px] text-card-foreground truncate">
          {member.displayName || member.username}
        </span>
        <span className="block text-[10.5px] text-muted-foreground mt-0.5">
            {joinedLabel(member.joinedAt)}
        </span>
      </span>

      <span className="flex gap-1.5">
        {GRANT_DEFINITIONS.map((grant) => {
          const on = Boolean(member[grant.key]);
          return (
            <button
              type="button"
              key={grant.key}
              title={grant.label}
              onClick={() => toggleGrant(grant.key)}
              disabled={updateGrants.isPending}
              className={`w-[26px] h-[26px] rounded-lg flex items-center justify-center cursor-pointer border-2 disabled:opacity-60 ${
                on
                  ? "bg-primary/20 text-primary border-primary"
                  : "bg-background text-muted-foreground border-secondary"
              }`}
            >
              {grant.icon}
            </button>
          );
        })}
      </span>

      <span className="flex gap-1.5 ml-1">
        <button
          type="button"
          title="Kick, can rejoin"
          onClick={() =>
            userId != null &&
            kickMember.mutate({ playlistId, userId }, { onSuccess: invalidate })
          }
          disabled={kickMember.isPending}
          className="w-[30px] h-[30px] rounded-lg flex items-center justify-center cursor-pointer bg-background text-muted-foreground border-2 border-secondary disabled:opacity-60"
        >
          <UserX className="size-[14px]" />
        </button>
        <button
          type="button"
          title="Ban, can't rejoin"
          onClick={() =>
            userId != null &&
            banMember.mutate({ playlistId, userId }, { onSuccess: invalidate })
          }
          disabled={banMember.isPending}
          className="w-[30px] h-[30px] rounded-lg flex items-center justify-center cursor-pointer bg-background text-destructive border-2 border-secondary disabled:opacity-60"
        >
          <Ban className="size-[14px]" />
        </button>
      </span>
    </div>
  );
}

export default function EditPlaylistPage({ params }: PageProps) {
  const { playlistId: rawId } = use(params);
  const playlistId = parseInt(rawId);
  const router = useRouter();
  const queryClient = useQueryClient();

  const { data: playlist } = useGetPlaylist(playlistId);
  const { data: members } = useGetMembers(playlistId);
  const updatePlaylist = useUpdatePlaylist();
  const publishMutation = usePublishPlaylist();
  const unpublishMutation = useUnpublishPlaylist();
  const [isDeleting, setIsDeleting] = React.useState(false);
  const [deleteError, setDeleteError] = React.useState("");

  const [nameDraft, setNameDraft] = React.useState("");
  const [descriptionDraft, setDescriptionDraft] = React.useState("");
  React.useEffect(() => {
    if (playlist?.name != null) {
      setNameDraft(playlist.name);
    }
  }, [playlist?.name]);

  const currentColor = playlist?.color ?? DEFAULT_PLAYLIST_COLOR;

  function invalidatePlaylist() {
    queryClient.invalidateQueries({
      queryKey: getGetPlaylistQueryKey(playlistId),
    });
    queryClient.invalidateQueries({
      queryKey: getGetUserPlaylistsQueryKey(),
    });
  }

  function saveName() {
    const trimmed = nameDraft.trim();
    if (trimmed.length === 0 || trimmed === playlist?.name) {
      return;
    }
    updatePlaylist.mutate(
      { playlistId, data: { name: trimmed } },
      {
        onSuccess: () => {
          invalidatePlaylist();
          router.push(`/playlists/${playlistId}`);
        },
      },
    );
  }

  function resetName() {
    setNameDraft(playlist?.name ?? "");
  }

  async function deletePlaylist() {
    setDeleteError("");
    setIsDeleting(true);
    try {
      await AXIOS_INSTANCE.delete(`/api/playlists/${playlistId}`);
      queryClient.invalidateQueries({ queryKey: getGetUserPlaylistsQueryKey() });
      router.push("/playlists");
    } catch {
      setDeleteError("Could not delete this playlist. Check your connection and try again later.");
    } finally {
      setIsDeleting(false);
    }
  }

  function selectColor(color: string) {
    if (color === currentColor) {
      return;
    }
    updatePlaylist.mutate(
      { playlistId, data: { color } },
      { onSuccess: invalidatePlaylist },
    );
  }

  const isPublic = Boolean(playlist?.isPublic);
  const publishing = publishMutation.isPending || unpublishMutation.isPending;

  function togglePublish() {
    const options = { onSuccess: invalidatePlaylist };
    if (isPublic) {
      unpublishMutation.mutate({ playlistId }, options);
    } else {
      publishMutation.mutate({ playlistId }, options);
    }
  }

  const memberList = members ?? [];
  const inviteLink = playlist?.inviteCode
    ? `${typeof window === "undefined" ? "" : window.location.origin}/playlists/join/${playlist.inviteCode}`
    : "";

  return (
    <div className="flex-1 min-w-0 flex flex-col px-12 pt-9 pb-8">
      <h1 className="font-display text-[26px] text-accent mb-1 [text-shadow:3px_3px_0_var(--text-shadow-on-page)]">
        Edit playlist
      </h1>
      <p className="text-[12.5px] text-muted-foreground mb-[22px]">
        Only you, as the playlist&apos;s admin, can see this screen.
      </p>

      <div className="flex gap-6 flex-1 min-h-0">
        <div className="flex-[1.5] min-w-0 bg-card border-[3px] border-border-strong rounded-2xl shadow-lg box-border p-[26px] overflow-y-auto">
          <div className="flex gap-[22px] mb-6">
            <div className="group relative w-[158px] h-[158px] shrink-0">
              <PlaylistCoverMosaic
                previewYoutubeIds={(playlist?.songs ?? []).map((song) => song.youtubeId)}
                className="w-full h-full"
              />
              <button
                type="button"
                className="absolute inset-0 rounded-[17px] bg-background/55 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center text-card-foreground"
                aria-label="Edit playlist cover"
              >
                <Pencil className="size-6" />
              </button>
            </div>
            <div className="flex-1 min-w-0 flex flex-col justify-center gap-4">
              <div>
                <label
                  htmlFor="playlist-name"
                  className="block mb-[7px] text-[11px] font-bold uppercase tracking-[0.5px] text-muted-foreground"
                >
                  Name
                </label>
                <input
                  id="playlist-name"
                  type="text"
                  value={nameDraft}
                  onChange={(event) => setNameDraft(event.target.value)}
                  onKeyDown={(event) => {
                    if (event.key === "Enter") {
                      saveName();
                    }
                  }}
                  className="w-full box-border font-display text-[22px] bg-background border-2 border-accent rounded-md px-4 py-2.5"
                  style={{ color: `#${currentColor}` }}
                />
              </div>
              <div>
                <label className="block mb-[9px] text-[11px] font-bold uppercase tracking-[0.5px] text-muted-foreground">
                  Title color
                </label>
                <div className="flex items-center gap-[11px] ml-1.5">
                  {PLAYLIST_COLOR_PRESETS.map((color) => {
                    const selected = color === currentColor;
                    return (
                      <button
                        type="button"
                        key={color}
                        onClick={() => selectColor(color)}
                        aria-label={`Set colour #${color}`}
                        aria-pressed={selected}
                        className="w-6 h-6 rounded-full cursor-pointer shrink-0"
                        style={{
                          background: `#${color}`,
                          boxShadow: selected
                            ? `0 0 0 3px var(--card), 0 0 0 5px #${color}`
                            : undefined,
                        }}
                      />
                    );
                  })}
                </div>
              </div>
            </div>
          </div>

          <div className="mb-4">
            <label className="block mb-[7px] text-[11px] font-bold uppercase tracking-[0.5px] text-muted-foreground">
              Invite link
            </label>
            <div className="flex items-center gap-2 bg-background border-2 border-secondary rounded-md py-2 pl-4 pr-2">
              <span className="flex-1 min-w-0 text-[12.5px] text-muted-foreground truncate">
                {inviteLink || "..."}
              </span>
              <button
                type="button"
                onClick={() => inviteLink && navigator.clipboard.writeText(inviteLink)}
                disabled={!inviteLink}
                className="size-8 rounded-lg bg-secondary text-card-foreground flex items-center justify-center disabled:opacity-50"
                aria-label="Copy invite link"
              >
                <Copy className="size-3.5" />
              </button>
            </div>
            <p className="text-[11px] text-muted-foreground/70 mt-2">
              Fixed for this playlist&apos;s lifetime, it doesn&apos;t change.
            </p>
          </div>

          <div className="flex items-center justify-between mb-[22px]">
            <div>
              <div className="text-[13px] text-card-foreground font-semibold">
                Public playlist
              </div>
              <div className="text-[11px] text-muted-foreground mt-0.5">
                Anyone can find and save this playlist.
              </div>
            </div>
            <button
              type="button"
              role="switch"
              aria-checked={isPublic}
              onClick={togglePublish}
              disabled={publishing}
              className={`w-10 h-[22px] rounded-full relative shrink-0 cursor-pointer disabled:opacity-60 ${
                isPublic ? "bg-primary" : "bg-secondary"
              }`}
            >
              <span
                className={`w-4 h-4 rounded-full bg-border-strong absolute top-[3px] transition-[left] ${
                  isPublic ? "left-[21px]" : "left-[3px]"
                }`}
              />
            </button>
          </div>

          <div className="mb-4">
            <label htmlFor="playlist-description" className="block mb-[7px] text-[11px] font-bold uppercase tracking-[0.5px] text-muted-foreground">
              Description
            </label>
            <textarea
              id="playlist-description"
              rows={3}
              value={descriptionDraft}
              onChange={(event) => setDescriptionDraft(event.target.value)}
              placeholder="Tell people what this playlist sounds like."
              className="w-full resize-none box-border bg-background border-2 border-secondary rounded-md px-4 py-3 text-[13px] leading-relaxed text-card-foreground"
            />
          </div>

          <div className="grid grid-cols-2 gap-3 mb-5">
            <button
              type="button"
              onClick={resetName}
              className="px-5 py-3 rounded-full border-2 border-secondary text-[12px] font-semibold text-muted-foreground order-2"
            >
              Cancel
            </button>
            <button
              type="button"
              onClick={saveName}
              disabled={updatePlaylist.isPending || nameDraft.trim().length === 0}
              className="px-6 py-3 rounded-full bg-primary text-primary-foreground font-display text-[12px] disabled:opacity-60 order-1"
            >
              {updatePlaylist.isPending ? "Saving..." : "Save changes"}
            </button>
          </div>

          <div className="h-0.5 bg-secondary mb-5" />

          <div className="bg-destructive/[0.08] border-2 border-destructive rounded-2xl box-border px-[18px] py-[15px] flex items-center justify-between gap-2.5">
            <div>
              <div className="text-[13px] text-destructive font-semibold">
                Delete this playlist
              </div>
              <div className="text-[11px] text-muted-foreground mt-[3px]">
                Can&apos;t be undone. All songs and history are lost.
              </div>
            </div>
            <AlertDialog>
              <AlertDialogTrigger asChild>
                <button
                  type="button"
                  className="font-sans font-semibold text-[12px] text-destructive bg-transparent px-4 py-[9px] rounded-full border-2 border-destructive box-border cursor-pointer whitespace-nowrap shrink-0 transition-colors hover:bg-destructive hover:text-destructive-foreground"
                >
                  Delete
                </button>
              </AlertDialogTrigger>
              <AlertDialogContent>
                <AlertDialogHeader>
                  <AlertDialogTitle>Delete this playlist?</AlertDialogTitle>
                  <AlertDialogDescription>
                    This permanently deletes the playlist. This action cannot be undone.
                  </AlertDialogDescription>
                </AlertDialogHeader>
                {deleteError ? <p className="text-sm text-destructive">{deleteError}</p> : null}
                <AlertDialogFooter>
                  <AlertDialogCancel disabled={isDeleting}>Cancel</AlertDialogCancel>
                  <AlertDialogAction
                    variant="destructive"
                    disabled={isDeleting}
                    onClick={(event) => {
                      event.preventDefault();
                      deletePlaylist();
                    }}
                  >
                    {isDeleting ? "Deleting..." : "Delete playlist"}
                  </AlertDialogAction>
                </AlertDialogFooter>
              </AlertDialogContent>
            </AlertDialog>
          </div>
        </div>

        <div className="w-[460px] shrink-0 bg-card border-[3px] border-border-strong rounded-2xl shadow-lg flex flex-col overflow-hidden">
          <div className="px-[22px] pt-[18px] pb-[14px] border-b-2 border-background shrink-0">
            <div className="font-display text-[15px] text-card-foreground">
              Members ({memberList.length})
            </div>
            <div className="text-[11px] text-muted-foreground mt-[5px] leading-[1.5]">
              Read, add songs, and remove songs are granted separately. Kicking
              lets someone rejoin later; banning doesn&apos;t.
            </div>
          </div>
          <div className="flex-1 min-h-0 overflow-y-auto">
            {memberList.length === 0 ? (
              <div className="p-6 text-center text-[13px] text-muted-foreground">
                No members yet.
              </div>
            ) : (
              memberList.map((member) => (
                <MemberRow
                  key={member.userId}
                  playlistId={playlistId}
                  member={member}
                />
              ))
            )}
          </div>
        </div>
      </div>

      <div className="mt-5 shrink-0">
        <Link
          href={`/playlists/${playlistId}`}
          className="text-[12px] text-muted-foreground cursor-pointer hover:text-card-foreground"
        >
          ‹ Back to playlist
        </Link>
      </div>
    </div>
  );
}
