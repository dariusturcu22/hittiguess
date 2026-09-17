"use client";

import { Headphones, Mic, PhoneOff } from "lucide-react";

import { useGetActiveMembership } from "@/hooks/generated/group-management/group-management";

export function AppVoiceSidebar() {
  const { data: activeGroup } = useGetActiveMembership({
    query: { retry: false },
  });
  const voiceMembers = (activeGroup?.members ?? []).filter(
    (member) => member.isInVoice,
  );

  if (voiceMembers.length === 0) {
    return null;
  }

  return (
    <aside className="flex w-[100px] shrink-0 flex-col items-center border-l-[3px] border-sidebar-border bg-sidebar/80 px-2 py-[22px]">
      <div className="flex flex-col items-center gap-6">
        {voiceMembers.map((member, memberIndex) => {
          const displayName = member.displayName || "Player";
          const colors = ["bg-success", "bg-info", "bg-primary", "bg-accent"];
          return (
            <div key={member.id} className="flex flex-col items-center gap-2">
              <div className={`flex size-[52px] items-center justify-center rounded-full font-display text-base text-primary-foreground ${colors[memberIndex % colors.length]}`}>
                {displayName.charAt(0).toUpperCase()}
              </div>
              <span className="max-w-[84px] truncate text-[10px] text-sidebar-foreground">
                {displayName}
              </span>
            </div>
          );
        })}
      </div>
      <div className="flex-1" />
      <div className="flex flex-col items-center gap-2.5">
        <button type="button" title="Mute" className="flex size-10 items-center justify-center rounded-xl bg-card text-card-foreground">
          <Mic className="size-[19px]" />
        </button>
        <button type="button" title="Deafen" className="flex size-10 items-center justify-center rounded-xl bg-card text-card-foreground">
          <Headphones className="size-[19px]" />
        </button>
        <div className="my-1 h-0.5 w-8 bg-sidebar-border" />
        <button type="button" title="Leave voice" className="flex size-10 items-center justify-center rounded-xl bg-destructive text-destructive-foreground">
          <PhoneOff className="size-[19px]" />
        </button>
      </div>
    </aside>
  );
}
