import { notFound } from "next/navigation";
import { StageQueuePage } from "./stage-queue-page";
import { STAGES } from "../pipeline-stages";

export default async function StagePage({
  params,
}: {
  params: Promise<{ stage: string }>;
}) {
  const { stage } = await params;
  if (!STAGES.some((item) => item.slug === stage)) notFound();
  return <StageQueuePage stage={stage} />;
}
