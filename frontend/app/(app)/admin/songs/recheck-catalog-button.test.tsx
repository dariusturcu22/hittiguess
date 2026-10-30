import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { RecheckCatalogButton } from "./recheck-catalog-button";

const QUEUED_SONG_COUNT = 42;
const CATALOG_SONG_COUNT = 60;

const recheckHook = vi.hoisted(() => ({
  mutateAsync: vi.fn(),
  isPending: false,
}));

const toastMock = vi.hoisted(() => ({ success: vi.fn(), error: vi.fn() }));

vi.mock("sonner", () => ({ toast: toastMock }));

vi.mock("@/hooks/generated/admin-song-pipeline/admin-song-pipeline", () => ({
  useRecheckSongs: () => ({
    mutateAsync: recheckHook.mutateAsync,
    isPending: recheckHook.isPending,
  }),
}));

function renderButton() {
  return render(
    <QueryClientProvider client={new QueryClient()}>
      <RecheckCatalogButton songCount={CATALOG_SONG_COUNT} />
    </QueryClientProvider>,
  );
}

describe("RecheckCatalogButton", () => {
  beforeEach(() => {
    recheckHook.mutateAsync.mockReset();
    recheckHook.isPending = false;
    toastMock.success.mockReset();
    toastMock.error.mockReset();
  });

  it("opens a confirmation that explains the recheck without sending anything", () => {
    renderButton();

    fireEvent.click(screen.getByRole("button", { name: "Recheck whole catalog" }));

    expect(screen.getByText("Recheck the whole catalog?")).toBeInTheDocument();
    expect(screen.getByText(/Up to 60 songs/)).toBeInTheDocument();
    expect(screen.getByText(/low priority/)).toBeInTheDocument();
    expect(screen.getByText(/active game are skipped/)).toBeInTheDocument();
    expect(recheckHook.mutateAsync).not.toHaveBeenCalled();
  });

  it("sends all: true on confirm and toasts how many songs were queued", async () => {
    recheckHook.mutateAsync.mockResolvedValue({ affected: QUEUED_SONG_COUNT });
    renderButton();

    fireEvent.click(screen.getByRole("button", { name: "Recheck whole catalog" }));
    fireEvent.click(screen.getByRole("button", { name: "Confirm" }));

    await waitFor(() =>
      expect(toastMock.success).toHaveBeenCalledWith(
        "Queued 42 songs for a recheck.",
      ),
    );
    expect(recheckHook.mutateAsync).toHaveBeenCalledWith({ data: { all: true } });
  });

  it("disables the button while the request is pending", () => {
    recheckHook.isPending = true;
    renderButton();

    expect(
      screen.getByRole("button", { name: "Recheck whole catalog" }),
    ).toBeDisabled();
  });
});
