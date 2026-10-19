import type { SourceFindingDTO } from "@/hooks/models/sourceFindingDTO";
import { stageLabel } from "../backfill-queue/pipeline-ui";

const SOURCE_STAGES = ["MUSICBRAINZ", "DISCOGS", "WIKIDATA", "WIKIPEDIA"];
function describe(source: SourceFindingDTO): string {
  if (source.failureReason && source.status !== "DONE")
    return source.failureReason;
  if (source.status === "SKIPPED") return "This source was not needed.";
  if (source.status !== "DONE")
    return source.status?.replaceAll("_", " ") ?? "Not queued";
  const candidates = source.result?.candidates;
  if (!Array.isArray(candidates) || candidates.length === 0)
    return "No matching candidates";
  const [candidate] = candidates;
  if (typeof candidate === "object" && candidate != null) {
    const fields = Object.entries(candidate)
      .filter(
        ([key, value]) =>
          [
            "title",
            "name",
            "date",
            "year",
            "first_release_date",
            "release_date",
          ].includes(key) &&
          (typeof value === "string" || typeof value === "number"),
      )
      .map(([, value]) => String(value));
    if (fields.length) return fields.join(", ");
  }
  return `${candidates.length} candidate${candidates.length === 1 ? "" : "s"} found`;
}
export function SourceFindings({
  sources,
  reason,
  releaseYear,
}: {
  sources?: SourceFindingDTO[];
  reason?: string;
  releaseYear?: number;
}) {
  return (
    <>
      {sources && (
        <>
          <h3 className="mb-1 font-display text-xs">What each source found</h3>
          <div className="mb-4">
            {SOURCE_STAGES.map((stage) => {
              const source = sources.find((item) => item.stage === stage);
              const year = source?.result?.earliestYear;
              return (
                <div
                  key={stage}
                  className="flex items-center gap-3 border-b-2 border-border py-[9px]"
                >
                  <span className="w-24 shrink-0 text-xs font-bold">
                    {stageLabel(stage)}
                  </span>
                  <span className={`w-11 shrink-0 font-display text-sm ${typeof year === "number" && releaseYear != null && year !== releaseYear ? "text-peach" : "text-green"}`}>
                    {typeof year === "number" ? year : ""}
                  </span>
                  <span className="min-w-0 flex-1 break-words text-[11px] text-muted-foreground">
                    {source ? describe(source) : "Not queued"}
                  </span>
                  <span className="text-[11px] text-icon-muted">
                    {source?.status === "DONE" && typeof year === "number"
                      ? "found"
                      : source?.status === "DONE"
                        ? "no year"
                        : source?.status?.toLowerCase().replaceAll("_", " ")}
                  </span>
                </div>
              );
            })}
          </div>
        </>
      )}
      {reason && (
        <>
          <h3 className="mb-2 font-display text-xs">Why it needs review</h3>
          <p className="mb-4 rounded-[12px] bg-background px-3.5 py-3 text-xs leading-[1.65] dark:bg-card">
            {reason}
          </p>
        </>
      )}
    </>
  );
}
