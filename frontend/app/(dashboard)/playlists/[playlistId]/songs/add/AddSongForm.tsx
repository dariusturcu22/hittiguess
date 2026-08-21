"use client";

import React, { useState } from "react";
import { Input } from "@/components/shadcn/input";
import { Label } from "@/components/shadcn/label";
import { Button } from "@/components/shadcn/button";
import {
  IconExternalLink,
  IconLoader2,
  IconSparkles,
  IconCheck,
} from "@tabler/icons-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useQueryClient } from "@tanstack/react-query";
import {
  getGetPlaylistQueryKey,
  useCreateSong,
} from "@/hooks/generated/playlist-management/playlist-management";
import { getSongMetadata } from "@/hooks/generated/song-metadata/song-metadata";
import {
  CreateSongRequestCountry,
  CreateSongRequestSongTag,
} from "@/hooks/models";

type Step = "youtube" | "preview" | "details";

interface SongDetails {
  title: string;
  artist: string;
  releaseYear: string | number;
  gradientColor1: string;
  gradientColor2: string;
  songTag: CreateSongRequestSongTag;
  country: CreateSongRequestCountry;
}

const YOUTUBE_ID_PATTERN = /^[a-zA-Z0-9_-]{11}$/;
const HEX_COLOR_PATTERN = /^#[0-9a-fA-F]{6}$/;
const MIN_RELEASE_YEAR = 1000;

function extractYoutubeId(input: string): string | null {
  const trimmed = input.trim();
  let candidate: string | null = null;

  if (YOUTUBE_ID_PATTERN.test(trimmed)) {
    candidate = trimmed;
  } else {
    try {
      const url = new URL(trimmed);
      if (url.searchParams.get("v")) candidate = url.searchParams.get("v");
      else if (url.hostname === "youtu.be")
        candidate = url.pathname.slice(1).split("?")[0];
      else if (url.pathname.startsWith("/embed/"))
        candidate = url.pathname.split("/embed/")[1].split("?")[0];
    } catch {}
  }

  return candidate && YOUTUBE_ID_PATTERN.test(candidate) ? candidate : null;
}

export function AddSongForm({
  backPath,
  playlistId,
}: {
  backPath: string;
  playlistId: number;
}) {
  const router = useRouter();
  const queryClient = useQueryClient();
  const { mutate: addSong, isPending: isAdding } = useCreateSong();

  const [step, setStep] = useState<Step>("youtube");
  const [youtubeInput, setYoutubeInput] = useState("");
  const [youtubeId, setYoutubeId] = useState<string | null>(null);
  const [youtubeError, setYoutubeError] = useState("");
  const [isLoadingAI, setIsLoadingAI] = useState(false);
  const [aiError, setAiError] = useState("");
  const [submitError, setSubmitError] = useState("");
  const [formData, setFormData] = useState<SongDetails>({
    title: "",
    artist: "",
    releaseYear: "",
    gradientColor1: "#8B5CF6",
    gradientColor2: "#EC4899",
    songTag: CreateSongRequestSongTag.NONE,
    country: CreateSongRequestCountry.NONE,
  });

  const handleCheckYoutube = () => {
    setYoutubeError("");
    const id = extractYoutubeId(youtubeInput);
    if (!id) {
      setYoutubeError(
        "Couldn't extract a YouTube ID from that link. Try a full URL or just the video ID.",
      );
      return;
    }
    setYoutubeId(id);
    setStep("preview");
  };

  const handleGetDetails = async () => {
    setIsLoadingAI(true);
    setAiError("");

    try {
      const songData = await getSongMetadata({
        youtubeUrl: `youtube.com/watch?v=${youtubeId}`,
      });

      setFormData({
        title: songData.content?.title ?? "",
        artist: songData.content?.artist ?? "",
        releaseYear: songData.content?.release_year ?? "",
        gradientColor1: songData.content?.gradient_color1
          ? `#${songData.content.gradient_color1}`
          : "#8B5CF6",
        gradientColor2: songData.content?.gradient_color2
          ? `#${songData.content.gradient_color2}`
          : "#EC4899",
        songTag: CreateSongRequestSongTag.NONE,
        country: CreateSongRequestCountry.NONE,
      });
      setStep("details");
    } catch {
      setAiError("Failed to get details. You can still fill them in manually.");
      setStep("details");
    } finally {
      setIsLoadingAI(false);
    }
  };

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { id, value } = e.target;
    setFormData((prev) => ({ ...prev, [id]: value }));
  };

  const handleColorChange = (id: string, value: string) => {
    setFormData((prev) => ({ ...prev, [id]: value }));
  };

  const handleSubmit = () => {
    setSubmitError("");

    const releaseYear =
      typeof formData.releaseYear === "string"
        ? parseInt(formData.releaseYear)
        : formData.releaseYear;
    const currentYear = new Date().getFullYear();

    if (!youtubeId || !formData.title || !formData.artist) {
      setSubmitError("Fill in the YouTube link, title, and artist.");
      return;
    }
    if (
      !Number.isFinite(releaseYear) ||
      releaseYear < MIN_RELEASE_YEAR ||
      releaseYear > currentYear
    ) {
      setSubmitError(
        `Release year must be between ${MIN_RELEASE_YEAR} and ${currentYear}.`,
      );
      return;
    }
    if (
      !HEX_COLOR_PATTERN.test(formData.gradientColor1) ||
      !HEX_COLOR_PATTERN.test(formData.gradientColor2)
    ) {
      setSubmitError("Both gradient colors must be a 6-character hex value.");
      return;
    }

    addSong(
      {
        playlistId,
        data: {
          youtubeId,
          title: formData.title,
          artist: formData.artist,
          releaseYear,
          gradientColor1: formData.gradientColor1.replace("#", ""),
          gradientColor2: formData.gradientColor2.replace("#", ""),
          songTag: formData.songTag,
          country: formData.country,
        },
      },
      {
        onSuccess: () => {
          queryClient.invalidateQueries({
            queryKey: getGetPlaylistQueryKey(playlistId),
          });
          router.push(`/playlists/${playlistId}`);
        },
        onError: () => {
          setSubmitError("Couldn't add the song. Try again.");
        },
      },
    );
  };

  const handleSkipAi = () => {
    setStep("details");
  };

  return (
    <div className="mx-auto w-full max-w-xl flex flex-col gap-6">
      <div className="grid gap-3">
        <Label htmlFor="youtubeUrl">YouTube Link</Label>
        <div className="flex gap-2">
          <Input
            id="youtubeUrl"
            placeholder="https://youtube.com/watch?v=... or video ID"
            value={youtubeInput}
            onChange={(e) => {
              setYoutubeInput(e.target.value);
              setYoutubeError("");
            }}
            onKeyDown={(e) => e.key === "Enter" && handleCheckYoutube()}
          />
          <Button
            variant="outline"
            onClick={handleCheckYoutube}
            className="shrink-0"
          >
            {step !== "youtube" ? (
              <IconCheck className="size-4 text-green-500" />
            ) : (
              "Check"
            )}
          </Button>
        </div>
        {youtubeError && (
          <p className="text-sm text-destructive">{youtubeError}</p>
        )}
      </div>

      {(step === "preview" || step === "details") && youtubeId && (
        <div className="grid gap-4 animate-in fade-in slide-in-from-bottom-2 duration-300">
          <div className="flex justify-center">
            <div className="aspect-video w-full max-w-xs overflow-hidden rounded-lg border bg-muted shadow-sm">
              <iframe
                width="100%"
                height="100%"
                src={`https://www.youtube.com/embed/${youtubeId}`}
                title="YouTube video player"
                allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
                allowFullScreen
              />
            </div>
          </div>

          <div className="flex gap-2 justify-center">
            <Button variant="outline" size="icon" asChild>
              <a
                href={`https://www.youtube.com/watch?v=${youtubeId}`}
                target="_blank"
                rel="noopener noreferrer"
              >
                <IconExternalLink className="size-4" />
              </a>
            </Button>

            {step === "preview" && (
              <>
                <Button
                  onClick={handleGetDetails}
                  disabled={isLoadingAI}
                  className="gap-2"
                >
                  {isLoadingAI ? (
                    <>
                      <IconLoader2 className="size-4 animate-spin" />
                      Fetching details...
                    </>
                  ) : (
                    <>
                      <IconSparkles className="size-4" />
                      Get Details with AI
                    </>
                  )}
                </Button>
                <Button
                  onClick={handleSkipAi}
                  disabled={isLoadingAI}
                  className="gap-2"
                >
                  Enter manually
                </Button>
              </>
            )}

            {step === "details" && (
              <Button
                variant="outline"
                onClick={handleGetDetails}
                disabled={isLoadingAI}
                className="gap-2"
              >
                {isLoadingAI ? (
                  <IconLoader2 className="size-4 animate-spin" />
                ) : (
                  <IconSparkles className="size-4" />
                )}
                Retry AI
              </Button>
            )}
          </div>

          {aiError && (
            <p className="text-sm text-destructive text-center">{aiError}</p>
          )}
        </div>
      )}

      {step === "details" && (
        <div className="flex flex-col gap-6 animate-in fade-in slide-in-from-bottom-2 duration-300">
          <div className="grid gap-2">
            <Label htmlFor="title">Title</Label>
            <Input id="title" value={formData.title} onChange={handleChange} />
          </div>

          <div className="grid gap-2">
            <Label htmlFor="artist">Artist</Label>
            <Input
              id="artist"
              value={formData.artist}
              onChange={handleChange}
            />
          </div>

          <div className="grid gap-2">
            <Label htmlFor="releaseYear">Release Year</Label>
            <Input
              id="releaseYear"
              type="number"
              min={MIN_RELEASE_YEAR}
              max={new Date().getFullYear()}
              value={formData.releaseYear}
              onChange={handleChange}
            />
          </div>

          <div className="grid gap-2">
            <Label>Tag</Label>
            <div className="flex gap-3 flex-wrap">
              {Object.values(CreateSongRequestSongTag).map((tag) => (
                <label
                  key={tag}
                  className="flex items-center gap-1.5 cursor-pointer"
                >
                  <input
                    type="radio"
                    name="songTag"
                    value={tag}
                    checked={formData.songTag === tag}
                    onChange={() =>
                      setFormData((prev) => ({ ...prev, songTag: tag }))
                    }
                  />
                  <span className="text-sm">{tag}</span>
                </label>
              ))}
            </div>
          </div>

          <div className="grid gap-2">
            <Label>Country</Label>
            <div className="flex gap-3 flex-wrap">
              {Object.values(CreateSongRequestCountry).map((c) => (
                <label
                  key={c}
                  className="flex items-center gap-1.5 cursor-pointer"
                >
                  <input
                    type="radio"
                    name="country"
                    value={c}
                    checked={formData.country === c}
                    onChange={() =>
                      setFormData((prev) => ({ ...prev, country: c }))
                    }
                  />
                  <span className="text-sm">{c}</span>
                </label>
              ))}
            </div>
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div className="flex items-center gap-3">
              <Input
                type="color"
                value={formData.gradientColor1}
                onChange={(e) =>
                  handleColorChange("gradientColor1", e.target.value)
                }
                className="size-8 p-0 border-none rounded shadow-sm shrink-0 cursor-pointer overflow-hidden"
              />
              <div className="grid gap-1 w-full">
                <Label className="text-[10px] uppercase">Color 1</Label>
                <Input
                  value={formData.gradientColor1}
                  onChange={(e) =>
                    handleColorChange("gradientColor1", e.target.value)
                  }
                  className="h-8 font-mono text-xs"
                />
              </div>
            </div>
            <div className="flex items-center gap-3">
              <Input
                type="color"
                value={formData.gradientColor2}
                onChange={(e) =>
                  handleColorChange("gradientColor2", e.target.value)
                }
                className="size-8 p-0 border-none rounded shadow-sm shrink-0 cursor-pointer overflow-hidden"
              />
              <div className="grid gap-1 w-full">
                <Label className="text-[10px] uppercase">Color 2</Label>
                <Input
                  value={formData.gradientColor2}
                  onChange={(e) =>
                    handleColorChange("gradientColor2", e.target.value)
                  }
                  className="h-8 font-mono text-xs"
                />
              </div>
            </div>
          </div>

          <div className="flex flex-col items-center py-4">
            <div
              className="relative aspect-square w-50 rounded-lg shadow-xl flex flex-col items-center justify-between p-4 text-white overflow-hidden"
              style={{
                background: `linear-gradient(to bottom, ${formData.gradientColor1}, ${formData.gradientColor2})`,
                fontFamily: "'Kanit', sans-serif",
              }}
            >
              <div
                className="mt-2 text-center font-normal leading-tight"
                style={{ fontSize: "15px" }}
              >
                {formData.artist || "Artist"}
              </div>
              <div
                className="font-medium tracking-tighter"
                style={{ fontSize: "62px" }}
              >
                {formData.releaseYear || "Year"}
              </div>
              <div
                className="mb-2 text-center italic font-light leading-tight"
                style={{ fontSize: "15px" }}
              >
                {formData.title || "Title"}
              </div>
            </div>
          </div>

          {submitError && (
            <p className="text-sm text-destructive text-center">
              {submitError}
            </p>
          )}

          <div className="flex gap-3 justify-center">
            <Button onClick={handleSubmit} className="px-10" disabled={isAdding}>
              {isAdding ? "Adding..." : "Add Song"}
            </Button>
            <Button variant="outline" className="px-10" asChild>
              <Link href={backPath}>Cancel</Link>
            </Button>
          </div>
        </div>
      )}
    </div>
  );
}
