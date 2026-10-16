import React from "react";
import {
  act,
  fireEvent,
  render as renderComponent,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import BackfillQueuePage, { RunList } from "./page";
import { StageQueuePage } from "./[stage]/stage-queue-page";
import {
  BACKFILL_QUEUE_REFRESH_INTERVAL_MILLISECONDS,
  RunActions,
  STAGES,
  RejectedKeyBanners,
} from "./pipeline-ui";
import { SongPipelineActions } from "../songs/song-pipeline-actions";
import { SourceFindings } from "../songs/source-findings";
import type { AdminSongDetailDTO } from "@/hooks/models/adminSongDetailDTO";

const state = vi.hoisted(() => ({
  queues: {} as Record<string, unknown>,
  runs: {} as Record<string, unknown>,
  jobs: {} as Record<string, unknown>,
  batches: {} as Record<string, unknown>,
  params: {} as Record<string, unknown>,
  options: {} as Record<string, unknown>,
  calls: vi.fn(),
  refetch: vi.fn(),
  fail: false,
  success: vi.fn(),
  error: vi.fn(),
}));
vi.mock("sonner", () => ({
  toast: { success: state.success, error: state.error },
}));
function mutation(
  name: string,
  options?: {
    mutation?: { onSuccess?: () => Promise<void>; onError?: () => void };
  },
) {
  const mutateAsync = async (variables?: unknown) => {
    state.calls(name, variables);
    if (state.fail) {
      options?.mutation?.onError?.();
      throw new Error("Failure");
    }
    await options?.mutation?.onSuccess?.();
    return { enqueuedYoutubeIds: ["dQw4w9WgXcQ"] };
  };
  return {
    isPending: false,
    mutateAsync,
    mutate: (
      variables?: unknown,
      callbacks?: { onSuccess?: (result: unknown) => void },
    ) => {
      void mutateAsync(variables)
        .then(callbacks?.onSuccess)
        .catch(() => {});
    },
  };
}
vi.mock("@/hooks/generated/admin-pipeline/admin-pipeline", () => ({
  useQueues: (options: unknown) => {
    state.options.queues = options;
    return { ...state.queues, refetch: state.refetch };
  },
  useRuns: (params: unknown, options: unknown) => {
    state.params.runs = params;
    state.options.runs = options;
    return { ...state.runs, refetch: state.refetch };
  },
  useJobs: (stage: string, params: unknown, options: unknown) => {
    state.params.jobs = { stage, ...(params as object) };
    state.options.jobs = options;
    return { ...state.jobs, refetch: state.refetch };
  },
  useBatches: (_params: unknown, options: unknown) => {
    state.options.batches = options;
    return { ...state.batches, refetch: state.refetch };
  },
  usePauseStage: (options: never) => mutation("pauseStage", options),
  useResumeStage: (options: never) => mutation("resumeStage", options),
  usePausePipeline: (options: never) => mutation("pauseAll", options),
  useResumePipeline: (options: never) => mutation("resumeAll", options),
  useBumpRun: (options: never) => mutation("bump", options),
  useRetryRun: (options: never) => mutation("retry", options),
  useDiscardRun: (options: never) => mutation("discard", options),
  useCancelBatch: (options: never) => mutation("cancel", options),
  useEnqueuePipeline: (options: never) => mutation("enqueue", options),
  useRunPipelineNow: (options: never) => mutation("runNow", options),
  useRetryFailedRuns: (options: never) => mutation("retryAll", options),
}));
vi.mock("@/hooks/generated/admin-song-pipeline/admin-song-pipeline", () => ({
  useRerunSongPipeline: (options: never) => mutation("rerun", options),
  useUpdateCatalogSong: (options: never) => mutation("update", options),
  useDeleteCatalogSong: (options: never) => mutation("delete", options),
}));
function render(component: React.ReactNode) {
  const client = new QueryClient();
  const invalidate = vi.spyOn(client, "invalidateQueries");
  return {
    ...renderComponent(
      <QueryClientProvider client={client}>{component}</QueryClientProvider>,
    ),
    invalidate,
  };
}
const RUN_ID = 7;
const SONG_ID = 12;
const detail = {
  songId: SONG_ID,
  youtubeId: "dQw4w9WgXcQ",
  verificationStatus: "NEEDS_REVIEW",
  releaseYear: 1999,
} as AdminSongDetailDTO;
beforeEach(() => {
  vi.clearAllMocks();
  state.fail = false;
  state.params = {};
  state.options = {};
  state.queues = {
    data: {
      stagesEnabled: true,
      stages: STAGES.map((stage) => ({
        stage: stage.id,
        state: "RUNNING",
        waiting: 2,
        doneToday: 3,
        failed: 1,
      })),
    },
  };
  state.runs = {
    data: {
      items: [
        {
          id: RUN_ID,
          title: "Test song",
          youtubeId: "dQw4w9WgXcQ",
          artists: ["Artist"],
          batchLabel: "Test batch",
          status: "FAILED",
          jobs: [
            { stage: "DISCOGS", status: "FAILED", lastError: "Unavailable" },
          ],
        },
      ],
      totalElements: 30,
      totalPages: 2,
      counts: { WAITING: 20, FAILED: 10 },
    },
  };
  state.jobs = {
    data: {
      items: [
        {
          id: 2,
          runId: RUN_ID,
          title: "Test song",
          status: "WAITING",
          retrying: true,
          attempts: 1,
          maxAttempts: 5,
          lastError: "Timeout",
          lane: "USER_IMPORT",
        },
      ],
      totalElements: 1,
      totalPages: 1,
    },
  };
  state.batches = {
    data: {
      items: [{ id: 4, label: "Test batch", total: 5, done: 2, waiting: 3 }],
      totalElements: 1,
      totalPages: 1,
    },
  };
});
async function confirm() {
  fireEvent.click(screen.getByRole("button", { name: "Confirm" }));
  await waitFor(() =>
    expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument(),
  );
}

describe("backfill pages", () => {
  it("renders all seven cards and pauses a stage and the pipeline", async () => {
    const { invalidate } = render(<BackfillQueuePage />);
    expect(screen.getAllByRole("link", { name: "Open" })).toHaveLength(
      STAGES.length,
    );
    fireEvent.click(screen.getByRole("button", { name: "Pause MusicBrainz" }));
    fireEvent.click(screen.getByRole("button", { name: "Pause all" }));
    await waitFor(() =>
      expect(state.calls).toHaveBeenCalledWith("pauseStage", {
        stage: "MUSICBRAINZ",
      }),
    );
    expect(state.calls).toHaveBeenCalledWith("pauseAll", undefined);
    expect(invalidate).toHaveBeenCalled();
  });
  it("resumes paused queues", async () => {
    state.queues = {
      data: { stages: [{ stage: "MUSICBRAINZ", state: "PAUSED" }] },
    };
    render(<BackfillQueuePage />);
    fireEvent.click(screen.getByRole("button", { name: "Resume MusicBrainz" }));
    fireEvent.click(screen.getByRole("button", { name: "Resume all" }));
    await waitFor(() =>
      expect(state.calls).toHaveBeenCalledWith("resumeAll", undefined),
    );
    expect(state.calls).toHaveBeenCalledWith("resumeStage", {
      stage: "MUSICBRAINZ",
    });
  });
  it("enqueues a playlist or IDs and validates run-now input", async () => {
    render(<BackfillQueuePage />);
    const input = screen.getByLabelText("Playlist link or YouTube IDs");
    fireEvent.change(input, {
      target: { value: "https://www.youtube.com/playlist?list=PLtest" },
    });
    const form = within(document.getElementById("add-to-backlog")!);
    fireEvent.click(form.getByRole("button", { name: "Add to backlog" }));
    await waitFor(() =>
      expect(state.calls).toHaveBeenCalledWith("enqueue", {
        data: { playlistLink: "https://www.youtube.com/playlist?list=PLtest" },
      }),
    );
    await waitFor(() => expect(input).toHaveValue(""));
    fireEvent.change(input, { target: { value: "dQw4w9WgXcQ anotherId12" } });
    fireEvent.click(screen.getByRole("button", { name: "Run one now" }));
    expect(state.error).toHaveBeenCalledWith(
      expect.stringContaining("one video"),
    );
    fireEvent.change(input, { target: { value: "dQw4w9WgXcQ" } });
    fireEvent.click(screen.getByRole("button", { name: "Run one now" }));
    await waitFor(() =>
      expect(state.calls).toHaveBeenCalledWith("runNow", {
        data: { youtubeId: "dQw4w9WgXcQ" },
      }),
    );
  });
  it("requires confirmation before cancelling a batch", async () => {
    render(<BackfillQueuePage />);
    fireEvent.click(screen.getByRole("button", { name: "Cancel batch" }));
    expect(state.calls).not.toHaveBeenCalled();
    await confirm();
    expect(state.calls).toHaveBeenCalledWith("cancel", { id: 4 });
  });
  it("shows run filters with global counts, resets paging, and opens the Songs tab", () => {
    render(<BackfillQueuePage />);
    fireEvent.click(screen.getByRole("tab", { name: "Songs" }));
    expect(screen.getByText("Test song")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "All 30" })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Next" }));
    expect(state.params.runs).toMatchObject({ page: 1 });
    fireEvent.click(screen.getByRole("button", { name: "Failed 10" }));
    expect(state.params.runs).toMatchObject({ page: 0, state: "FAILED" });
    expect(screen.getByLabelText("Discogs: FAILED")).toBeInTheDocument();
  });
  it("bumps and retries a run, confirms discard, and reports a failed action", async () => {
    render(<RunActions id={RUN_ID} status="ACTIVE" retrying />);
    fireEvent.click(screen.getByRole("button", { name: "Bump" }));
    fireEvent.click(screen.getByRole("button", { name: "Retry" }));
    await waitFor(() =>
      expect(state.calls).toHaveBeenCalledWith("retry", { id: RUN_ID }),
    );
    expect(state.calls).toHaveBeenCalledWith("bump", { id: RUN_ID });
  });
  it("keeps discard confirmation open on an error", async () => {
    state.fail = true;
    render(<RunActions id={RUN_ID} status="FAILED" />);
    fireEvent.click(screen.getByRole("button", { name: "Discard" }));
    fireEvent.click(screen.getByRole("button", { name: "Confirm" }));
    await waitFor(() => expect(state.error).toHaveBeenCalled());
    expect(screen.getByRole("alertdialog")).toBeInTheDocument();
  });
  it("shows stage details and scopes retry-all to the stage", async () => {
    render(<StageQueuePage stage="musicbrainz" />);
    expect(screen.getByText("User import")).toBeInTheDocument();
    expect(screen.getByText("Retry 1 of 5")).toBeInTheDocument();
    expect(
      screen.getByText("Collecting one sample per minute"),
    ).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Retrying" }));
    expect(state.params.jobs).toMatchObject({
      status: "RETRYING",
      stage: "musicbrainz",
    });
    fireEvent.click(screen.getByRole("button", { name: "Retry all failed" }));
    await confirm();
    expect(state.calls).toHaveBeenCalledWith("retryAll", {
      params: { stage: "musicbrainz" },
    });
  });
  it("resumes a rejected-key stage from the banner", async () => {
    render(
      <RejectedKeyBanners
        stages={[
          {
            stage: "DISCOGS",
            state: "PAUSED",
            pausedReason: "API key rejected: DISCOGS",
          },
        ]}
      />,
    );
    expect(screen.getByRole("alert")).toHaveTextContent("DISCOGS");
    fireEvent.click(screen.getByRole("button", { name: "Resume Discogs" }));
    await waitFor(() =>
      expect(state.calls).toHaveBeenCalledWith("resumeStage", {
        stage: "DISCOGS",
      }),
    );
  });
  it.each(["queues", "runs", "jobs"] as const)(
    "shows loading, empty, and retryable errors for %s",
    (key) => {
      state[key] = { isLoading: true };
      const component = () =>
        key === "queues" ? (
          <BackfillQueuePage />
        ) : key === "runs" ? (
          <RunList />
        ) : (
          <StageQueuePage stage="musicbrainz" />
        );
      const view = render(component());
      expect(screen.getByRole("status")).toHaveTextContent("Loading");
      state[key] = { isError: true };
      view.rerender(
        <QueryClientProvider client={new QueryClient()}>
          {component()}
        </QueryClientProvider>,
      );
      fireEvent.click(screen.getByRole("button", { name: "Retry" }));
      expect(state.refetch).toHaveBeenCalled();
      state[key] = { data: { items: [], stages: [] } };
      view.rerender(
        <QueryClientProvider client={new QueryClient()}>
          {component()}
        </QueryClientProvider>,
      );
      if (key !== "queues")
        expect(screen.getByText("No items in this view.")).toBeInTheDocument();
    },
  );
});

describe("song detail controls", () => {
  it("renders source findings and review reasons from the response", () => {
    render(
      <SourceFindings
        sources={[
          {
            stage: "MUSICBRAINZ",
            status: "DONE",
            result: { earliestYear: 1999, candidates: [{ title: "Release" }] },
          },
        ]}
        reason="Sources disagree"
      />,
    );
    expect(screen.getByText("1999")).toBeInTheDocument();
    expect(screen.getByText("Release")).toBeInTheDocument();
    expect(screen.getByText("Sources disagree")).toBeInTheDocument();
  });
  it.each(["Re-run pipeline", "Set year", "Set status", "Delete"])(
    "confirms %s and submits its data",
    async (label) => {
      const onDelete = vi.fn();
      render(<SongPipelineActions detail={detail} onDelete={onDelete} />);
      fireEvent.click(screen.getByRole("button", { name: label }));
      expect(state.calls).not.toHaveBeenCalled();
      if (label === "Set year")
        fireEvent.change(screen.getByLabelText("Release year"), {
          target: { value: "2001" },
        });
      if (label === "Set status")
        fireEvent.change(screen.getByLabelText("Verification status"), {
          target: { value: "VERIFIED" },
        });
      await confirm();
      if (label === "Set year")
        expect(state.calls).toHaveBeenCalledWith("update", {
          id: SONG_ID,
          data: { releaseYear: 2001 },
        });
      if (label === "Set status")
        expect(state.calls).toHaveBeenCalledWith("update", {
          id: SONG_ID,
          data: { verificationStatus: "VERIFIED" },
        });
      if (label === "Delete") expect(onDelete).toHaveBeenCalled();
      if (label === "Re-run pipeline")
        expect(state.calls).toHaveBeenCalledWith("rerun", { id: SONG_ID });
    },
  );
  it("rejects an invalid release year and keeps the edit open", async () => {
    render(<SongPipelineActions detail={detail} onDelete={vi.fn()} />);
    fireEvent.click(screen.getByRole("button", { name: "Set year" }));
    fireEvent.change(screen.getByLabelText("Release year"), {
      target: { value: "999" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Confirm" }));
    await waitFor(() =>
      expect(state.error).toHaveBeenCalledWith("Enter a valid release year."),
    );
    expect(state.calls).not.toHaveBeenCalled();
  });
});

describe("queue safeguards", () => {
  it("disables enqueue controls while the stage flag is off", () => {
    state.queues = { data: { stagesEnabled: false, stages: [] } };
    render(<BackfillQueuePage />);
    fireEvent.change(screen.getByLabelText("Playlist link or YouTube IDs"), {
      target: { value: "dQw4w9WgXcQ" },
    });
    expect(screen.getByRole("button", { name: "Run one now" })).toBeDisabled();
    expect(
      within(document.getElementById("add-to-backlog")!).getByRole("button", {
        name: "Add to backlog",
      }),
    ).toBeDisabled();
  });
  it("supports arrow-key tab navigation", () => {
    render(<BackfillQueuePage />);
    screen.getByRole("tab", { name: "Overview" }).focus();
    fireEvent.keyDown(screen.getByRole("tablist"), { key: "ArrowRight" });
    expect(screen.getByRole("tab", { name: "Songs" })).toHaveAttribute(
      "aria-selected",
      "true",
    );
    expect(screen.getByRole("tab", { name: "Songs" })).toHaveFocus();
  });
  it("discards only after confirmation and can cancel the dialog", async () => {
    render(<RunActions id={RUN_ID} status="FAILED" />);
    fireEvent.click(screen.getByRole("button", { name: "Discard" }));
    fireEvent.click(screen.getByRole("button", { name: "Cancel" }));
    expect(state.calls).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole("button", { name: "Discard" }));
    await confirm();
    expect(state.calls).toHaveBeenCalledWith("discard", { id: RUN_ID });
  });
  it("retains form input when enqueue fails and shows the error toast", async () => {
    state.fail = true;
    render(<BackfillQueuePage />);
    const input = screen.getByLabelText("Playlist link or YouTube IDs");
    fireEvent.change(input, { target: { value: "dQw4w9WgXcQ" } });
    fireEvent.click(
      within(document.getElementById("add-to-backlog")!).getByRole("button", {
        name: "Add to backlog",
      }),
    );
    await waitFor(() => expect(state.error).toHaveBeenCalled());
    expect(input).toHaveValue("dQw4w9WgXcQ");
  });
  it("explains the embedding queue's missing history without metadata controls", () => {
    render(<StageQueuePage stage="embedding" />);
    expect(screen.getByText(/retains waiting work only/)).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Retry all failed" }),
    ).not.toBeInTheDocument();
  });
});

it("collects actual response samples during the visit", () => {
  vi.useFakeTimers();
  try {
    state.queues = {
      data: {
        stages: [
          { stage: "MUSICBRAINZ", state: "RUNNING", averageResponseMs: 350 },
        ],
      },
    };
    render(<StageQueuePage stage="musicbrainz" />);
    expect(screen.getByRole("img")).toHaveAttribute(
      "aria-label",
      "0 response-time samples collected, one per minute",
    );
    const SAMPLE_INTERVAL_MS = 60_000;
    act(() => vi.advanceTimersByTime(SAMPLE_INTERVAL_MS));
    expect(screen.getByRole("img")).toHaveAttribute(
      "aria-label",
      "1 response-time samples collected, one per minute",
    );
  } finally {
    vi.useRealTimers();
  }
});

type InvalidateFilters = {
  predicate?: (query: { queryKey: readonly unknown[] }) => boolean;
};
function expectAdminQueriesInvalidated(invalidate: {
  mock: { calls: unknown[][] };
}) {
  const [filters] = (invalidate.mock.calls.at(-1) ?? []) as [
    InvalidateFilters?,
  ];
  expect(filters?.predicate?.({ queryKey: ["/api/admin/queues"] })).toBe(true);
  expect(
    filters?.predicate?.({
      queryKey: ["/api/admin/queues/MUSICBRAINZ/jobs", { page: 0 }],
    }),
  ).toBe(true);
  expect(filters?.predicate?.({ queryKey: ["/api/admin/dashboard"] })).toBe(
    true,
  );
  expect(filters?.predicate?.({ queryKey: ["/api/playlists"] })).toBe(false);
}

describe("live queue data", () => {
  it.each(["queues", "batches"] as const)(
    "polls %s on the overview every five seconds and refetches on focus",
    (key) => {
      render(<BackfillQueuePage />);
      expect(state.options[key]).toMatchObject({
        query: {
          refetchInterval: BACKFILL_QUEUE_REFRESH_INTERVAL_MILLISECONDS,
          refetchIntervalInBackground: false,
          refetchOnWindowFocus: true,
        },
      });
      expect(BACKFILL_QUEUE_REFRESH_INTERVAL_MILLISECONDS).toBe(5_000);
    },
  );
  it("polls the songs tab and the stage page every five seconds and keeps previous data", () => {
    render(<RunList />);
    render(<StageQueuePage stage="musicbrainz" />);
    for (const key of ["runs", "jobs", "queues"]) {
      const options = state.options[key] as {
        query: { refetchInterval: number; placeholderData: unknown };
      };
      expect(options.query.refetchInterval).toBe(
        BACKFILL_QUEUE_REFRESH_INTERVAL_MILLISECONDS,
      );
      expect(typeof options.query.placeholderData).toBe("function");
    }
  });
  it("invalidates admin queries after pausing and resuming a stage and the pipeline", async () => {
    const paused = render(<BackfillQueuePage />);
    for (const name of ["Pause MusicBrainz", "Pause all"]) {
      paused.invalidate.mockClear();
      fireEvent.click(screen.getByRole("button", { name }));
      await waitFor(() => expect(paused.invalidate).toHaveBeenCalled());
      expectAdminQueriesInvalidated(paused.invalidate);
    }
    paused.unmount();
    state.queues = {
      data: { stages: [{ stage: "MUSICBRAINZ", state: "PAUSED" }] },
    };
    const resumed = render(<BackfillQueuePage />);
    for (const name of ["Resume MusicBrainz", "Resume all"]) {
      resumed.invalidate.mockClear();
      fireEvent.click(screen.getByRole("button", { name }));
      await waitFor(() => expect(resumed.invalidate).toHaveBeenCalled());
      expectAdminQueriesInvalidated(resumed.invalidate);
    }
  });
  it("invalidates admin queries after enqueue, run now, and batch cancel", async () => {
    const { invalidate } = render(<BackfillQueuePage />);
    const input = screen.getByLabelText("Playlist link or YouTube IDs");
    fireEvent.change(input, { target: { value: "dQw4w9WgXcQ" } });
    fireEvent.click(
      within(document.getElementById("add-to-backlog")!).getByRole("button", {
        name: "Add to backlog",
      }),
    );
    await waitFor(() => expect(invalidate).toHaveBeenCalled());
    expectAdminQueriesInvalidated(invalidate);
    invalidate.mockClear();
    fireEvent.change(input, { target: { value: "dQw4w9WgXcQ" } });
    fireEvent.click(screen.getByRole("button", { name: "Run one now" }));
    await waitFor(() => expect(invalidate).toHaveBeenCalled());
    expectAdminQueriesInvalidated(invalidate);
    invalidate.mockClear();
    fireEvent.click(screen.getByRole("button", { name: "Cancel batch" }));
    await confirm();
    expect(state.calls).toHaveBeenCalledWith("cancel", { id: 4 });
    expectAdminQueriesInvalidated(invalidate);
  });
  it("invalidates admin queries after bump, retry, discard, and retry all failed", async () => {
    const active = render(<RunActions id={RUN_ID} status="ACTIVE" retrying />);
    for (const name of ["Bump", "Retry"]) {
      active.invalidate.mockClear();
      fireEvent.click(screen.getByRole("button", { name }));
      await waitFor(() => expect(active.invalidate).toHaveBeenCalled());
      expectAdminQueriesInvalidated(active.invalidate);
    }
    active.unmount();
    const failed = render(<RunActions id={RUN_ID} status="FAILED" />);
    fireEvent.click(screen.getByRole("button", { name: "Discard" }));
    await confirm();
    expectAdminQueriesInvalidated(failed.invalidate);
    failed.unmount();
    const stagePage = render(<StageQueuePage stage="musicbrainz" />);
    fireEvent.click(screen.getByRole("button", { name: "Retry all failed" }));
    await confirm();
    expectAdminQueriesInvalidated(stagePage.invalidate);
  });
});

describe("retry display", () => {
  it("states how often a finished job was retried and hides its old failure", () => {
    state.runs = {
      data: {
        items: [
          {
            id: RUN_ID,
            title: "Saved song",
            status: "DONE",
            jobs: [
              {
                stage: "DISCOGS",
                status: "DONE",
                attempts: 1,
                lastError: "Timeout",
              },
            ],
          },
        ],
        totalElements: 1,
        totalPages: 1,
        counts: {},
      },
    };
    render(<RunList />);
    expect(
      screen.getByText("Metadata saved. Discogs retried once."),
    ).toBeInTheDocument();
    expect(screen.queryByText("Timeout")).not.toBeInTheDocument();
  });
  it("shows the stage job's retry progress and hides failures on done jobs", () => {
    state.jobs = {
      data: {
        items: [
          {
            id: 1,
            runId: 1,
            title: "Done song",
            status: "DONE",
            attempts: 2,
            lastError: "Old error",
          },
          {
            id: 2,
            runId: 2,
            title: "Waiting song",
            status: "WAITING",
            retrying: true,
            attempts: 2,
            lastError: "Timeout",
          },
        ],
        totalElements: 2,
        totalPages: 1,
      },
    };
    render(<StageQueuePage stage="musicbrainz" />);
    expect(screen.getByText("Retried 2 times")).toBeInTheDocument();
    expect(screen.queryByText("Old error")).not.toBeInTheDocument();
    expect(screen.getByText("Retry 2 of 6")).toBeInTheDocument();
    expect(screen.getByText("Timeout")).toBeInTheDocument();
  });
});
