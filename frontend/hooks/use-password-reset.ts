"use client";

import { useMutation, type UseMutationOptions } from "@tanstack/react-query";

import { customInstance } from "@/lib/axios-instance";

// Landed backend fields the generated client predates. These mirror the orval
// output shapes (path params plus a data body) exactly so the next api:gen run
// replaces each module mechanically: the generated hooks then take these names.
export interface PasswordResetRequestBody {
  email: string;
}

export interface PasswordResetConfirmBody {
  token: string;
  newPassword: string;
}

export interface VerifyEmailBody {
  token: string;
}

export function useRequestPasswordReset(
  options?: UseMutationOptions<void, unknown, { data: PasswordResetRequestBody }>,
) {
  return useMutation({
    ...options,
    mutationFn: ({ data }: { data: PasswordResetRequestBody }) =>
      customInstance<void>({
        url: "/auth/password-reset/request",
        method: "POST",
        data,
      }),
  });
}

export function useConfirmPasswordReset(
  options?: UseMutationOptions<void, unknown, { data: PasswordResetConfirmBody }>,
) {
  return useMutation({
    ...options,
    mutationFn: ({ data }: { data: PasswordResetConfirmBody }) =>
      customInstance<void>({
        url: "/auth/password-reset/confirm",
        method: "POST",
        data,
      }),
  });
}

export function useVerifyEmail(
  options?: UseMutationOptions<void, unknown, { data: VerifyEmailBody }>,
) {
  return useMutation({
    ...options,
    mutationFn: ({ data }: { data: VerifyEmailBody }) =>
      customInstance<void>({
        url: "/auth/verify-email",
        method: "POST",
        data,
      }),
  });
}
