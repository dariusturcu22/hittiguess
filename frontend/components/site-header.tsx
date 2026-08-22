"use client";

import * as React from "react";
import { IconCheck, IconPencil, IconX } from "@tabler/icons-react";
import { Separator } from "@/components/shadcn/separator";
import { SidebarTrigger } from "@/components/shadcn/sidebar";
import { Input } from "@/components/shadcn/input";
import { Button } from "@/components/shadcn/button";

export function SiteHeader({
  title = "Playlists",
  color,
  onTitleChange,
  onColorChange,
}: {
  title?: string;
  color?: string;
  onTitleChange?: (newTitle: string) => void;
  onColorChange?: (newColor: string) => void;
}) {
  const [isEditing, setIsEditing] = React.useState(false);
  const [titleValue, setTitleValue] = React.useState(title);
  const [colorValue, setColorValue] = React.useState(color ?? "#000000");
  const inputRef = React.useRef<HTMLInputElement>(null);

  React.useEffect(() => {
    setTitleValue(title);
  }, [title]);

  React.useEffect(() => {
    setColorValue(color ?? "#000000");
  }, [color]);

  React.useEffect(() => {
    if (isEditing) {
      inputRef.current?.focus();
      inputRef.current?.select();
    }
  }, [isEditing]);

  const handleSave = () => {
    if (titleValue.trim() === "") {
      handleCancel();
      return;
    }
    onTitleChange?.(titleValue.trim());
    setIsEditing(false);
  };

  const handleCancel = () => {
    setTitleValue(title);
    setIsEditing(false);
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === "Enter") handleSave();
    if (e.key === "Escape") handleCancel();
  };

  return (
    <header className="flex h-(--header-height) shrink-0 items-center gap-2 border-b transition-[width,height] ease-linear group-has-data-[collapsible=icon]/sidebar-wrapper:h-(--header-height)">
      <div className="flex w-full items-center gap-1 px-4 lg:gap-2 lg:px-6">
        <SidebarTrigger className="-ml-1" />
        <Separator
          orientation="vertical"
          className="mx-2 data-[orientation=vertical]:h-4"
        />
        {isEditing ? (
          <div className="flex items-center gap-1">
            <Input
              ref={inputRef}
              value={titleValue}
              onChange={(e) => setTitleValue(e.target.value)}
              onKeyDown={handleKeyDown}
              className="h-7 text-sm font-medium py-0 px-2 w-48"
            />
            <Button
              variant="ghost"
              size="icon"
              className="size-7"
              onClick={handleSave}
            >
              <IconCheck className="size-3.5 text-green-500" />
            </Button>
            <Button
              variant="ghost"
              size="icon"
              className="size-7"
              onClick={handleCancel}
            >
              <IconX className="size-3.5 text-destructive" />
            </Button>
          </div>
        ) : (
          <div className="flex items-center gap-1 group/title">
            <h1 className="text-base font-medium">{title}</h1>
            <Button
              variant="ghost"
              size="icon"
              className="size-7 opacity-60 hover:opacity-100 transition-opacity"
              onClick={() => setIsEditing(true)}
            >
              <IconPencil className="size-3.5" />
            </Button>
          </div>
        )}

        {onColorChange && (
          <Input
            type="color"
            value={colorValue}
            onChange={(e) => setColorValue(e.target.value)}
            onBlur={() => {
              onColorChange(colorValue);
            }}
            className="size-8 p-0 border-none rounded shadow-sm shrink-0 cursor-pointer overflow-hidden"
          />
        )}
      </div>
    </header>
  );
}
