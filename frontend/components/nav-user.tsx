"use client";

import * as React from "react";
import { IconLogout, IconMoon, IconSun } from "@tabler/icons-react";
import { useTheme } from "next-themes";

import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/shadcn/dropdown-menu";
import { useLogout } from "@/hooks/generated/authentication-management/authentication-management";
import { useRouter } from "next/navigation";
import { useQueryClient } from "@tanstack/react-query";
import { useGetCurrentUser } from "@/hooks/generated/user-management/user-management";

export function NavUser() {
  const [mounted, setMounted] = React.useState(false);
  const { resolvedTheme, setTheme } = useTheme();

  React.useEffect(() => {
    setMounted(true);
  }, []);

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
      {/* Visual treatment (card background, border, radius, shadow) matches
          the floating profile card in AppShellDark.dc.html / Light.dc.html.
          "View profile" and "settings" entries from that mockup aren't real
          routes yet, so the menu keeps its existing items: theme toggle and
          log out. */}
      <DropdownMenuContent
        className="w-[290px] overflow-hidden rounded-[18px] border-[3px] border-border-strong bg-card p-0 shadow-lg"
        side="right"
        align="end"
        sideOffset={12}
      >
        <DropdownMenuLabel className="rounded-none bg-primary/20 p-4 font-normal">
          <div className="flex items-center gap-3 text-left text-sm">
            <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-primary font-display text-base text-primary-foreground">
              {avatarInitial}
            </div>
            <div className="grid flex-1 text-left leading-tight">
              <span className="truncate font-display text-base text-card-foreground">
                {user?.username}
              </span>
              <span className="text-muted-foreground truncate text-xs">
                {user?.email}
              </span>
            </div>
          </div>
        </DropdownMenuLabel>
        <DropdownMenuSeparator />
        <DropdownMenuGroup className="p-1">
          <DropdownMenuItem
            className="cursor-pointer"
            onClick={() => setTheme(resolvedTheme === "dark" ? "light" : "dark")}
          >
            {mounted ? (
              <>
                {resolvedTheme === "dark" ? <IconSun /> : <IconMoon />}
                <span>
                  {resolvedTheme === "dark" ? "Light Mode" : "Dark Mode"}
                </span>
              </>
            ) : (
              <>
                <div className="size-4 animate-pulse rounded-full bg-muted" />
                <span>Loading...</span>
              </>
            )}
          </DropdownMenuItem>
        </DropdownMenuGroup>
        <DropdownMenuSeparator />
        <DropdownMenuGroup className="p-1">
          <DropdownMenuItem onClick={handleLogout} className="cursor-pointer">
            <IconLogout />
            Log out
          </DropdownMenuItem>
        </DropdownMenuGroup>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
