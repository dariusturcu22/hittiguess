import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { NavUser } from "./nav-user";

const uploadAvatarMutate = vi.fn();

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn() }),
}));

vi.mock("@tanstack/react-query", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@tanstack/react-query")>();
  return {
    ...actual,
    useQueryClient: () => ({ clear: vi.fn(), invalidateQueries: vi.fn() }),
  };
});

vi.mock("@/hooks/generated/authentication-management/authentication-management", () => ({
  useLogout: () => ({ mutate: vi.fn() }),
}));

vi.mock("@/hooks/generated/user-management/user-management", () => ({
  getGetCurrentUserQueryKey: () => ["current-user"],
  useGetCurrentUser: () => ({ data: { id: 11, username: "alex" } }),
}));

vi.mock("@/hooks/generated/pixel-art-images/pixel-art-images", () => ({
  useUploadOwnAvatar: () => ({ mutate: uploadAvatarMutate, isPending: false }),
}));

vi.mock("@/lib/pixelate", () => ({
  pixelizeImage: vi.fn(async () => new Blob(["pixels"], { type: "image/png" })),
}));

describe("NavUser avatar upload", () => {
  beforeEach(() => {
    uploadAvatarMutate.mockReset();
    vi.stubGlobal("URL", {
      createObjectURL: vi.fn(() => "blob:preview"),
      revokeObjectURL: vi.fn(),
    });
  });

  function openMenu() {
    const trigger = screen.getByRole("button", { name: "A" });
    fireEvent.pointerDown(trigger);
    fireEvent.mouseDown(trigger);
    fireEvent.mouseUp(trigger);
    fireEvent.click(trigger);
  }

  it("uploads the pixelized file as the profile picture", async () => {
    render(<NavUser />);

    openMenu();
    fireEvent.change(document.querySelector('input[type="file"]')!, {
      target: { files: [new File(["source"], "avatar.jpg", { type: "image/jpeg" })] },
    });

    await waitFor(() =>
      expect(uploadAvatarMutate).toHaveBeenCalledWith(
        { data: { avatar: expect.any(Blob) } },
        expect.anything(),
      ),
    );
  });

  it("shows the account initial while no avatar image loads", () => {
    render(<NavUser />);

    expect(screen.getByRole("button", { name: "A" })).toBeVisible();
  });
});
