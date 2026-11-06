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
const exportPersonalDataMock = vi.fn();
const queryClientClear = vi.fn();
const queryClientInvalidateQueries = vi.fn();

const MOCK_USER = {
  id: 42,
  username: "melody_master",
  email: "melody@example.com",
};

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: routerPush }),
}));

vi.mock("sonner", () => ({
  toast: toastMocks,
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
    data: MOCK_USER,
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
    document.cookie = "session_hint=active; path=/";
    globalThis.URL.createObjectURL = vi.fn(() => "blob:export-url");
    globalThis.URL.revokeObjectURL = vi.fn();
  });

  it("initializes form inputs with current user credentials", () => {
    renderWithClient(<AccountSettingsPage />);

    const usernameInput = screen.getByLabelText("Username");
    const emailInput = screen.getByLabelText("Email address");

    expect(usernameInput).toHaveValue(MOCK_USER.username);
    expect(emailInput).toHaveValue(MOCK_USER.email);
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

    fireEvent.change(confirmationInput, { target: { value: MOCK_USER.username } });
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
    fireEvent.change(confirmationInput, { target: { value: MOCK_USER.username } });

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
    fireEvent.change(confirmationInput, { target: { value: MOCK_USER.username } });

    const confirmActionButton = screen.getByRole("button", { name: "Permanently delete account" });
    fireEvent.click(confirmActionButton);

    expect(screen.getByRole("alert")).toHaveTextContent("Cannot delete account while host of active match");
    expect(toastMocks.error).toHaveBeenCalledWith("Cannot delete account while host of active match");
    expect(routerPush).not.toHaveBeenCalled();
  });
});
