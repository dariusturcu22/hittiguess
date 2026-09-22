"use client";

import React from "react";

import { pixelizeImage } from "@/lib/pixelate";

interface PixelImageInputProps {
  label: string;
  disabled?: boolean;
  buttonClassName?: string;
  onPixelized: (pixelBlob: Blob, previewUrl: string) => void;
  onError?: (message: string) => void;
  children: React.ReactNode;
}

// A file picker that pixelizes the chosen picture before handing it over, so
// every upload path stores pixel art without each caller redoing the canvas work.
export function PixelImageInput({
  label,
  disabled,
  buttonClassName,
  onPixelized,
  onError,
  children,
}: PixelImageInputProps) {
  const fileInputReference = React.useRef<HTMLInputElement>(null);

  async function handleFileSelected(event: React.ChangeEvent<HTMLInputElement>) {
    const [chosenFile] = event.target.files ?? [];
    event.target.value = "";
    if (!chosenFile) {
      return;
    }
    try {
      const pixelBlob = await pixelizeImage(chosenFile);
      onPixelized(pixelBlob, URL.createObjectURL(pixelBlob));
    } catch (error) {
      onError?.(error instanceof Error ? error.message : "Could not read that image");
    }
  }

  return (
    <>
      <input
        ref={fileInputReference}
        type="file"
        accept="image/*"
        tabIndex={-1}
        aria-hidden="true"
        disabled={disabled}
        onChange={handleFileSelected}
        className="hidden"
      />
      <button
        type="button"
        aria-label={label}
        disabled={disabled}
        onClick={() => fileInputReference.current?.click()}
        className={buttonClassName}
      >
        {children}
      </button>
    </>
  );
}
