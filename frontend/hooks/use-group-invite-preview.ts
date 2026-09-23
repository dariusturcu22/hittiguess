import { useQuery } from "@tanstack/react-query";

import { customInstance } from "@/lib/axios-instance";

export interface GroupInviteMemberPreview {
  displayName?: string;
  avatarUrl?: string;
  isAdmin?: boolean;
}

export interface GroupInvitePreview {
  memberCount?: number;
  members?: GroupInviteMemberPreview[];
}

export function useGroupInvitePreview(inviteCode: string) {
  return useQuery({
    queryKey: [`/api/groups/invites/${inviteCode}/preview`],
    queryFn: ({ signal }) =>
      customInstance<GroupInvitePreview>({
        url: `/api/groups/invites/${inviteCode}/preview`,
        method: "GET",
        signal,
      }),
    enabled: Boolean(inviteCode),
    retry: false,
  });
}
