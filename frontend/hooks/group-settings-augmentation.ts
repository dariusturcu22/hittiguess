// The generated client predates the backend fixedDjMemberId fields on the group
// detail and the settings update. These merge the landed shapes in until the
// next api:gen run generates them.
declare module "@/hooks/models/groupDetailDTO" {
  interface GroupDetailDTO {
    fixedDjMemberId?: number;
  }
}

declare module "@/hooks/models/updateGroupSettingsRequest" {
  interface UpdateGroupSettingsRequest {
    fixedDjMemberId?: number;
  }
}

export {};
