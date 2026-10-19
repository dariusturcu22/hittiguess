import { describe, expect, it } from "vitest";

import { CreateGroupBody, JoinGroupBody } from "@/hooks/zod/group-management/group-management";

// The backend's validation patterns are copied verbatim into these generated schemas, so a
// pattern written in Java-only regex syntax fails the moment this module is imported.
describe("generated member identity schemas", () => {
  it("accept a plain display name and a Google profile image", () => {
    const identity = { displayName: "Sam", avatarUrl: "https://lh3.googleusercontent.com/a/profile" };

    expect(CreateGroupBody.safeParse(identity).success).toBe(true);
    expect(JoinGroupBody.safeParse({ joinCode: "ABCD", ...identity }).success).toBe(true);
  });

  it("refuse an avatar hosted anywhere else", () => {
    expect(JoinGroupBody.safeParse({ joinCode: "ABCD", avatarUrl: "https://example.com/tracker.png" }).success).toBe(false);
  });
});
