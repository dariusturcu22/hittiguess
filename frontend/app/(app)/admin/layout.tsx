"use client";

import React from "react";

import { useGetCurrentUser } from "@/hooks/generated/user-management/user-management";
import { UserDetailDTORole } from "@/hooks/models";

export default function AdminLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const { data: currentUser, isLoading } = useGetCurrentUser({
    query: { retry: false },
  });

  if (isLoading) {
    return (
      <div className="flex h-full flex-1 min-w-0 bg-dotted flex items-center justify-center">
        <span className="text-muted-foreground">Checking admin access...</span>
      </div>
    );
  }

  if (currentUser?.role !== UserDetailDTORole.ADMIN) {
    return (
      <div className="flex h-full flex-1 min-w-0 bg-dotted flex items-center justify-center px-14">
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
