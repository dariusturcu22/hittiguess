"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { AlertTriangle, ExternalLink, Flag, ShieldCheck, Trash2 } from "lucide-react";

import { Button } from "@/components/shadcn/button";
import { Badge } from "@/components/shadcn/badge";
import { Input } from "@/components/shadcn/input";
import { Label } from "@/components/shadcn/label";
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
import { SongDTO } from "@/hooks/models";
import {
  getGetPlaylistQueryKey,
  getGetSongQueryKey,
  useDeleteSong,
} from "@/hooks/generated/playlist-management/playlist-management";
import {
  useSubmitConfirmation,
  useSubmitReport,
} from "@/hooks/generated/community-song-reports/community-song-reports";
import { GameCard } from "@/components/game-card";
import { needsUserAttention } from "@/lib/song-attention";

const REPORT_MESSAGE_MIN_LENGTH = 10;
const REPORT_YEAR_MIN = 1000;

export function validateReportFields(message: string, yearInput: string): string | null {
  if (message.trim().length < REPORT_MESSAGE_MIN_LENGTH) {
    return `Describe what's wrong in a little more detail (at least ${REPORT_MESSAGE_MIN_LENGTH} characters).`;
  }
  if (yearInput) {
    const parsedYear = parseInt(yearInput);
    if (Number.isNaN(parsedYear) || parsedYear < REPORT_YEAR_MIN || parsedYear > new Date().getFullYear()) {
      return "Enter a plausible year for the correction.";
    }
  }
  return null;
}

interface SongReadOnlyViewProps {
  song: SongDTO;
  playlistId: number;
  backPath: string;
}

export function SongReadOnlyView({
  song,
  playlistId,
  backPath,
}: SongReadOnlyViewProps) {
  const router = useRouter();
  const queryClient = useQueryClient();
  const { mutate: removeSong, isPending: isRemoving } = useDeleteSong();
  const { mutate: confirmSong, isPending: isConfirming } =
    useSubmitConfirmation();
  const { mutate: submitReport, isPending: isReporting } = useSubmitReport();

  const [reportOpen, setReportOpen] = useState(false);
  const [reportMessage, setReportMessage] = useState("");
  const [reportYear, setReportYear] = useState("");
  const [reportSources, setReportSources] = useState("");
  const [reportError, setReportError] = useState("");

  const artistNames = song.artists.map((artist) => artist.name).join(", ");
  const isNeedsReview = needsUserAttention(song);

  const invalidateSong = () => {
    queryClient.invalidateQueries({
      queryKey: getGetSongQueryKey(playlistId, song.id),
    });
    queryClient.invalidateQueries({
      queryKey: getGetPlaylistQueryKey(playlistId),
    });
  };

  const handleRemove = () => {
    removeSong(
      { playlistId, songId: song.id },
      {
        onSuccess: () => {
          queryClient.invalidateQueries({
            queryKey: getGetPlaylistQueryKey(playlistId),
          });
          router.push(backPath);
        },
      },
    );
  };

  const handleConfirm = () => {
    confirmSong(
      { songId: song.id },
      {
        onSuccess: () => {
          invalidateSong();
          toast.success("Thanks, this song's details are now confirmed.");
        },
        onError: () => {
          toast.error("Couldn't submit the confirmation. Try again.");
        },
      },
    );
  };

  const handleSubmitReport = () => {
    setReportError("");
    const validationError = validateReportFields(reportMessage, reportYear);
    if (validationError) {
      setReportError(validationError);
      return;
    }
    const parsedYear = reportYear ? parseInt(reportYear) : undefined;

    submitReport(
      {
        songId: song.id,
        data: {
          message: reportMessage.trim(),
          suggestedCorrectYear: parsedYear,
          sources: reportSources.trim() || undefined,
        },
      },
      {
        onSuccess: () => {
          setReportOpen(false);
          setReportMessage("");
          setReportYear("");
          setReportSources("");
          toast.success("Report submitted. Thanks for the flag.");
        },
        onError: () => {
          setReportError("Couldn't submit the report. Try again.");
        },
      },
    );
  };

  const reportDialog = (
    <AlertDialog open={reportOpen} onOpenChange={setReportOpen}>
      <AlertDialogTrigger asChild>
        <Button variant="outline" className="min-w-0 flex-1 gap-2">
          <Flag className="size-3.5" />
          Report
        </Button>
      </AlertDialogTrigger>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>Report this song</AlertDialogTitle>
          <AlertDialogDescription>
            Flag &quot;{song.title}&quot; if its metadata looks wrong. An
            admin reviews every report.
          </AlertDialogDescription>
        </AlertDialogHeader>
        <div className="flex flex-col gap-3">
          <div className="grid gap-1.5">
            <Label htmlFor="reportMessage">What&apos;s wrong</Label>
            <Input
              id="reportMessage"
              value={reportMessage}
              onChange={(event) => setReportMessage(event.target.value)}
              placeholder="The release year looks off"
            />
          </div>
          <div className="grid gap-1.5">
            <Label htmlFor="reportYear">Suggested correct year (optional)</Label>
            <Input
              id="reportYear"
              type="number"
              min={REPORT_YEAR_MIN}
              max={new Date().getFullYear()}
              value={reportYear}
              onChange={(event) => setReportYear(event.target.value)}
            />
          </div>
          <div className="grid gap-1.5">
            <Label htmlFor="reportSources">Sources (optional)</Label>
            <Input
              id="reportSources"
              value={reportSources}
              onChange={(event) => setReportSources(event.target.value)}
              placeholder="A link backing up the correction"
            />
          </div>
          {reportError && (
            <p className="text-sm text-destructive">{reportError}</p>
          )}
        </div>
        <AlertDialogFooter>
          <AlertDialogCancel>Cancel</AlertDialogCancel>
          <AlertDialogAction
            onClick={(event) => {
              event.preventDefault();
              handleSubmitReport();
            }}
            disabled={isReporting}
          >
            {isReporting ? "Submitting..." : "Submit report"}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );

  const removeDialog = (
    <AlertDialog>
      <AlertDialogTrigger asChild>
        <Button
          variant="outline"
          disabled={isRemoving}
          className={`gap-2 text-destructive hover:text-destructive ${
            isNeedsReview ? "w-full" : "min-w-0 flex-1"
          }`}
        >
          <Trash2 className="size-3.5" />
          Remove from playlist
        </Button>
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
            onClick={handleRemove}
          >
            Delete
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );

  return (
    <div className="flex w-full max-w-[640px] flex-col items-center">
      {isNeedsReview && (
        <Badge variant="warning" className="mb-5 gap-1.5 py-2 text-[11px]">
          <AlertTriangle className="size-3" />
          Needs review
        </Badge>
      )}

      <div className="mb-6">
        <GameCard
          artist={artistNames}
          year={song.releaseYear}
          title={song.title}
          color={song.color}
        />
      </div>

      <Button asChild className="mb-4 w-full gap-2">
        <a
          href={`https://www.youtube.com/watch?v=${song.youtubeId}`}
          target="_blank"
          rel="noopener noreferrer"
        >
          <ExternalLink className="size-4" />
          Open on YouTube
        </a>
      </Button>

      {isNeedsReview && (
        <p className="mb-4 max-w-[460px] text-center text-xs leading-relaxed text-muted-foreground">
          The sources on this one didn&apos;t fully agree, so it&apos;s not
          locked yet. If it looks right, confirm it. If something&apos;s
          actually wrong, report it instead of editing it directly.
        </p>
      )}

      {isNeedsReview ? (
        <>
          <div className="mb-3.5 flex w-full flex-col gap-2.5 sm:flex-row sm:gap-3.5">
            <Button
              variant="outline"
              onClick={handleConfirm}
              disabled={isConfirming}
              className="min-w-0 flex-1 gap-2 border-primary text-primary"
            >
              <ShieldCheck className="size-3.5 shrink-0" />
              {isConfirming ? "Confirming..." : "Is this correct?"}
            </Button>
            {reportDialog}
          </div>
          <div className="mb-6 w-full">{removeDialog}</div>
        </>
      ) : (
        <div className="mb-6 flex w-full flex-col gap-2.5 sm:flex-row sm:gap-3.5">
          {reportDialog}
          {removeDialog}
        </div>
      )}

      <div className="mb-4.5 h-0.5 w-full bg-border" />

      <div className="text-xs text-muted-foreground">
        {song.addedBy
          ? `Added by ${song.addedBy.username}`
          : "Added by a deleted account"}
      </div>

    </div>
  );
}
