"use client";

import React, { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useQueryClient } from "@tanstack/react-query";
import { ChevronDown, ExternalLink, HelpCircle } from "lucide-react";

import { Input } from "@/components/shadcn/input";
import { Label } from "@/components/shadcn/label";
import { Button } from "@/components/shadcn/button";
import { Badge } from "@/components/shadcn/badge";
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from "@/components/shadcn/collapsible";
import { CreateSongRequestCountry, SongDTO, SongDTOVerificationStatus } from "@/hooks/models";
import {
  getGetPlaylistQueryKey,
  getGetSongQueryKey,
  useUpdateSong,
} from "@/hooks/generated/playlist-management/playlist-management";
import { GameCard } from "@/components/game-card";

const YOUTUBE_ID_PATTERN = /^[a-zA-Z0-9_-]{11}$/;
const HEX_COLOR_PATTERN = /^#[0-9a-fA-F]{6}$/;
const MIN_RELEASE_YEAR = 1000;

const EDIT_FIELD_CLASSES =
  "border-2 border-destructive/70 bg-background focus-visible:border-destructive focus-visible:ring-destructive/20";

interface SongFormProps {
  song: SongDTO;
  playlistId: number;
  playlistName?: string;
  backPath: string;
}

interface SongFormData {
  youtubeId: string;
  title: string;
  artist: string;
  releaseYear: number | string;
  gradientColor1: string;
  gradientColor2: string;
  country: CreateSongRequestCountry;
}

export function SongForm({
  song,
  playlistId,
  playlistName,
  backPath,
}: SongFormProps) {
  const router = useRouter();
  const queryClient = useQueryClient();
  const { mutate: updateSong, isPending } = useUpdateSong();
  const [submitError, setSubmitError] = useState("");
  const [advancedOpen, setAdvancedOpen] = useState(false);

  const [formData, setFormData] = useState<SongFormData>({
    youtubeId: song.youtubeId,
    title: song.title,
    artist: song.artists[0]?.name ?? "",
    releaseYear: song.releaseYear,
    gradientColor1: song.gradientColor1 ? `#${song.gradientColor1}` : "#8B5CF6",
    gradientColor2: song.gradientColor2 ? `#${song.gradientColor2}` : "#EC4899",
    country: song.country ?? CreateSongRequestCountry.NONE,
  });

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { id, value } = e.target;
    setFormData((prev: SongFormData) => ({ ...prev, [id]: value }));
  };

  const handleColorChange = (id: string, value: string) => {
    setFormData((prev: SongFormData) => ({ ...prev, [id]: value }));
  };

  const handleSubmit = () => {
    setSubmitError("");

    const releaseYear =
      typeof formData.releaseYear === "string"
        ? parseInt(formData.releaseYear)
        : formData.releaseYear;
    const currentYear = new Date().getFullYear();

    if (!formData.youtubeId || !formData.title || !formData.artist) {
      setSubmitError("Fill in the YouTube ID, title, and artist.");
      return;
    }
    if (!YOUTUBE_ID_PATTERN.test(formData.youtubeId)) {
      setSubmitError("YouTube ID must be 11 characters.");
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

    updateSong(
      {
        playlistId,
        songId: song.id,
        data: {
          youtubeId: formData.youtubeId,
          title: formData.title,
          artist: formData.artist,
          releaseYear,
          gradientColor1: formData.gradientColor1.replace("#", ""),
          gradientColor2: formData.gradientColor2.replace("#", ""),
          country: formData.country,
        },
      },
      {
        onSuccess: () => {
          queryClient.invalidateQueries({
            queryKey: getGetSongQueryKey(playlistId, song.id),
          });
          queryClient.invalidateQueries({
            queryKey: getGetPlaylistQueryKey(playlistId),
          });
          router.push(backPath);
        },
        onError: () => {
          setSubmitError("Couldn't save changes. Try again.");
        },
      },
    );
  };

  const isManualEntry =
    song.verificationStatus === SongDTOVerificationStatus.MANUAL_ENTRY;

  return (
    <div className="flex w-full max-w-[560px] flex-col items-center">
      <h1
        className="mb-1.5 w-full text-center font-display text-2xl text-accent"
        style={{ textShadow: "3px 3px 0 var(--text-shadow-on-card)" }}
      >
        Edit song details
      </h1>
      {playlistName && (
        <div className="mb-4 text-center text-xs text-muted-foreground">
          From {playlistName}
        </div>
      )}

      {isManualEntry && (
        <Badge variant="destructive" className="mb-5 gap-1.5 py-2 text-[11px]">
          <HelpCircle className="size-3" />
          Manual entry
        </Badge>
      )}

      <div className="mb-6 w-[160px]">
        <GameCard
          size="sm"
          artist={formData.artist}
          year={formData.releaseYear}
          title={formData.title}
          gradientColor1={formData.gradientColor1}
          gradientColor2={formData.gradientColor2}
        />
      </div>

      <div className="w-full">
        <div className="mb-4 grid gap-1.5">
          <Label
            htmlFor="title"
            className="text-[11px] font-semibold tracking-wide text-muted-foreground uppercase"
          >
            Title
          </Label>
          <Input
            id="title"
            value={formData.title}
            onChange={handleChange}
            className={EDIT_FIELD_CLASSES}
          />
        </div>
        <div className="mb-4 grid gap-1.5">
          <Label
            htmlFor="artist"
            className="text-[11px] font-semibold tracking-wide text-muted-foreground uppercase"
          >
            Artist
          </Label>
          <Input
            id="artist"
            value={formData.artist}
            onChange={handleChange}
            className={EDIT_FIELD_CLASSES}
          />
        </div>
        <div className="mb-5 grid gap-1.5">
          <Label
            htmlFor="releaseYear"
            className="text-[11px] font-semibold tracking-wide text-muted-foreground uppercase"
          >
            Release year
          </Label>
          <Input
            id="releaseYear"
            type="number"
            min={MIN_RELEASE_YEAR}
            max={new Date().getFullYear()}
            value={formData.releaseYear}
            onChange={handleChange}
            className={EDIT_FIELD_CLASSES}
          />
        </div>
      </div>

      <p className="mb-6 text-center text-xs leading-relaxed text-muted-foreground">
        {isManualEntry
          ? "No source had any data on this one, not even Wikipedia. These are the details entered by hand. Check them, fix anything that's wrong, then save."
          : "This song hasn't been verified yet. Check the details below, fix anything that's wrong, then save."}
      </p>

      <Collapsible
        open={advancedOpen}
        onOpenChange={setAdvancedOpen}
        className="mb-5 w-full"
      >
        <CollapsibleTrigger asChild>
          <button
            type="button"
            className="flex w-full items-center justify-between text-[11px] font-semibold tracking-wide text-muted-foreground uppercase"
          >
            Advanced details
            <ChevronDown
              className={`size-3.5 transition-transform ${advancedOpen ? "rotate-180" : ""}`}
            />
          </button>
        </CollapsibleTrigger>
        <CollapsibleContent className="mt-4 flex flex-col gap-4">
          <div className="grid gap-1.5">
            <Label
              htmlFor="youtubeId"
              className="text-[11px] font-semibold tracking-wide text-muted-foreground uppercase"
            >
              YouTube ID
            </Label>
            <div className="flex gap-2">
              <Input
                id="youtubeId"
                value={formData.youtubeId}
                onChange={handleChange}
              />
              <Button variant="outline" size="icon" asChild>
                <a
                  href={`https://www.youtube.com/watch?v=${formData.youtubeId}`}
                  target="_blank"
                  rel="noopener noreferrer"
                >
                  <ExternalLink className="size-4" />
                </a>
              </Button>
            </div>
          </div>

          <div className="grid gap-1.5">
            <Label className="text-[11px] font-semibold tracking-wide text-muted-foreground uppercase">
              Country
            </Label>
            <div className="flex flex-wrap gap-3">
              {Object.values(CreateSongRequestCountry).map((country) => (
                <label
                  key={country}
                  className="flex cursor-pointer items-center gap-1.5"
                >
                  <input
                    type="radio"
                    name="country"
                    value={country}
                    checked={formData.country === country}
                    onChange={() =>
                      setFormData((prev) => ({ ...prev, country }))
                    }
                  />
                  <span className="text-sm">{country}</span>
                </label>
              ))}
            </div>
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div className="flex items-center gap-3">
              <Input
                type="color"
                value={formData.gradientColor1}
                onChange={(event) =>
                  handleColorChange("gradientColor1", event.target.value)
                }
                className="size-8 shrink-0 cursor-pointer overflow-hidden rounded-md border-none p-0 shadow-sm"
              />
              <div className="grid w-full gap-1">
                <Label className="text-[10px] uppercase">Color 1</Label>
                <Input
                  value={formData.gradientColor1}
                  onChange={(event) =>
                    handleColorChange("gradientColor1", event.target.value)
                  }
                  className="h-8 font-mono text-xs"
                />
              </div>
            </div>
            <div className="flex items-center gap-3">
              <Input
                type="color"
                value={formData.gradientColor2}
                onChange={(event) =>
                  handleColorChange("gradientColor2", event.target.value)
                }
                className="size-8 shrink-0 cursor-pointer overflow-hidden rounded-md border-none p-0 shadow-sm"
              />
              <div className="grid w-full gap-1">
                <Label className="text-[10px] uppercase">Color 2</Label>
                <Input
                  value={formData.gradientColor2}
                  onChange={(event) =>
                    handleColorChange("gradientColor2", event.target.value)
                  }
                  className="h-8 font-mono text-xs"
                />
              </div>
            </div>
          </div>
        </CollapsibleContent>
      </Collapsible>

      {submitError && (
        <p className="mb-4 text-center text-sm text-destructive">
          {submitError}
        </p>
      )}

      <div className="mb-4.5 flex w-full gap-3">
        <Button
          variant="outline"
          className="flex-1"
          onClick={() => router.push(backPath)}
        >
          Cancel
        </Button>
        <Button
          className="flex-1"
          onClick={handleSubmit}
          disabled={isPending}
        >
          {isPending ? "Saving..." : "Save changes"}
        </Button>
      </div>

      <Link
        href={backPath}
        className="text-xs text-muted-foreground hover:text-foreground"
      >
        ‹ Back to playlist
      </Link>
    </div>
  );
}
