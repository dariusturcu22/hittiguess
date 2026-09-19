"use client";

import * as React from "react";
import Link from "next/link";
import {
  Play,
  Pencil,
  UserPlus,
  FileDown,
  LogOut,
  ListMusic,
  Plus,
  Search,
  Eye,
  Trash2,
  AlertTriangle,
  HelpCircle,
  Check,
  Link2,
  Hash,
  Crown,
  ChevronDown,
} from "lucide-react";
import { toast } from "sonner";

import { Button } from "@/components/shadcn/button";
import { Input } from "@/components/shadcn/input";
import { Badge } from "@/components/shadcn/badge";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
  DropdownMenuLabel,
  DropdownMenuSeparator,
} from "@/components/shadcn/dropdown-menu";
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
import { SongDTO, SongDTOVerificationStatus } from "@/hooks/models";
import { needsUserAttention } from "@/lib/song-attention";
import {
  getGetPlaylistQueryKey,
  useDeleteSong,
  useGetPlaylist,
} from "@/hooks/generated/playlist-management/playlist-management";
import { useLeavePlaylist } from "@/hooks/generated/user-management/user-management";
import { getGetUserPlaylistsQueryKey } from "@/hooks/generated/user-management/user-management";
import { useQueryClient } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { PlaylistCoverMosaic } from "@/components/playlist-cover-mosaic";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/shadcn/popover";
import { playlistTitleColor } from "@/lib/playlist-colors";
import { SongCatalogQuickAdd } from "./SongCatalogQuickAdd";

const MEMBER_AVATAR_COLORS = [
  "var(--primary)",
  "#89b4fa",
  "#f9e2af",
  "#f5c2e7",
  "#a6e3a1",
  "#cba6f7",
];

interface PlaylistContentProps {
  playlistId: number;
}

function MemberAvatar({
  initial,
  color,
  size = 34,
  isOwner = false,
}: {
  initial: string;
  color: string;
  size?: number;
  isOwner?: boolean;
}) {
  return (
    <div
      className="flex shrink-0 items-center justify-center rounded-full border-[3px] border-card font-display text-accent-foreground"
      style={{
        width: size,
        height: size,
        backgroundColor: color,
        fontSize: size * 0.35,
      }}
    >
      {isOwner ? <Crown className="size-3.5" /> : initial}
    </div>
  );
}

export default function PlaylistContent({
  playlistId,
}: PlaylistContentProps) {
  const queryClient = useQueryClient();
  const router = useRouter();

  const { data: playlist, isLoading } = useGetPlaylist(playlistId);
  const songs = React.useMemo(() => playlist?.songs ?? [], [playlist?.songs]);

  const { mutate: removeSong } = useDeleteSong();
  const { mutate: leavePlaylist } = useLeavePlaylist();

  const [searchQuery, setSearchQuery] = React.useState("");
  const [linkCopied, setLinkCopied] = React.useState(false);
  const [codeCopied, setCodeCopied] = React.useState(false);

  const handleDeleteSong = (songId: number) => {
    removeSong(
      { playlistId, songId },
      {
        onSuccess: () => {
          queryClient.invalidateQueries({
            queryKey: getGetPlaylistQueryKey(playlistId),
          });
        },
      },
    );
  };

  const handleLeavePlaylist = () => {
    leavePlaylist(
      { playlistId },
      {
        onSuccess: () => {
          queryClient.invalidateQueries({
            queryKey: getGetUserPlaylistsQueryKey(),
          });
          router.push("/playlists");
        },
      },
    );
  };

  const handleExport = async () => {
    for (const type of ["info", "qr"] as const) {
      const response = await fetch(
        `${process.env.NEXT_PUBLIC_API_URL}/api/playlists/${playlistId}/export/${type}`,
        { credentials: "include" },
      );
      if (!response.ok) {
        toast.error(`Couldn't export the ${type} cards. Try again.`);
        continue;
      }
      const blob = await response.blob();
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement("a");
      anchor.href = url;
      anchor.download = `playlist-${playlistId}-${type}.pdf`;
      anchor.click();
      URL.revokeObjectURL(url);
    }
  };

  const handleCopyLink = async () => {
    if (!playlist) return;
    const inviteLink = `${window.location.origin}/playlists/join/${playlist.inviteCode}`;
    await navigator.clipboard.writeText(inviteLink);
    setLinkCopied(true);
    setTimeout(() => setLinkCopied(false), 2000);
  };

  const handleCopyCode = async () => {
    if (!playlist) return;
    await navigator.clipboard.writeText(playlist.inviteCode);
    setCodeCopied(true);
    setTimeout(() => setCodeCopied(false), 2000);
  };

  if (isLoading || !playlist) {
    return (
      <div className="flex h-full flex-col p-6 md:p-11">
        <p className="text-sm text-muted-foreground">Loading playlist...</p>
      </div>
    );
  }

  const needsReviewCount = songs.filter(needsUserAttention).length;

  const visibleSongs = songs.filter((song) => {
    const query = searchQuery.trim().toLowerCase();
    if (!query) return true;
    return (
      song.title.toLowerCase().includes(query) ||
      song.artists.some((artist) =>
        artist.name?.toLowerCase().includes(query),
      )
    );
  });

  const owner = playlist.members.find((member) => member.owner);

  return (
    <div className="flex h-full flex-col p-6 md:p-11">
      <div className="mb-9 flex flex-col gap-7 sm:flex-row sm:items-start sm:justify-between">
        <div className="flex flex-col gap-7 sm:flex-row">
          <PlaylistCoverMosaic
            previewYoutubeIds={(playlist.songs ?? []).map((song) => song.youtubeId)}
            className="w-[180px] shrink-0"
          />

          <div className="flex flex-col justify-center gap-2.5">
            <h1
              className="font-display text-[26px] sm:text-[32px]"
              style={{
                color: playlistTitleColor(playlist.color),
                textShadow: "3px 3px 0 var(--text-shadow-on-page)",
              }}
            >
              {playlist.name}
            </h1>

            <div className="text-[13px] text-muted-foreground">
              {playlist.songCount} songs
              {owner && (
                <>
                  {" "}
                  &nbsp;•&nbsp; owned by{" "}
                  {owner.displayName ?? owner.username}
                </>
              )}
            </div>

            <div className="flex flex-wrap items-center gap-3">
              <Button
                title="Live sessions haven't shipped yet"
                className="gap-2"
              >
                <Play className="size-3.5 fill-current" />
                Start session
              </Button>

              <Button
                variant="outline"
                size="icon"
                className="size-[46px] rounded-[13px]"
                title="Edit playlist"
                asChild
              >
                <Link href={`/playlists/${playlistId}/edit`}>
                  <Pencil className="size-4" />
                </Link>
              </Button>

              <DropdownMenu>
                <DropdownMenuTrigger asChild>
                  <Button
                    variant="outline"
                    size="icon"
                    className="size-[46px] rounded-[13px]"
                    title="Invite"
                  >
                    <UserPlus className="size-4" />
                  </Button>
                </DropdownMenuTrigger>
                <DropdownMenuContent align="start">
                  <DropdownMenuLabel>Invite people</DropdownMenuLabel>
                  <DropdownMenuSeparator />
                  <DropdownMenuItem onSelect={handleCopyLink}>
                    {linkCopied ? (
                      <Check className="size-4 text-primary" />
                    ) : (
                      <Link2 className="size-4" />
                    )}
                    {linkCopied ? "Link copied" : "Copy invite link"}
                  </DropdownMenuItem>
                  <DropdownMenuItem onSelect={handleCopyCode}>
                    {codeCopied ? (
                      <Check className="size-4 text-primary" />
                    ) : (
                      <Hash className="size-4" />
                    )}
                    {codeCopied ? "Code copied" : "Copy invite code"}
                  </DropdownMenuItem>
                </DropdownMenuContent>
              </DropdownMenu>

              <Button
                variant="outline"
                size="icon"
                className="size-[46px] rounded-[13px]"
                title="Export cards"
                onClick={handleExport}
              >
                <FileDown className="size-4" />
              </Button>

              <AlertDialog>
                <AlertDialogTrigger asChild>
                  <Button
                    variant="outline"
                    size="icon"
                    className="size-[46px] rounded-[13px] text-destructive hover:text-destructive"
                    title="Leave playlist"
                  >
                    <LogOut className="size-4" />
                  </Button>
                </AlertDialogTrigger>
                <AlertDialogContent>
                  <AlertDialogHeader>
                    <AlertDialogTitle>Leave playlist?</AlertDialogTitle>
                    <AlertDialogDescription>
                      You&apos;ll lose access to this playlist. You can rejoin
                      later with an invite link.
                    </AlertDialogDescription>
                  </AlertDialogHeader>
                  <AlertDialogFooter>
                    <AlertDialogCancel>Cancel</AlertDialogCancel>
                    <AlertDialogAction
                      className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
                      onClick={handleLeavePlaylist}
                    >
                      Leave
                    </AlertDialogAction>
                  </AlertDialogFooter>
                </AlertDialogContent>
              </AlertDialog>
            </div>
          </div>
        </div>

        <aside className="w-[260px] shrink-0 rounded-2xl border-[3px] border-border-strong bg-card p-4 shadow-lg">
          <Popover>
            <PopoverTrigger className="flex w-full items-center justify-between gap-3 rounded-lg text-left outline-none transition-colors hover:bg-secondary/50 focus-visible:ring-2 focus-visible:ring-ring">
              <span className="font-display text-[11px] text-muted-foreground">Members ({playlist.members.length})</span>
              <span className="flex -space-x-2">
                {playlist.members.slice(0, 4).map((member, index) => (
                  <MemberAvatar
                    key={member.userId ?? index}
                    initial={(member.displayName ?? member.username ?? "?").charAt(0).toUpperCase()}
                    color={MEMBER_AVATAR_COLORS[index % MEMBER_AVATAR_COLORS.length]}
                    size={26}
                  />
                ))}
              </span>
            </PopoverTrigger>
            <PopoverContent align="end" className="max-h-[360px] overflow-y-auto p-0">
              <div className="border-b-2 border-background px-4 py-3 font-display text-[11px] text-muted-foreground">Members ({playlist.members.length})</div>
              <div>
            {playlist.members.map((member, index) => (
              <div
                key={member.userId ?? index}
                className="flex items-center gap-2.5 border-b border-background px-4 py-2.5 last:border-b-0"
              >
                <MemberAvatar
                  initial={(member.displayName ?? member.username ?? "?")
                    .charAt(0)
                    .toUpperCase()}
                  color={MEMBER_AVATAR_COLORS[index % MEMBER_AVATAR_COLORS.length]}
                  size={26}
                />
                <span className="flex-1 truncate text-sm">
                  {member.displayName ?? member.username}
                </span>
                {member.owner && (
                  <Crown className="size-3.5 shrink-0 text-primary" />
                )}
              </div>
            ))}
              </div>
            </PopoverContent>
          </Popover>
        </aside>
      </div>

      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <div className="font-display text-base">Songs</div>
        <div className="flex flex-wrap items-center gap-2.5">
          <div className="relative">
            <Search className="pointer-events-none absolute top-1/2 left-3.5 size-3.5 -translate-y-1/2 text-muted-foreground" />
            <Input
              placeholder="Search songs..."
              value={searchQuery}
              onChange={(event) => setSearchQuery(event.target.value)}
              className="h-9 w-[180px] rounded-full pl-9 sm:w-[220px]"
            />
          </div>
          <Button
            variant="outline"
            size="sm"
            title="Import playlist"
            className="gap-1.5"
            asChild
          >
            <Link href={`/playlists/${playlistId}/import`}>
              <ListMusic className="size-3.5" />
              Import playlist
            </Link>
          </Button>
          <Button size="sm" className="gap-1.5" asChild>
            <Link href={`/playlists/${playlistId}/songs/add`}>
              <Plus className="size-3" />
              Add song
            </Link>
          </Button>
        </div>
      </div>

      {needsReviewCount > 0 && (
        <div className="mb-3.5 flex items-center justify-between gap-3 rounded-[13px] border-2 border-warning bg-warning/8 px-4.5 py-3">
          <div className="flex items-center gap-2.5">
            <AlertTriangle className="size-4 shrink-0 text-warning" />
            <div className="text-[13px]">
              <strong>
                {needsReviewCount} song{needsReviewCount === 1 ? "" : "s"} need
                {needsReviewCount === 1 ? "s" : ""} review.
              </strong>{" "}
              Their details weren&apos;t fully confirmed when they were added.
            </div>
          </div>
          <Link
            href={`/playlists/${playlistId}/songs/${
              songs.find(needsUserAttention)?.id ?? ""
            }`}
            className="flex shrink-0 items-center gap-1 font-semibold text-[12px] text-warning whitespace-nowrap"
          >
            Review now
            <ChevronDown className="size-3.5 -rotate-90" />
          </Link>
        </div>
      )}

      <div className="flex min-h-0 w-full min-w-0 flex-1 flex-col overflow-hidden rounded-2xl border-[3px] border-border-strong bg-card shadow-lg">
        <div className="min-h-0 min-w-0 flex-1 overflow-y-auto">
          {visibleSongs.length === 0 && songs.length === 0 && (
            <SongCatalogQuickAdd playlistId={playlistId} />
          )}
          {visibleSongs.length === 0 && songs.length > 0 && (
            <p className="p-8 text-center text-sm text-muted-foreground">
              No songs match your search.
            </p>
          )}
          {visibleSongs.map((song, index) => (
            <SongRow
              key={song.id}
              song={song}
              index={index + 1}
              playlistId={playlistId}
              onDelete={handleDeleteSong}
            />
          ))}
        </div>
      </div>
    </div>
  );
}

function SongRow({
  song,
  index,
  playlistId,
  onDelete,
}: {
  song: SongDTO;
  index: number;
  playlistId: number;
  onDelete: (songId: number) => void;
}) {
  const isManualEntry =
    song.verificationStatus === SongDTOVerificationStatus.MANUAL_ENTRY;
  const isNeedsReview = needsUserAttention(song);

  return (
    <div
      className={`flex w-full min-w-0 items-center gap-2 border-b-2 border-background px-3 py-3.5 last:border-b-0 sm:gap-4.5 sm:px-5.5 ${
        isNeedsReview
          ? "bg-warning/6"
          : isManualEntry
            ? "bg-destructive/6"
            : ""
      }`}
    >
      <div className="hidden w-5.5 shrink-0 text-center text-[13px] text-muted-foreground sm:block">
        {index}
      </div>
      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-2">
          <div className="truncate text-sm">{song.title}</div>
          {isNeedsReview && (
            <Badge variant="warning" className="hidden gap-1 sm:inline-flex">
              <AlertTriangle className="size-2.5" />
              Needs review
            </Badge>
          )}
          {isManualEntry && (
            <Badge
              variant="destructive"
              className="hidden gap-1 sm:inline-flex"
            >
              <HelpCircle className="size-2.5" />
              Manual entry
            </Badge>
          )}
        </div>
        <div className="truncate text-xs text-muted-foreground">
          {song.artists.map((artist) => artist.name).join(", ")}
        </div>
      </div>
      <div className="w-9 shrink-0 text-right text-[13px] text-muted-foreground sm:w-[50px]">
        {song.releaseYear}
      </div>
      <Link
        href={`/playlists/${playlistId}/songs/${song.id}`}
        title={isManualEntry ? "Edit song details" : "View song details"}
        className={`flex size-8 shrink-0 items-center justify-center rounded-md ${
          isManualEntry ? "text-destructive" : "text-muted-foreground"
        } hover:bg-secondary`}
      >
        {isManualEntry ? (
          <Pencil className="size-4" />
        ) : (
          <Eye className="size-4" />
        )}
      </Link>
      <AlertDialog>
        <AlertDialogTrigger asChild>
          <button
            type="button"
            title="Remove from playlist"
            className="flex size-8 shrink-0 items-center justify-center rounded-md text-destructive hover:bg-destructive/10"
          >
            <Trash2 className="size-4" />
          </button>
        </AlertDialogTrigger>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Delete song?</AlertDialogTitle>
            <AlertDialogDescription>
              This will permanently remove &quot;{song.title}&quot; from the
              playlist.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction
              className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
              onClick={() => onDelete(song.id)}
            >
              Delete
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}
