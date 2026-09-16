"use client";

import { useState } from "react";
import { ChevronDown, Flag, Lock, ShieldCheck } from "lucide-react";

import { Input } from "@/components/shadcn/input";
import { Label } from "@/components/shadcn/label";
import { Button } from "@/components/shadcn/button";
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from "@/components/shadcn/collapsible";
import { GameCard } from "@/components/game-card";
import { CreateSongRequest, CreateSongRequestCountry } from "@/hooks/models";

const EDITABLE_FIELD_CLASSES =
  "border-2 border-warning/70 bg-background focus-visible:border-warning focus-visible:ring-warning/20";

export interface PendingSongDetails {
  title: string;
  artist: string;
  releaseYear: string | number;
  gradientColor1: string;
  gradientColor2: string;
  country: CreateSongRequestCountry;
  /** True when the metadata pipeline resolved every field with high confidence. */
  isHighConfidence: boolean;
}

interface NewSongReviewStepProps {
  youtubeId: string;
  details: PendingSongDetails;
  onSubmit: (request: CreateSongRequest) => void;
  isSubmitting: boolean;
  submitError: string;
}

export function NewSongReviewStep({
  youtubeId,
  details,
  onSubmit,
  isSubmitting,
  submitError,
}: NewSongReviewStepProps) {
  const [forceEditable, setForceEditable] = useState(false);
  const [advancedOpen, setAdvancedOpen] = useState(false);
  const [formError, setFormError] = useState("");
  const [formData, setFormData] = useState({
    title: details.title,
    artist: details.artist,
    releaseYear: details.releaseYear,
    gradientColor1: details.gradientColor1,
    gradientColor2: details.gradientColor2,
    country: details.country,
  });

  const isLocked = details.isHighConfidence && !forceEditable;

  const handleChange = (field: "title" | "artist" | "releaseYear", value: string) => {
    setFormData((prev) => ({ ...prev, [field]: value }));
  };

  const handleSubmit = () => {
    setFormError("");
    const releaseYear =
      typeof formData.releaseYear === "string"
        ? parseInt(formData.releaseYear)
        : formData.releaseYear;
    const currentYear = new Date().getFullYear();

    if (!formData.title.trim() || !formData.artist.trim()) {
      setFormError("Fill in the title and artist.");
      return;
    }
    if (
      !Number.isFinite(releaseYear) ||
      releaseYear < 1000 ||
      releaseYear > currentYear
    ) {
      setFormError(`Release year must be between 1000 and ${currentYear}.`);
      return;
    }

    onSubmit({
      youtubeId,
      title: formData.title.trim(),
      artist: formData.artist.trim(),
      releaseYear,
      gradientColor1: formData.gradientColor1.replace("#", ""),
      gradientColor2: formData.gradientColor2.replace("#", ""),
      country: formData.country,
    });
  };

  return (
    <div className="flex w-full max-w-[460px] flex-col items-center">
      <div className="mb-4.5 rounded-full border-2 border-border bg-background px-3.5 py-1.5 font-display text-[10px] tracking-wide text-muted-foreground">
        STEP 2 OF 2
      </div>

      <h1
        className="mb-4 w-full text-center font-display text-2xl text-accent"
        style={{ textShadow: "3px 3px 0 var(--text-shadow-on-card)" }}
      >
        Review details
      </h1>

      {isLocked ? (
        <div className="mb-5.5 inline-flex items-center gap-1.5 rounded-full bg-[#499f36]/14 px-4 py-2 font-display text-[11px] text-[#499f36] dark:bg-[#a6e3a1]/16 dark:text-[#a6e3a1]">
          <ShieldCheck className="size-3" />
          High confidence
        </div>
      ) : (
        <div className="mb-5.5 inline-flex items-center gap-1.5 rounded-full bg-warning/16 px-4 py-2 font-display text-[11px] text-warning">
          <Flag className="size-3" />
          Needs review
        </div>
      )}

      <div className="mb-6 w-[160px]">
        <GameCard
          size="sm"
          artist={formData.artist}
          year={formData.releaseYear}
          title={formData.title}
          gradientColor1={formData.gradientColor1}
          gradientColor2={formData.gradientColor2}
          placeholder={!formData.artist && !formData.releaseYear}
        />
      </div>

      {isLocked ? (
        <div className="w-full">
          {(
            [
              ["Title", formData.title],
              ["Artist", formData.artist],
              ["Release year", formData.releaseYear],
            ] as const
          ).map(([label, value]) => (
            <div
              key={label}
              className="mb-3 flex w-full items-center justify-between rounded-[13px] border-2 border-border bg-background px-4 py-3 last:mb-5.5"
            >
              <div>
                <div className="mb-0.5 text-[11px] tracking-wide text-muted-foreground uppercase">
                  {label}
                </div>
                <div className="text-sm">{value}</div>
              </div>
              <Lock className="size-3.5 text-muted-foreground" />
            </div>
          ))}
        </div>
      ) : (
        <div className="w-full">
          <div className="mb-4 grid gap-1.5">
            <Label className="text-[11px] font-semibold tracking-wide text-muted-foreground uppercase">
              Title
            </Label>
            <Input
              value={formData.title}
              onChange={(event) => handleChange("title", event.target.value)}
              placeholder="Enter the title"
              className={EDITABLE_FIELD_CLASSES}
            />
          </div>
          <div className="mb-4 grid gap-1.5">
            <Label className="text-[11px] font-semibold tracking-wide text-muted-foreground uppercase">
              Artist
            </Label>
            <Input
              value={formData.artist}
              onChange={(event) => handleChange("artist", event.target.value)}
              placeholder="Enter the artist"
              className={EDITABLE_FIELD_CLASSES}
            />
          </div>
          <div className="mb-5 grid gap-1.5">
            <Label className="text-[11px] font-semibold tracking-wide text-muted-foreground uppercase">
              Release year
            </Label>
            <Input
              type="number"
              min={1000}
              max={new Date().getFullYear()}
              value={formData.releaseYear}
              onChange={(event) =>
                handleChange("releaseYear", event.target.value)
              }
              placeholder="Enter the release year"
              className={EDITABLE_FIELD_CLASSES}
            />
          </div>
        </div>
      )}

      <p className="mb-4.5 max-w-[420px] text-center text-xs leading-relaxed text-muted-foreground">
        {isLocked
          ? "We're confident these are right, so they're locked."
          : details.isHighConfidence
            ? "Fields unlocked for editing."
            : "We got what we could from the video. Fill in or fix anything that's missing."}
      </p>

      {isLocked && (
        <button
          type="button"
          onClick={() => setForceEditable(true)}
          className="mb-6 flex items-center gap-1.5 text-xs text-destructive"
        >
          <Flag className="size-3" />
          Something wrong? Report and edit manually
        </button>
      )}

      {!isLocked && (
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
                    setFormData((prev) => ({
                      ...prev,
                      gradientColor1: event.target.value,
                    }))
                  }
                  className="size-8 shrink-0 cursor-pointer overflow-hidden rounded-md border-none p-0 shadow-sm"
                />
                <Input
                  value={formData.gradientColor1}
                  onChange={(event) =>
                    setFormData((prev) => ({
                      ...prev,
                      gradientColor1: event.target.value,
                    }))
                  }
                  className="h-8 font-mono text-xs"
                />
              </div>
              <div className="flex items-center gap-3">
                <Input
                  type="color"
                  value={formData.gradientColor2}
                  onChange={(event) =>
                    setFormData((prev) => ({
                      ...prev,
                      gradientColor2: event.target.value,
                    }))
                  }
                  className="size-8 shrink-0 cursor-pointer overflow-hidden rounded-md border-none p-0 shadow-sm"
                />
                <Input
                  value={formData.gradientColor2}
                  onChange={(event) =>
                    setFormData((prev) => ({
                      ...prev,
                      gradientColor2: event.target.value,
                    }))
                  }
                  className="h-8 font-mono text-xs"
                />
              </div>
            </div>
          </CollapsibleContent>
        </Collapsible>
      )}

      {(formError || submitError) && (
        <p className="mb-4 text-center text-sm text-destructive">
          {formError || submitError}
        </p>
      )}

      <Button className="w-full" onClick={handleSubmit} disabled={isSubmitting}>
        {isSubmitting
          ? "Adding..."
          : isLocked
            ? "Add to playlist"
            : "Save & add to playlist"}
      </Button>
    </div>
  );
}
