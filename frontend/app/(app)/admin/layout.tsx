"use client";

import React from "react";
import type { AxiosError } from "axios";

import { useBacklogStatus } from "@/hooks/generated/admin-catalog-seeding/admin-catalog-seeding";

const FORBIDDEN_STATUS = 403;

// The current-user DTO (/api/users/me) does not expose the account role, so the
// frontend cannot read ADMIN directly. The admin endpoints are role-guarded
// server-side, so this layout uses the backlog-status endpoint as an access
// probe: a 403 means the account is not an admin and the admin section renders
// an access-denied state instead of its pages. The backend guard is the real
// boundary; this only mirrors its verdict in the UI. The clean replacement is
// to expose role on /api/users/me and gate on that instead of probing.
export default function AdminLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const { isLoading, isError, error } = useBacklogStatus({
    query: { retry: false },
  });

  const forbidden =
    isError && (error as AxiosError | undefined)?.response?.status === FORBIDDEN_STATUS;

  if (isLoading) {
    return (
      <div className="flex-1 min-w-0 bg-dotted flex items-center justify-center">
        <span className="text-muted-foreground">Checking admin access...</span>
      </div>
    );
  }

  if (forbidden) {
    return (
      <div className="flex-1 min-w-0 bg-dotted flex items-center justify-center px-14">
        <div className="max-w-[420px] text-center">
          <h1 className="font-display text-2xl text-accent mb-3 [text-shadow:3px_3px_0_var(--text-shadow-on-page)]">
            Admins only
          </h1>
          <p className="text-[13px] text-muted-foreground leading-[1.6]">
            This area is restricted to admin accounts. If you believe you should
            have access, ask an administrator to grant your account the admin
            role.
          </p>
        </div>
      </div>
    );
  }

  return <>{children}</>;
}
