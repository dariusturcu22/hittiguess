import { fireEvent, render as renderComponent, screen, waitFor, within } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { beforeEach, describe, expect, it, vi } from "vitest";

import RecheckSongsPage from "./page";

const QUEUED_SONG_COUNT = 3;

const hooks = vi.hoisted(() => ({
  candidates: { data: [] as unknown[], isLoading: false, isError: false, refetch: vi.fn() },
  mutateAsync: vi.fn(),
  isPending: false,
}));
const toastMock = vi.hoisted(() => ({ success: vi.fn(), error: vi.fn() }));

vi.mock("sonner", () => ({ toast: toastMock }));
vi.mock("@/hooks/generated/admin-song-catalog/admin-song-catalog", () => ({
  useRecheckCandidates: () => hooks.candidates,
}));
vi.mock("@/hooks/generated/admin-song-pipeline/admin-song-pipeline", () => ({
  useRecheckSongs: () => ({ mutateAsync: hooks.mutateAsync, isPending: hooks.isPending }),
}));

function song(songId: number, title: string, lastCheckedAt: string, overrides: Record<string, unknown> = {}) {
  return {
    songId,
    title,
    artists: ["Artist"],
    releaseYear: 2000,
    verificationStatus: "VERIFIED",
    inActiveGame: false,
    lastCheckedAt,
    ...overrides,
  };
}

const CANDIDATES = [
  song(1, "Dragostea Din Tei", "2026-10-28T10:00:00Z"),
  song(2, "What Is Love", "2026-10-27T10:00:00Z"),
  song(3, "Zi-mi", "2026-10-26T10:00:00Z", { verificationStatus: "NEEDS_REVIEW" }),
  song(4, "Barbie Girl", "2026-10-25T10:00:00Z", { inActiveGame: true }),
  song(5, "Take Five", "2026-09-12T10:00:00Z"),
  song(6, "Motorul", "2026-09-02T10:00:00Z"),
];
const ALL_SELECTABLE_IDS = [1, 2, 3, 5, 6];

function render() {
  return renderComponent(
    <QueryClientProvider client={new QueryClient()}>
      <RecheckSongsPage />
    </QueryClientProvider>,
  );
}

function checkbox(name: string | RegExp) {
  return screen.getByRole("checkbox", { name });
}

describe("RecheckSongsPage", () => {
  beforeEach(() => {
    hooks.candidates = { data: CANDIDATES, isLoading: false, isError: false, refetch: vi.fn() };
    hooks.mutateAsync.mockReset();
    hooks.isPending = false;
    toastMock.success.mockReset();
    toastMock.error.mockReset();
  });

  it("groups songs by month newest first with only the newest month expanded", () => {
    render();

    const months = screen.getAllByTestId("recheck-month");
    expect(months).toHaveLength(2);
    expect(within(months[0]).getByText("October 2026")).toBeInTheDocument();
    expect(within(months[1]).getByText("September 2026")).toBeInTheDocument();
    expect(within(months[0]).getAllByTestId("recheck-song-row")).toHaveLength(4);
    expect(within(months[1]).queryAllByTestId("recheck-song-row")).toHaveLength(0);
    expect(within(months[0]).getByText(/0 of 3 selected/)).toBeInTheDocument();
    expect(screen.getByText(/Grouped by month last checked/)).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Expand September 2026" }));
    expect(within(screen.getAllByTestId("recheck-month")[1]).getAllByTestId("recheck-song-row")).toHaveLength(2);
    fireEvent.click(screen.getByRole("button", { name: "Collapse October 2026" }));
    expect(within(screen.getAllByTestId("recheck-month")[0]).queryAllByTestId("recheck-song-row")).toHaveLength(0);
  });

  it("disables a song in an active game and marks it", () => {
    render();

    expect(checkbox("Select Barbie Girl")).toBeDisabled();
    expect(screen.getByText("IN AN ACTIVE GAME")).toBeInTheDocument();
    expect(checkbox("Select Zi-mi")).toBeEnabled();
  });

  it("selects and clears a whole month, leaving the locked song out", () => {
    render();

    fireEvent.click(checkbox("Select October 2026"));
    expect(checkbox("Select October 2026")).toBeChecked();
    expect(checkbox("Select Dragostea Din Tei")).toBeChecked();
    expect(checkbox("Select Barbie Girl")).not.toBeChecked();
    expect(screen.getByText(/3 of 3 selected/)).toBeInTheDocument();
    expect(screen.getByText("Recheck 3 songs")).toBeInTheDocument();

    fireEvent.click(checkbox("Select October 2026"));
    expect(checkbox("Select October 2026")).not.toBeChecked();
    expect(checkbox("Select Dragostea Din Tei")).not.toBeChecked();
    expect(screen.queryByTestId("recheck-action-bar")).not.toBeInTheDocument();
  });

  it("shows the month and select all boxes as indeterminate while partly selected", () => {
    render();

    fireEvent.click(checkbox("Select Dragostea Din Tei"));

    expect(checkbox("Select October 2026")).toHaveAttribute("aria-checked", "mixed");
    expect(checkbox("Select September 2026")).toHaveAttribute("aria-checked", "false");
    expect(checkbox("Select all")).toHaveAttribute("aria-checked", "mixed");
    expect(screen.getByText(/1 of 3 selected/)).toBeInTheDocument();
    expect(screen.getByText("Recheck 1 song")).toBeInTheDocument();
  });

  it("select all picks every selectable song, including collapsed months", () => {
    render();

    fireEvent.click(checkbox("Select all"));

    expect(checkbox("Select all")).toBeChecked();
    expect(checkbox("Select October 2026")).toBeChecked();
    expect(checkbox("Select September 2026")).toBeChecked();
    expect(screen.getByText("Recheck 5 songs")).toBeInTheDocument();

    fireEvent.click(checkbox("Select all"));
    expect(screen.queryByTestId("recheck-action-bar")).not.toBeInTheDocument();
  });

  it("opens a confirmation first and sends nothing until it is confirmed", () => {
    render();
    fireEvent.click(checkbox("Select October 2026"));

    fireEvent.click(screen.getByRole("button", { name: "Recheck 3 songs" }));

    expect(screen.getByText("Recheck 3 songs?")).toBeInTheDocument();
    expect(screen.getByText(/low priority/)).toBeInTheDocument();
    expect(screen.getByText(/active game are skipped/)).toBeInTheDocument();
    expect(hooks.mutateAsync).not.toHaveBeenCalled();
  });

  it("sends exactly the chosen ids with all false, toasts the result, and clears the selection", async () => {
    hooks.mutateAsync.mockResolvedValue({ affected: QUEUED_SONG_COUNT });
    render();
    fireEvent.click(checkbox("Select October 2026"));
    fireEvent.click(checkbox("Select all"));
    fireEvent.click(checkbox("Select all"));
    fireEvent.click(checkbox("Select Zi-mi"));
    fireEvent.click(checkbox("Select Dragostea Din Tei"));

    fireEvent.click(screen.getByRole("button", { name: "Recheck 2 songs" }));
    fireEvent.click(screen.getByRole("button", { name: "Confirm" }));

    await waitFor(() =>
      expect(toastMock.success).toHaveBeenCalledWith("Queued 3 songs for a recheck.", expect.anything()),
    );
    expect(hooks.mutateAsync).toHaveBeenCalledWith({ data: { songIds: [1, 3], all: false } });
    expect(screen.queryByTestId("recheck-action-bar")).not.toBeInTheDocument();
  });

  it("sends every selectable id when everything is selected", async () => {
    hooks.mutateAsync.mockResolvedValue({ affected: ALL_SELECTABLE_IDS.length });
    render();

    fireEvent.click(checkbox("Select all"));
    fireEvent.click(screen.getByRole("button", { name: "Recheck 5 songs" }));
    fireEvent.click(screen.getByRole("button", { name: "Confirm" }));

    await waitFor(() => expect(hooks.mutateAsync).toHaveBeenCalled());
    expect(hooks.mutateAsync).toHaveBeenCalledWith({ data: { songIds: ALL_SELECTABLE_IDS, all: false } });
  });

  it("keeps the selection when the request fails", async () => {
    hooks.mutateAsync.mockRejectedValue(new Error("boom"));
    render();
    fireEvent.click(checkbox("Select October 2026"));

    fireEvent.click(screen.getByRole("button", { name: "Recheck 3 songs" }));
    fireEvent.click(screen.getByRole("button", { name: "Confirm" }));

    await waitFor(() => expect(hooks.mutateAsync).toHaveBeenCalled());
    expect(toastMock.success).not.toHaveBeenCalled();
    expect(screen.getByTestId("recheck-action-bar")).toBeInTheDocument();
  });

  it("disables the bar buttons while the request is pending", () => {
    hooks.isPending = true;
    render();
    fireEvent.click(checkbox("Select October 2026"));

    expect(screen.getByRole("button", { name: "Recheck 3 songs" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Clear selection" })).toBeDisabled();
  });
});
