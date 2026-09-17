"use client";

import * as React from "react";
import { IconLogout, IconSettings } from "@tabler/icons-react";

import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuTrigger,
} from "@/components/shadcn/dropdown-menu";
import { useLogout } from "@/hooks/generated/authentication-management/authentication-management";
import { useRouter } from "next/navigation";
import { useQueryClient } from "@tanstack/react-query";
import { useGetCurrentUser } from "@/hooks/generated/user-management/user-management";

export function NavUser() {
  const router = useRouter();
  const queryClient = useQueryClient();

  const { mutate: logout } = useLogout({
    mutation: {
      onSuccess: () => {
        queryClient.clear();
        router.push("/login");
      },
    },
  });

  const { data: user } = useGetCurrentUser({
    query: {
      enabled:
        typeof window !== "undefined" &&
        window.location.pathname !== "/login" &&
        window.location.pathname !== "/register",
    },
  });

  function handleLogout() {
    logout();
  }

  const avatarInitial = user?.username?.trim().charAt(0).toUpperCase() ?? "";

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <button
          type="button"
          className="flex h-11 w-11 shrink-0 cursor-pointer items-center justify-center rounded-full border-2 border-sidebar-border bg-primary font-display text-base text-primary-foreground"
        >
          {avatarInitial}
        </button>
      </DropdownMenuTrigger>
      {/* Matches the floating profile card in AppShellDark.dc.html /
          Light.dc.html: avatar, name, an inert "View profile" pill, and a
          settings gear next to a working log-out icon, all in one row. The
          mockup's stats row (games/win rate/streak) has no backing endpoint
          yet and is left out rather than faked. The theme toggle lives only
          in app-sidebar.tsx's dedicated rail icon, not duplicated here. */}
      <DropdownMenuContent
        className="w-[290px] overflow-hidden rounded-[18px] border-[3px] border-border-strong bg-card p-0 shadow-lg"
        side="right"
        align="end"
        sideOffset={12}
      >
        <div className="flex items-start gap-3 bg-primary/20 p-4">
          <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-primary font-display text-base text-primary-foreground">
            {avatarInitial}
          </div>
          <div className="flex min-w-0 flex-1 flex-col gap-1.5 pt-0.5">
            <span className="truncate font-display text-base text-card-foreground">
              {user?.username}
            </span>
            <span
              className="w-fit rounded-full bg-border-strong px-2.5 py-1 font-display text-[8px] whitespace-nowrap text-primary"
              title="Profile pages haven't shipped yet"
            >
              View profile
            </span>
          </div>
          <div className="flex shrink-0 gap-1.5">
            <button
              type="button"
              title="Settings haven't shipped yet"
              className="flex size-[30px] items-center justify-center rounded-[9px] text-muted-foreground"
            >
              <IconSettings className="size-4" />
            </button>
            <button
              type="button"
              onClick={handleLogout}
              title="Log out"
              className="flex size-[30px] cursor-pointer items-center justify-center rounded-[9px] text-destructive"
            >
              <IconLogout className="size-4" />
            </button>
          </div>
        </div>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
