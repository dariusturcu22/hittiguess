import { fireEvent, render, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import { describe, expect, it, vi } from "vitest";

import { CreateSongRequestCountry, SongDTO, SongDTOVerificationStatus } from "@/hooks/models";
import { SongForm } from "./SongForm";

const updateSong = vi.fn();

vi.mock("next/link", () => ({ default: ({ children }: { children: ReactNode }) => <>{children}</> }));
vi.mock("next/navigation", () => ({ useRouter: () => ({ push: vi.fn() }) }));
vi.mock("@/hooks/generated/playlist-management/playlist-management", () => ({
  getGetPlaylistQueryKey: () => ["playlist"],
  getGetSongQueryKey: () => ["song"],
  useUpdateSong: () => ({ mutate: updateSong, isPending: false }),
}));

const song: SongDTO = {
  id: 1,
  artists: [{ name: "Test Artist" }],
  title: "Test Song",
  releaseYear: 1999,
  youtubeId: "abc12345678",
  verificationStatus: SongDTOVerificationStatus.MANUAL_ENTRY,
  country: CreateSongRequestCountry.NONE,
};

function renderForm() {
  return render(<QueryClientProvider client={new QueryClient()}><SongForm song={song} playlistId={1} backPath="/playlists/1" /></QueryClientProvider>);
}

describe("SongForm validation", () => {
  it("rejects a missing required field", () => {
    renderForm();
    fireEvent.change(screen.getByLabelText("Title"), { target: { value: "" } });
    fireEvent.click(screen.getByRole("button", { name: "Save changes" }));
    expect(screen.getByText("Fill in the YouTube ID, title, and artist.")).toBeInTheDocument();
    expect(updateSong).not.toHaveBeenCalled();
  });

  it("rejects malformed YouTube IDs", () => {
    renderForm();
    fireEvent.click(screen.getByRole("button", { name: /advanced details/i }));
    fireEvent.change(screen.getByLabelText("YouTube ID"), { target: { value: "too-short" } });
    fireEvent.click(screen.getByRole("button", { name: "Save changes" }));
    expect(screen.getByText("YouTube ID must be 11 characters.")).toBeInTheDocument();
    expect(updateSong).not.toHaveBeenCalled();
  });
});
