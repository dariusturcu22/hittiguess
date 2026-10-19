import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi, beforeEach } from "vitest";

import ReportQueuePage from "./page";

const resolveMutate = vi.fn();
const dismissMutate = vi.fn();
const refetchQueue = vi.fn();
const toastMocks = vi.hoisted(() => ({ success: vi.fn(), error: vi.fn() }));
let reviewQueueLoading = false;

vi.mock("sonner", () => ({
  toast: toastMocks,
}));

const QUEUE_ITEM = {
  songId: 1,
  songTitle: "A Song",
  artistName: "An Artist",
  releaseYear: 2001,
  verificationStatus: "NEEDS_REVIEW",
  priorityTier: "REPORTED_NO_CONVERGENCE",
  openReportCount: 2,
  openReports: [],
};

vi.mock("@/hooks/generated/admin-song-report-review/admin-song-report-review", () => ({
  useReviewQueue: () => ({
    data: [QUEUE_ITEM],
    isLoading: reviewQueueLoading,
    isError: false,
    refetch: refetchQueue,
  }),
  useResolve: () => ({ mutate: resolveMutate, isPending: false }),
  useDismiss: () => ({ mutate: dismissMutate, isPending: false }),
}));

describe("ReportQueuePage", () => {
  beforeEach(() => {
    reviewQueueLoading = false;
    resolveMutate.mockReset();
    dismissMutate.mockReset();
    refetchQueue.mockReset();
    toastMocks.success.mockReset();
    toastMocks.error.mockReset();
  });

  it("shows a skeleton while the queue is loading", () => {
    reviewQueueLoading = true;
    render(<ReportQueuePage />);

    expect(screen.getByTestId("report-queue-skeleton")).toBeInTheDocument();
  });

  it("toasts on a successful resolve", async () => {
    resolveMutate.mockImplementation((_args, options) => options?.onSuccess?.());
    render(<ReportQueuePage />);

    fireEvent.click(screen.getByRole("button", { name: "Resolve" }));

    await waitFor(() => expect(toastMocks.success).toHaveBeenCalledWith("Report resolved"));
  });

  it("toasts an error when resolve fails", async () => {
    resolveMutate.mockImplementation((_args, options) => options?.onError?.());
    render(<ReportQueuePage />);

    fireEvent.click(screen.getByRole("button", { name: "Resolve" }));

    await waitFor(() =>
      expect(toastMocks.error).toHaveBeenCalledWith("Couldn't resolve that report. Try again."),
    );
  });

  it("toasts on a successful dismiss", async () => {
    dismissMutate.mockImplementation((_args, options) => options?.onSuccess?.());
    render(<ReportQueuePage />);

    fireEvent.click(screen.getByRole("button", { name: "Dismiss report" }));

    await waitFor(() => expect(toastMocks.success).toHaveBeenCalledWith("Report dismissed"));
  });
});
