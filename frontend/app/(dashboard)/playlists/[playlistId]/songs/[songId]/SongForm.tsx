"use client";

import React, { useState } from "react";
import { Input } from "@/components/shadcn/input";
import { Label } from "@/components/shadcn/label";
import { Button } from "@/components/shadcn/button";
import { IconExternalLink } from "@tabler/icons-react";
import Link from "next/link";
import {
  CreateSongRequestCountry,
  CreateSongRequestSongTag,
  SongDTO,
} from "@/hooks/models";
import { useRouter } from "next/navigation";
import { useQueryClient } from "@tanstack/react-query";
import {
  getGetPlaylistQueryKey,
  getGetSongQueryKey,
  useUpdateSong,
} from "@/hooks/generated/playlist-management/playlist-management";

const YOUTUBE_ID_PATTERN = /^[a-zA-Z0-9_-]{11}$/;
const HEX_COLOR_PATTERN = /^#[0-9a-fA-F]{6}$/;
const MIN_RELEASE_YEAR = 1000;

interface SongFormProps {
  song: SongDTO;
  backPath: string;
  playlistId: number;
}

interface SongFormData {
  youtubeId: string;
  title: string;
  artist: string;
  releaseYear: number | string;
  gradientColor1: string;
  gradientColor2: string;
  songTag: CreateSongRequestSongTag;
  country: CreateSongRequestCountry;
}

export function SongForm({ song, backPath, playlistId }: SongFormProps) {
  const router = useRouter();
  const queryClient = useQueryClient();
  const { mutate: updateSong, isPending } = useUpdateSong();
  const [submitError, setSubmitError] = useState("");

  const [formData, setFormData] = useState<SongFormData>({
    youtubeId: song.youtubeId,
    title: song.title,
    artist: song.artist,
    releaseYear: song.releaseYear,
    gradientColor1: song.gradientColor1 ? `#${song.gradientColor1}` : "#8B5CF6",
    gradientColor2: song.gradientColor2 ? `#${song.gradientColor2}` : "#EC4899",
    songTag: CreateSongRequestSongTag.NONE,
    country: CreateSongRequestCountry.NONE,
  });

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { id, value } = e.target;
    setFormData((prev: any) => ({ ...prev, [id]: value }));
  };

  const handleColorChange = (id: string, value: string) => {
    setFormData((prev: any) => ({ ...prev, [id]: value }));
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
          songTag: formData.songTag,
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

  return (
    <div className="mx-auto w-full max-w-xl flex flex-col gap-6">
      <div className="grid gap-4">
        <div className="flex justify-center">
          <div className="aspect-video w-full max-w-xs overflow-hidden rounded-lg border bg-muted shadow-sm">
            <iframe
              width="100%"
              height="100%"
              src={`https://www.youtube.com/embed/${formData.youtubeId}`}
              title="YouTube video player"
              allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
              allowFullScreen
            ></iframe>
          </div>
        </div>
        <div className="grid gap-2">
          <Label htmlFor="youtubeId">YouTube ID</Label>
          <div className="flex gap-2">
            <Input
              id="youtubeId"
              value={formData.youtubeId}
              onChange={handleChange}
              className="text-left"
            />
            <Button variant="outline" size="icon" asChild>
              <a
                href={`https://www.youtube.com/watch?v=${formData.youtubeId}`}
                target="_blank"
                rel="noopener noreferrer"
              >
                <IconExternalLink className="size-4" />
              </a>
            </Button>
          </div>
        </div>
      </div>

      <div className="grid gap-2">
        <Label htmlFor="title">Title</Label>
        <Input
          id="title"
          value={formData.title}
          onChange={handleChange}
          className="text-left"
        />
      </div>

      <div className="grid gap-2">
        <Label htmlFor="artist">Artist</Label>
        <Input
          id="artist"
          value={formData.artist}
          onChange={handleChange}
          className="text-left"
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
          className="text-left"
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
            <label key={c} className="flex items-center gap-1.5 cursor-pointer">
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
            {formData.artist}
          </div>

          <div
            className="font-medium tracking-tighter"
            style={{ fontSize: "62px" }}
          >
            {formData.releaseYear}
          </div>

          <div
            className="mb-2 text-center italic font-light leading-tight"
            style={{ fontSize: "15px" }}
          >
            {formData.title}
          </div>
        </div>
      </div>

      {submitError && (
        <p className="text-sm text-destructive text-center">{submitError}</p>
      )}

      <div className="flex gap-3 justify-center">
        <Button className="px-10" onClick={handleSubmit} disabled={isPending}>
          {isPending ? "Saving..." : "Save Changes"}
        </Button>
        <Button variant="outline" className="px-10" asChild>
          <Link href={backPath}>Cancel</Link>
        </Button>
      </div>
    </div>
  );
}
