"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
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

type Mode = "search" | "new-link" | "new-review";

const DEFAULT_GRADIENT_1 = "#8B5CF6";
const DEFAULT_GRADIENT_2 = "#EC4899";

interface AddSongFormProps {
  playlistId: number;
  backPath: string;
}

export function AddSongForm({ playlistId, backPath }: AddSongFormProps) {
  const router = useRouter();
  const queryClient = useQueryClient();

  const [mode, setMode] = useState<Mode>("search");
  const [queue, setQueue] = useState<SongDTO[]>([]);
  const [isSubmittingQueue, setIsSubmittingQueue] = useState(false);

  const [pendingYoutubeId, setPendingYoutubeId] = useState("");
  const [pendingDetails, setPendingDetails] =
    useState<PendingSongDetails | null>(null);
  const [isFetchingMetadata, setIsFetchingMetadata] = useState(false);

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
      const request: CreateSongRequest = {
        youtubeId: song.youtubeId,
        title: song.title,
        artist: song.artists[0]?.name ?? "Unknown artist",
        releaseYear: song.releaseYear,
        gradientColor1: song.gradientColor1 ?? DEFAULT_GRADIENT_1.replace("#", ""),
        gradientColor2: song.gradientColor2 ?? DEFAULT_GRADIENT_2.replace("#", ""),
        country: song.country,
      };
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
  };

  const handleFetchDetails = async (youtubeId: string) => {
    setPendingYoutubeId(youtubeId);
    setIsFetchingMetadata(true);

    try {
      const response = await getSongMetadata({
        youtubeUrl: `youtube.com/watch?v=${youtubeId}`,
      });

      if (response.status === "ERROR") {
        throw new Error("Metadata pipeline returned an error status");
      }

      const metadata = response.content;
      const isHighConfidence =
        !!metadata?.title && !!metadata?.artist && !!metadata?.releaseYear;

      setPendingDetails({
        title: metadata?.title ?? "",
        artist: metadata?.artist ?? "",
        releaseYear: metadata?.releaseYear ?? "",
        gradientColor1: metadata?.gradientColor1
          ? `#${metadata.gradientColor1}`
          : DEFAULT_GRADIENT_1,
        gradientColor2: metadata?.gradientColor2
          ? `#${metadata.gradientColor2}`
          : DEFAULT_GRADIENT_2,
        country: CreateSongRequestCountry.NONE,
        isHighConfidence,
      });
    } catch {
      setPendingDetails({
        title: "",
        artist: "",
        releaseYear: "",
        gradientColor1: DEFAULT_GRADIENT_1,
        gradientColor2: DEFAULT_GRADIENT_2,
        country: CreateSongRequestCountry.NONE,
        isHighConfidence: false,
      });
    } finally {
      setIsFetchingMetadata(false);
      setMode("new-review");
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
      queue={queue}
      onToggleQueued={handleToggleQueued}
      onSubmitQueue={handleSubmitQueue}
      isSubmitting={isSubmittingQueue}
      onStartNewSong={handleStartNewSong}
    />
  );
}
