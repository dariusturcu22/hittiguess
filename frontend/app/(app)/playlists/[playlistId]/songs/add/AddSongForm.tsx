"use client";

import { useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";

import {
  createSong,
  getGetPlaylistQueryKey,
  useCreateSong,
} from "@/hooks/generated/playlist-management/playlist-management";
import { getSongMetadata } from "@/hooks/generated/song-metadata/song-metadata";
import { CreateSongRequest, CreateSongRequestCountry, SongDTO } from "@/hooks/models";
import { SongSearchStep } from "./SongSearchStep";
import { NewSongLinkStep } from "./NewSongLinkStep";
import { NewSongReviewStep, PendingSongDetails } from "./NewSongReviewStep";
import { buildCreateSongRequestFromCatalog } from "./songCatalogRequest";

type Mode = "search" | "new-link" | "new-review";

const DEFAULT_COLOR = "#8B5CF6";
const REVIEW_PREVIEW_DETAILS: PendingSongDetails = {
  title: "Dreams",
  artist: "Fleetwood Mac",
  releaseYear: "1977",
  color: "#89b4fa",
  country: CreateSongRequestCountry.NONE,
  isHighConfidence: false,
};

interface AddSongFormProps {
  playlistId: number;
  backPath: string;
}

export function AddSongForm({ playlistId, backPath }: AddSongFormProps) {
  const router = useRouter();
  const searchParams = useSearchParams();
  const queryClient = useQueryClient();
  const previewState = searchParams.get("preview");

  const [mode, setMode] = useState<Mode>(
    previewState === "link" ? "new-link" : previewState === "review" ? "new-review" : "search",
  );
  const [queue, setQueue] = useState<SongDTO[]>([]);
  const [isSubmittingQueue, setIsSubmittingQueue] = useState(false);

  const [pendingYoutubeId, setPendingYoutubeId] = useState("");
  const [pendingDetails, setPendingDetails] =
    useState<PendingSongDetails | null>(
      previewState === "review" ? REVIEW_PREVIEW_DETAILS : null,
    );
  const [isFetchingMetadata, setIsFetchingMetadata] = useState(false);
  const [metadataFetchError, setMetadataFetchError] = useState("");

  const { mutate: addSong, isPending: isAddingSong } = useCreateSong();
  const [reviewSubmitError, setReviewSubmitError] = useState("");

  const goToPlaylist = () => {
    queryClient.invalidateQueries({
      queryKey: getGetPlaylistQueryKey(playlistId),
    });
    router.push(backPath);
  };

  const handleToggleQueued = (song: SongDTO) => {
    setQueue((prev) =>
      prev.some((queued) => queued.id === song.id)
        ? prev.filter((queued) => queued.id !== song.id)
        : [...prev, song],
    );
  };

  const handleSubmitQueue = async () => {
    setIsSubmittingQueue(true);
    let failureCount = 0;

    for (const song of queue) {
      const request = buildCreateSongRequestFromCatalog(song);
      try {
        await createSong(playlistId, request);
      } catch {
        failureCount += 1;
      }
    }

    setIsSubmittingQueue(false);

    if (failureCount > 0) {
      toast.error(
        `${failureCount} of ${queue.length} songs couldn't be added. Try again for those.`,
      );
    } else {
      toast.success(`Added ${queue.length} songs to the playlist.`);
    }
    goToPlaylist();
  };

  const handleStartNewSong = () => {
    setMode("new-link");
  };

  const handleBackToSearch = () => {
    setMode("search");
    setPendingDetails(null);
    setPendingYoutubeId("");
    setMetadataFetchError("");
  };

  const handleFetchDetails = async (youtubeId: string) => {
    setPendingYoutubeId(youtubeId);
    setIsFetchingMetadata(true);
    setMetadataFetchError("");

    try {
      const response = await getSongMetadata({
        youtubeUrl: `youtube.com/watch?v=${youtubeId}`,
      });

      if (response.status === "REJECTED") {
        setMetadataFetchError(
          response.rejectionDetail ??
            "That video was rejected by content-safety checks.",
        );
        return;
      }

      if (response.status !== "SUCCESS" || !response.content) {
        setMetadataFetchError(
          "Couldn't fetch metadata for that link. Try again.",
        );
        return;
      }

      const metadata = response.content;
      const isHighConfidence =
        !!metadata.title && !!metadata.artist && !!metadata.releaseYear;

      setPendingDetails({
        title: metadata.title ?? "",
        artist: metadata.artist ?? "",
        releaseYear: metadata.releaseYear ?? "",
        color: metadata.color ? `#${metadata.color}` : DEFAULT_COLOR,
        country: CreateSongRequestCountry.NONE,
        isHighConfidence,
      });
      setMode("new-review");
    } catch {
      setMetadataFetchError(
        "Couldn't fetch metadata for that link. Try again.",
      );
    } finally {
      setIsFetchingMetadata(false);
    }
  };

  const handleSubmitNewSong = (request: CreateSongRequest) => {
    setReviewSubmitError("");
    addSong(
      { playlistId, data: request },
      {
        onSuccess: () => {
          toast.success(`Added "${request.title}" to the playlist.`);
          goToPlaylist();
        },
        onError: () => {
          setReviewSubmitError("Couldn't add the song. Try again.");
        },
      },
    );
  };

  if (mode === "new-link") {
    return (
      <div className="flex h-full items-center justify-center p-6">
        <div className="w-full max-w-[560px] rounded-2xl border-[3px] border-border-strong bg-card p-8 shadow-lg sm:p-11">
          <NewSongLinkStep
            onFetch={handleFetchDetails}
            isFetching={isFetchingMetadata}
            onBackToSearch={handleBackToSearch}
            fetchError={metadataFetchError}
          />
        </div>
      </div>
    );
  }

  if (mode === "new-review" && pendingDetails) {
    return (
      <div className="flex h-full items-center justify-center p-6">
        <div className="w-full max-w-[560px] rounded-2xl border-[3px] border-border-strong bg-card p-8 shadow-lg sm:p-11">
          <NewSongReviewStep
            youtubeId={pendingYoutubeId}
            details={pendingDetails}
            onSubmit={handleSubmitNewSong}
            isSubmitting={isAddingSong}
            submitError={reviewSubmitError}
          />
        </div>
      </div>
    );
  }

  return (
    <SongSearchStep
      backPath={backPath}
      queue={queue}
      onToggleQueued={handleToggleQueued}
      onSubmitQueue={handleSubmitQueue}
      isSubmitting={isSubmittingQueue}
      onStartNewSong={handleStartNewSong}
    />
  );
}
