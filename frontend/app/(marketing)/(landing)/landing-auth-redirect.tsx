"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";

import { useGetCurrentUser } from "@/hooks/generated/user-management/user-management";

export function LandingAuthRedirect() {
  const router = useRouter();
  const currentUserQuery = useGetCurrentUser({
    query: { retry: false },
    request: { skipAuthRedirect: true },
  });

  useEffect(() => {
    if (currentUserQuery.data) {
      router.replace("/playlists");
    }
  }, [currentUserQuery.data, router]);

  return null;
}
