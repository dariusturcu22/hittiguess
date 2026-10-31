import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import AccountSettingsPage from "./page";

const routerPush = vi.fn();
const toastMocks = vi.hoisted(() => ({
  success: vi.fn(),
  error: vi.fn(),
}));

const updateUserMutate = vi.fn();
const deleteUserMutate = vi.fn();
const setupTwoFactorMutate = vi.fn();
const confirmTwoFactorMutate = vi.fn();
const disableTwoFactorMutate = vi.fn();
const exportPersonalDataMock = vi.fn();
const queryClientClear = vi.fn();
const queryClientInvalidateQueries = vi.fn();

let mockCurrentUser: {
  id: number;
  username: string;
  email: string;
  twoFactorEnabled?: boolean;
} = {
  id: 42,
  username: "melody_master",
  email: "melody@example.com",
  twoFactorEnabled: false,
};

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: routerPush }),
}));

vi.mock("sonner", () => ({
  toast: toastMocks,
}));

vi.mock("qrcode", () => ({
  default: {
    toDataURL: vi.fn(async () => "data:image/png;base64,mock-qr-code"),
  },
}));

vi.mock("@tanstack/react-query", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@tanstack/react-query")>();
  return {
    ...actual,
    useQueryClient: () => ({
      clear: queryClientClear,
      invalidateQueries: queryClientInvalidateQueries,
    }),
  };
});

vi.mock("@/hooks/generated/user-management/user-management", () => ({
  useGetCurrentUser: () => ({
    data: mockCurrentUser,
    isLoading: false,
  }),
  useUpdateUser: () => ({
    mutate: updateUserMutate,
    isPending: false,
  }),
  useDeleteUser: () => ({
    mutate: deleteUserMutate,
    isPending: false,
  }),
  exportPersonalData: () => exportPersonalDataMock(),
  getGetCurrentUserQueryKey: () => ["/api/users/me"],
}));

vi.mock("@/hooks/generated/authentication-management/authentication-management", () => ({
  useSetupTwoFactor: () => ({
    mutate: setupTwoFactorMutate,
    isPending: false,
  }),
  useConfirmTwoFactor: () => ({
    mutate: confirmTwoFactorMutate,
    isPending: false,
  }),
  useDisableTwoFactor: () => ({
    mutate: disableTwoFactorMutate,
    isPending: false,
  }),
}));

function renderWithClient(component: ReactNode) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      {component}
    </QueryClientProvider>,
  );
}

describe("AccountSettingsPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockCurrentUser = {
      id: 42,
      username: "melody_master",
      email: "melody@example.com",
      twoFactorEnabled: false,
    };
    document.cookie = "session_hint=active; path=/";
    globalThis.URL.createObjectURL = vi.fn(() => "blob:export-url");
    globalThis.URL.revokeObjectURL = vi.fn();
    Object.assign(navigator, {
      clipboard: {
        writeText: vi.fn(async () => undefined),
      },
    });
  });

  it("initializes form inputs with current user credentials", () => {
    renderWithClient(<AccountSettingsPage />);

    const usernameInput = screen.getByLabelText("Username");
    const emailInput = screen.getByLabelText("Email address");

    expect(usernameInput).toHaveValue(mockCurrentUser.username);
    expect(emailInput).toHaveValue(mockCurrentUser.email);
  });

  it("validates username length before submitting profile update", async () => {
    renderWithClient(<AccountSettingsPage />);

    const usernameInput = screen.getByLabelText("Username");
    fireEvent.change(usernameInput, { target: { value: "ab" } });

    const submitButton = screen.getByRole("button", { name: "Save changes" });
    fireEvent.click(submitButton);

    expect(updateUserMutate).not.toHaveBeenCalled();
    expect(screen.getByRole("alert")).toHaveTextContent("Username must be between 3 and 20 characters long.");
    expect(toastMocks.error).toHaveBeenCalledWith("Username must be between 3 and 20 characters long.");
  });

  it("validates email format before submitting profile update", async () => {
    renderWithClient(<AccountSettingsPage />);

    const emailInput = screen.getByLabelText("Email address");
    fireEvent.change(emailInput, { target: { value: "not-an-email" } });

    const submitButton = screen.getByRole("button", { name: "Save changes" });
    fireEvent.click(submitButton);

    expect(updateUserMutate).not.toHaveBeenCalled();
    expect(screen.getByRole("alert")).toHaveTextContent("Email must be a valid email address.");
    expect(toastMocks.error).toHaveBeenCalledWith("Email must be a valid email address.");
  });

  it("submits valid profile changes and handles success", async () => {
    updateUserMutate.mockImplementation((variables, callbacks) => {
      callbacks?.onSuccess?.();
    });

    renderWithClient(<AccountSettingsPage />);

    const usernameInput = screen.getByLabelText("Username");
    fireEvent.change(usernameInput, { target: { value: "new_username" } });

    const submitButton = screen.getByRole("button", { name: "Save changes" });
    fireEvent.click(submitButton);

    expect(updateUserMutate).toHaveBeenCalledWith(
      {
        data: {
          username: "new_username",
          email: "melody@example.com",
        },
      },
      expect.anything(),
    );

    expect(screen.getByRole("status")).toHaveTextContent("Profile updated successfully.");
    expect(toastMocks.success).toHaveBeenCalledWith("Profile updated successfully.");
    expect(queryClientInvalidateQueries).toHaveBeenCalledWith({ queryKey: ["/api/users/me"] });
  });

  it("displays server error message on failed profile update", async () => {
    updateUserMutate.mockImplementation((variables, callbacks) => {
      callbacks?.onError?.({
        response: {
          data: {
            message: "Username or email already in use",
          },
        },
      });
    });

    renderWithClient(<AccountSettingsPage />);

    const submitButton = screen.getByRole("button", { name: "Save changes" });
    fireEvent.click(submitButton);

    expect(screen.getByRole("alert")).toHaveTextContent("Username or email already in use");
    expect(toastMocks.error).toHaveBeenCalledWith("Username or email already in use");
  });

  it("starts two-factor setup and displays QR code and secret", async () => {
    setupTwoFactorMutate.mockImplementation((variables, callbacks) => {
      callbacks?.onSuccess?.({
        secret: "JBSWY3DPEHPK3PXP",
        provisioningUri: "otpauth://totp/hittiguess:melody_master?secret=JBSWY3DPEHPK3PXP&issuer=hittiguess",
      });
    });

    renderWithClient(<AccountSettingsPage />);

    const setupButton = screen.getByRole("button", { name: "Set up two-factor authentication" });
    await act(async () => {
      fireEvent.click(setupButton);
    });

    expect(setupTwoFactorMutate).toHaveBeenCalled();
    expect(screen.getByRole("heading", { name: "Set up authenticator" })).toBeVisible();
    expect(screen.getByAltText("Two-factor authentication QR code")).toBeVisible();
    expect(screen.getByText("JBSWY3DPEHPK3PXP")).toBeVisible();
    expect(screen.getByLabelText("Enter 6-digit verification code")).toBeVisible();
  });

  it("confirms two-factor setup and displays one-time backup codes", async () => {
    setupTwoFactorMutate.mockImplementation((variables, callbacks) => {
      callbacks?.onSuccess?.({
        secret: "JBSWY3DPEHPK3PXP",
        provisioningUri: "otpauth://totp/hittiguess:melody_master?secret=JBSWY3DPEHPK3PXP&issuer=hittiguess",
      });
    });

    confirmTwoFactorMutate.mockImplementation((variables, callbacks) => {
      callbacks?.onSuccess?.({
        backupCodes: ["CODE111111", "CODE222222", "CODE333333"],
      });
    });

    renderWithClient(<AccountSettingsPage />);

    await act(async () => {
      fireEvent.click(screen.getByRole("button", { name: "Set up two-factor authentication" }));
    });

    const codeInput = screen.getByLabelText("Enter 6-digit verification code");
    fireEvent.change(codeInput, { target: { value: "123456" } });

    await act(async () => {
      fireEvent.click(screen.getByRole("button", { name: "Confirm and enable" }));
    });

    expect(confirmTwoFactorMutate).toHaveBeenCalledWith(
      { data: { code: "123456" } },
      expect.anything(),
    );

    expect(screen.getByText("Two-factor authentication is now active")).toBeVisible();
    expect(screen.getByText("CODE111111")).toBeVisible();
    expect(screen.getByText("CODE222222")).toBeVisible();
    expect(screen.getByText("CODE333333")).toBeVisible();

    // Test copying backup codes
    fireEvent.click(screen.getByRole("button", { name: "Copy codes" }));
    expect(navigator.clipboard.writeText).toHaveBeenCalledWith("CODE111111\nCODE222222\nCODE333333");
    expect(toastMocks.success).toHaveBeenCalledWith("Backup codes copied to clipboard.");

    // Test downloading backup codes
    fireEvent.click(screen.getByRole("button", { name: "Download (.txt)" }));
    expect(URL.createObjectURL).toHaveBeenCalled();
    expect(toastMocks.success).toHaveBeenCalledWith("Backup codes downloaded.");

    // Dismiss backup codes view
    fireEvent.click(screen.getByRole("button", { name: "Done" }));
    expect(screen.queryByText("CODE111111")).not.toBeInTheDocument();
  });

  it("displays error when two-factor setup confirmation fails", async () => {
    setupTwoFactorMutate.mockImplementation((variables, callbacks) => {
      callbacks?.onSuccess?.({
        secret: "JBSWY3DPEHPK3PXP",
        provisioningUri: "otpauth://totp/hittiguess:melody_master?secret=JBSWY3DPEHPK3PXP&issuer=hittiguess",
      });
    });

    confirmTwoFactorMutate.mockImplementation((variables, callbacks) => {
      callbacks?.onError?.({
        response: { data: { message: "Invalid verification code" } },
      });
    });

    renderWithClient(<AccountSettingsPage />);

    await act(async () => {
      fireEvent.click(screen.getByRole("button", { name: "Set up two-factor authentication" }));
    });

    fireEvent.change(screen.getByLabelText("Enter 6-digit verification code"), { target: { value: "000000" } });

    await act(async () => {
      fireEvent.click(screen.getByRole("button", { name: "Confirm and enable" }));
    });

    expect(screen.getByRole("alert")).toHaveTextContent("Invalid verification code");
    expect(toastMocks.error).toHaveBeenCalledWith("Invalid verification code");
  });

  it("opens disable dialog and disables two-factor authentication with proof", async () => {
    mockCurrentUser.twoFactorEnabled = true;

    disableTwoFactorMutate.mockImplementation((variables, callbacks) => {
      callbacks?.onSuccess?.();
    });

    renderWithClient(<AccountSettingsPage />);

    expect(screen.getByText("Active")).toBeVisible();
    const disableButton = screen.getByRole("button", { name: "Disable two-factor authentication" });
    fireEvent.click(disableButton);

    expect(screen.getByRole("heading", { name: "Disable two-factor authentication" })).toBeVisible();

    const passwordInput = screen.getByLabelText(/Current password/i);
    fireEvent.change(passwordInput, { target: { value: "MySecretPassword1!" } });

    const confirmDisableButton = screen.getByRole("button", { name: "Confirm disable" });
    fireEvent.click(confirmDisableButton);

    expect(disableTwoFactorMutate).toHaveBeenCalledWith(
      {
        data: {
          currentPassword: "MySecretPassword1!",
          code: undefined,
        },
      },
      expect.anything(),
    );

    expect(toastMocks.success).toHaveBeenCalledWith("Two-factor authentication disabled.");
    expect(queryClientInvalidateQueries).toHaveBeenCalledWith({ queryKey: ["/api/users/me"] });
  });

  it("displays error inside dialog when disabling two-factor authentication fails", async () => {
    mockCurrentUser.twoFactorEnabled = true;

    disableTwoFactorMutate.mockImplementation((variables, callbacks) => {
      callbacks?.onError?.({
        response: {
          data: {
            message: "Current password or a valid authentication code is required",
          },
        },
      });
    });

    renderWithClient(<AccountSettingsPage />);

    fireEvent.click(screen.getByRole("button", { name: "Disable two-factor authentication" }));

    const codeInput = screen.getByLabelText(/Authentication code or backup code/i);
    fireEvent.change(codeInput, { target: { value: "999999" } });

    fireEvent.click(screen.getByRole("button", { name: "Confirm disable" }));

    expect(screen.getByRole("alert")).toHaveTextContent("Current password or a valid authentication code is required");
    expect(toastMocks.error).toHaveBeenCalledWith("Current password or a valid authentication code is required");
  });

  it("downloads personal data export on clicking export button", async () => {
    const sampleExport = {
      user: { id: 42, username: "melody_master" },
      playlists: [],
      history: [],
    };
    exportPersonalDataMock.mockResolvedValue(sampleExport);

    renderWithClient(<AccountSettingsPage />);

    const exportButton = screen.getByRole("button", { name: "Download personal data (.json)" });
    await act(async () => {
      fireEvent.click(exportButton);
    });

    expect(exportPersonalDataMock).toHaveBeenCalled();
    expect(URL.createObjectURL).toHaveBeenCalled();
    expect(URL.revokeObjectURL).toHaveBeenCalledWith("blob:export-url");
    expect(toastMocks.success).toHaveBeenCalledWith("Personal data export downloaded.");
  });

  it("displays error when personal data export fails", async () => {
    exportPersonalDataMock.mockRejectedValue(new Error("Network export error"));

    renderWithClient(<AccountSettingsPage />);

    const exportButton = screen.getByRole("button", { name: "Download personal data (.json)" });
    await act(async () => {
      fireEvent.click(exportButton);
    });

    expect(screen.getByRole("alert")).toHaveTextContent("Network export error");
    expect(toastMocks.error).toHaveBeenCalledWith("Network export error");
  });

  it("requires exact username typing to confirm account deletion", async () => {
    renderWithClient(<AccountSettingsPage />);

    const initialDeleteButton = screen.getByRole("button", { name: "Delete account" });
    fireEvent.click(initialDeleteButton);

    const confirmActionButton = screen.getByRole("button", { name: "Permanently delete account" });
    expect(confirmActionButton).toBeDisabled();

    const confirmationInput = screen.getByLabelText("Confirm username to delete account");
    fireEvent.change(confirmationInput, { target: { value: "wrong_username" } });
    expect(confirmActionButton).toBeDisabled();

    fireEvent.change(confirmationInput, { target: { value: mockCurrentUser.username } });
    expect(confirmActionButton).not.toBeDisabled();
  });

  it("executes account deletion, clears session, and redirects to landing page on confirm", async () => {
    deleteUserMutate.mockImplementation((variables, callbacks) => {
      callbacks?.onSuccess?.();
    });

    renderWithClient(<AccountSettingsPage />);

    const initialDeleteButton = screen.getByRole("button", { name: "Delete account" });
    fireEvent.click(initialDeleteButton);

    const confirmationInput = screen.getByLabelText("Confirm username to delete account");
    fireEvent.change(confirmationInput, { target: { value: mockCurrentUser.username } });

    const confirmActionButton = screen.getByRole("button", { name: "Permanently delete account" });
    fireEvent.click(confirmActionButton);

    expect(deleteUserMutate).toHaveBeenCalled();
    expect(queryClientClear).toHaveBeenCalled();
    expect(toastMocks.success).toHaveBeenCalledWith("Account deleted.");
    expect(routerPush).toHaveBeenCalledWith("/");
  });

  it("displays error inside dialog when account deletion fails", async () => {
    deleteUserMutate.mockImplementation((variables, callbacks) => {
      callbacks?.onError?.({
        response: {
          data: {
            message: "Cannot delete account while host of active match",
          },
        },
      });
    });

    renderWithClient(<AccountSettingsPage />);

    const initialDeleteButton = screen.getByRole("button", { name: "Delete account" });
    fireEvent.click(initialDeleteButton);

    const confirmationInput = screen.getByLabelText("Confirm username to delete account");
    fireEvent.change(confirmationInput, { target: { value: mockCurrentUser.username } });

    const confirmActionButton = screen.getByRole("button", { name: "Permanently delete account" });
    fireEvent.click(confirmActionButton);

    expect(screen.getByRole("alert")).toHaveTextContent("Cannot delete account while host of active match");
    expect(toastMocks.error).toHaveBeenCalledWith("Cannot delete account while host of active match");
    expect(routerPush).not.toHaveBeenCalled();
  });
});
