"use client";

import Link from "next/link";
import { Music2, Radio } from "lucide-react";
import { usePathname } from "next/navigation";

import { useGetActiveMembership } from "@/hooks/generated/group-management/group-management";
import { useGetActiveSessionForGroup } from "@/hooks/generated/game-session/game-session";

const LOCKED_GROUP_STATUS = "LOCKED";

export function ActiveSessionWidget() {
  const pathname = usePathname();
  const membershipQuery = useGetActiveMembership({ query: { retry: false } });
  const groupId = membershipQuery.data?.id ?? 0;
  const sessionQuery = useGetActiveSessionForGroup(groupId, { query: { enabled: membershipQuery.data?.status === LOCKED_GROUP_STATUS, retry: false } });
  const sessionId = sessionQuery.data?.id;

  if (!sessionId || pathname.startsWith(`/sessions/${sessionId}`)) return null;

  return <Link href={`/sessions/${sessionId}`} className="fixed bottom-5 right-5 z-40 flex items-center gap-3 rounded-[18px] border-[3px] border-border bg-card px-4 py-3 shadow-[5px_5px_0_rgba(0,0,0,0.3)] transition-transform hover:-translate-y-1"><span className="flex size-10 items-center justify-center rounded-xl bg-primary text-primary-foreground"><Music2 className="size-5" /></span><span><span className="block font-display text-xs text-card-foreground">Game in progress</span><span className="mt-0.5 flex items-center gap-1 text-[10px] text-muted-foreground"><Radio className="size-3 text-green" />Return to your timeline</span></span></Link>;
}
