"use client";

import * as React from "react";
import { useRouter } from "next/navigation";
import { useQueryClient } from "@tanstack/react-query";
import { IconDownload, IconTrash } from "@tabler/icons-react";
import { toast } from "sonner";

import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/shadcn/alert-dialog";
import {
  exportPersonalData,
  getGetCurrentUserQueryKey,
  useDeleteUser,
  useGetCurrentUser,
  useUpdateUser,
} from "@/hooks/generated/user-management/user-management";

const USERNAME_MINIMUM_LENGTH = 3;
const USERNAME_MAXIMUM_LENGTH = 20;
const ISO_DATE_SUBSTRING_LENGTH = 10;
const JSON_INDENTATION_SPACES = 2;
const BASIC_EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

function extractErrorMessage(error: unknown, fallbackMessage: string): string {
  if (typeof error === "object" && error !== null && "response" in error) {
    const errorResponse = (error as { response?: { data?: { message?: string; errors?: Array<{ defaultMessage?: string }> } } }).response;
    if (errorResponse?.data?.errors && errorResponse.data.errors.length > 0) {
      const fieldMessages = errorResponse.data.errors
        .map((entry) => entry.defaultMessage)
        .filter((message): message is string => Boolean(message));
      if (fieldMessages.length > 0) {
        return fieldMessages.join(". ");
      }
    }
    if (errorResponse?.data?.message) {
      return errorResponse.data.message;
    }
  }
  if (error instanceof Error && error.message) {
    return error.message;
  }
  return fallbackMessage;
}

export default function AccountSettingsPage() {
  const router = useRouter();
  const queryClient = useQueryClient();

  const { data: currentUser, isLoading: isUserLoading } = useGetCurrentUser();

  const [usernameInput, setUsernameInput] = React.useState("");
  const [emailInput, setEmailInput] = React.useState("");
  const [hasInitializedForm, setHasInitializedForm] = React.useState(false);

  const [profileErrorMessage, setProfileErrorMessage] = React.useState<string | null>(null);
  const [profileSuccessMessage, setProfileSuccessMessage] = React.useState<string | null>(null);

  const [isExporting, setIsExporting] = React.useState(false);
  const [exportErrorMessage, setExportErrorMessage] = React.useState<string | null>(null);

  const [isDeleteDialogOpen, setIsDeleteDialogOpen] = React.useState(false);
  const [deleteConfirmationInput, setDeleteConfirmationInput] = React.useState("");
  const [deleteErrorMessage, setDeleteErrorMessage] = React.useState<string | null>(null);

  React.useEffect(() => {
    if (currentUser && !hasInitializedForm) {
      setUsernameInput(currentUser.username ?? "");
      setEmailInput(currentUser.email ?? "");
      setHasInitializedForm(true);
    }
  }, [currentUser, hasInitializedForm]);

  const updateProfileMutation = useUpdateUser();
  const deleteUserMutation = useDeleteUser();

  const currentUsername = currentUser?.username ?? "";
  const isDeleteConfirmationMatching =
    Boolean(currentUsername) && deleteConfirmationInput.trim() === currentUsername.trim();

  function handleSaveProfile(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setProfileErrorMessage(null);
    setProfileSuccessMessage(null);

    const trimmedUsername = usernameInput.trim();
    const trimmedEmail = emailInput.trim();

    if (trimmedUsername.length < USERNAME_MINIMUM_LENGTH || trimmedUsername.length > USERNAME_MAXIMUM_LENGTH) {
      const lengthError = `Username must be between ${USERNAME_MINIMUM_LENGTH} and ${USERNAME_MAXIMUM_LENGTH} characters long.`;
      setProfileErrorMessage(lengthError);
      toast.error(lengthError);
      return;
    }

    if (!BASIC_EMAIL_PATTERN.test(trimmedEmail)) {
      const emailError = "Email must be a valid email address.";
      setProfileErrorMessage(emailError);
      toast.error(emailError);
      return;
    }

    updateProfileMutation.mutate(
      {
        data: {
          username: trimmedUsername,
          email: trimmedEmail,
        },
      },
      {
        onSuccess: () => {
          setProfileSuccessMessage("Profile updated successfully.");
          toast.success("Profile updated successfully.");
          void queryClient.invalidateQueries({ queryKey: getGetCurrentUserQueryKey() });
        },
        onError: (error) => {
          const message = extractErrorMessage(error, "Failed to update profile. Please try again.");
          setProfileErrorMessage(message);
          toast.error(message);
        },
      },
    );
  }

  async function handleExportPersonalData() {
    setExportErrorMessage(null);
    setIsExporting(true);

    try {
      const exportData = await exportPersonalData();
      const exportJsonString = JSON.stringify(exportData, null, JSON_INDENTATION_SPACES);
      const jsonBlob = new Blob([exportJsonString], { type: "application/json" });
      const objectUrl = URL.createObjectURL(jsonBlob);
      const currentDateStamp = new Date().toISOString().slice(0, ISO_DATE_SUBSTRING_LENGTH);
      const downloadAnchor = document.createElement("a");
      downloadAnchor.href = objectUrl;
      downloadAnchor.download = `hittiguess-data-export-${currentDateStamp}.json`;
      document.body.appendChild(downloadAnchor);
      downloadAnchor.click();
      document.body.removeChild(downloadAnchor);
      URL.revokeObjectURL(objectUrl);
      toast.success("Personal data export downloaded.");
    } catch (error) {
      const message = extractErrorMessage(error, "Failed to export personal data. Please try again.");
      setExportErrorMessage(message);
      toast.error(message);
    } finally {
      setIsExporting(false);
    }
  }

  function handleConfirmDeleteAccount() {
    setDeleteErrorMessage(null);

    deleteUserMutation.mutate(undefined, {
      onSuccess: () => {
        setIsDeleteDialogOpen(false);
        document.cookie = "session_hint=; path=/; max-age=0; SameSite=Lax";
        queryClient.clear();
        toast.success("Account deleted.");
        router.push("/");
      },
      onError: (error) => {
        const message = extractErrorMessage(error, "Failed to delete account. Please try again.");
        setDeleteErrorMessage(message);
        toast.error(message);
      },
    });
  }

  return (
    <div className="mx-auto flex max-w-[820px] flex-col gap-8 px-6 py-12 sm:px-14">
      <div>
        <h1 className="font-display text-[28px] text-foreground">Account settings</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          Manage your account credentials, export your personal data, or delete your account.
        </p>
      </div>

      {/* Section 1: Profile & Credentials */}
      <section
        className="rounded-[18px] border-[3px] border-border-strong bg-card p-7 shadow-md"
        aria-label="Account information"
      >
        <h2 className="font-display text-base text-foreground">Profile & credentials</h2>
        <p className="mt-1 mb-5 text-sm text-muted-foreground">
          Update your username and primary contact email.
        </p>

        {profileErrorMessage && (
          <div role="alert" className="mb-4 rounded-xl border border-destructive/40 bg-destructive/10 p-3 text-sm font-medium text-destructive">
            {profileErrorMessage}
          </div>
        )}

        {profileSuccessMessage && (
          <div role="status" className="mb-4 rounded-xl border border-primary/40 bg-primary/10 p-3 text-sm font-medium text-primary">
            {profileSuccessMessage}
          </div>
        )}

        <form onSubmit={handleSaveProfile} noValidate className="space-y-5">
          <div className="grid grid-cols-1 gap-5 sm:grid-cols-2">
            <div>
              <label htmlFor="username-input" className="mb-1.5 block text-xs font-bold uppercase tracking-wider text-muted-foreground">
                Username
              </label>
              <input
                id="username-input"
                name="username"
                type="text"
                disabled={isUserLoading || updateProfileMutation.isPending}
                value={usernameInput}
                onChange={(event) => setUsernameInput(event.target.value)}
                className="w-full rounded-xl border-2 border-border bg-background px-4 py-3 text-sm text-foreground outline-none transition-colors focus:border-primary"
                required
              />
            </div>
            <div>
              <label htmlFor="email-input" className="mb-1.5 block text-xs font-bold uppercase tracking-wider text-muted-foreground">
                Email address
              </label>
              <input
                id="email-input"
                name="email"
                type="email"
                disabled={isUserLoading || updateProfileMutation.isPending}
                value={emailInput}
                onChange={(event) => setEmailInput(event.target.value)}
                className="w-full rounded-xl border-2 border-border bg-background px-4 py-3 text-sm text-foreground outline-none transition-colors focus:border-primary"
                required
              />
            </div>
          </div>

          <button
            type="submit"
            disabled={isUserLoading || updateProfileMutation.isPending}
            className="inline-flex cursor-pointer items-center justify-center gap-2 rounded-full bg-primary px-6 py-2.5 font-display text-xs text-primary-foreground shadow-sm transition-opacity hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-50"
          >
            {updateProfileMutation.isPending ? "Saving changes..." : "Save changes"}
          </button>
        </form>
      </section>

      {/* Section 2: Personal Data Export */}
      <section
        className="rounded-[18px] border-[3px] border-border-strong bg-card p-7 shadow-md"
        aria-label="Personal data export"
      >
        <h2 className="font-display text-base text-foreground">Personal data export</h2>
        <p className="mt-1 mb-5 text-sm text-muted-foreground">
          Under GDPR and privacy regulations, you can download a full export of your personal information, created playlists, song submissions, and gameplay history as a structured JSON file.
        </p>

        {exportErrorMessage && (
          <div role="alert" className="mb-4 rounded-xl border border-destructive/40 bg-destructive/10 p-3 text-sm font-medium text-destructive">
            {exportErrorMessage}
          </div>
        )}

        <button
          type="button"
          onClick={handleExportPersonalData}
          disabled={isExporting}
          className="inline-flex cursor-pointer items-center justify-center gap-2 rounded-full border-2 border-border-strong bg-secondary px-5 py-2.5 font-display text-xs text-secondary-foreground transition-colors hover:bg-secondary/80 disabled:cursor-not-allowed disabled:opacity-50"
        >
          <IconDownload className="size-4" />
          {isExporting ? "Exporting data..." : "Download personal data (.json)"}
        </button>
      </section>

      {/* Section 3: Danger Zone */}
      <section
        className="rounded-[18px] border-[3px] border-destructive bg-card p-7 shadow-md"
        aria-label="Delete account"
      >
        <h2 className="font-display text-base text-destructive">Delete account</h2>
        <p className="mt-1 mb-5 text-sm text-muted-foreground">
          Permanently delete your Hittiguess account. All playlists you own, song contributions, and game progress will be removed immediately. This action cannot be reversed.
        </p>

        <button
          type="button"
          onClick={() => {
            setDeleteErrorMessage(null);
            setDeleteConfirmationInput("");
            setIsDeleteDialogOpen(true);
          }}
          className="inline-flex cursor-pointer items-center justify-center gap-2 rounded-full bg-destructive px-6 py-2.5 font-display text-xs text-destructive-foreground shadow-sm transition-opacity hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-50"
        >
          <IconTrash className="size-4" />
          Delete account
        </button>
      </section>

      {/* Delete Confirmation Alert Dialog */}
      <AlertDialog open={isDeleteDialogOpen} onOpenChange={setIsDeleteDialogOpen}>
        <AlertDialogContent className="max-w-[480px] rounded-[18px] border-[3px] border-border-strong bg-card p-6 shadow-xl">
          <AlertDialogHeader>
            <AlertDialogTitle className="font-display text-lg text-destructive">
              Delete account
            </AlertDialogTitle>
            <AlertDialogDescription className="text-sm text-muted-foreground">
              This action is permanent and cannot be reversed. All your data will be permanently wiped.
            </AlertDialogDescription>
          </AlertDialogHeader>

          <div className="my-4 space-y-3">
            <p className="text-xs font-semibold text-foreground">
              To confirm deletion, please type your username{" "}
              <span className="font-mono font-bold text-destructive">{currentUsername}</span> below:
            </p>
            <input
              id="confirm-username-input"
              aria-label="Confirm username to delete account"
              type="text"
              value={deleteConfirmationInput}
              onChange={(event) => setDeleteConfirmationInput(event.target.value)}
              placeholder={currentUsername}
              className="w-full rounded-xl border-2 border-border bg-background px-4 py-2.5 text-sm text-foreground outline-none focus:border-destructive"
              autoFocus
            />

            {deleteErrorMessage && (
              <div role="alert" className="rounded-xl border border-destructive/40 bg-destructive/10 p-2.5 text-xs font-medium text-destructive">
                {deleteErrorMessage}
              </div>
            )}
          </div>

          <AlertDialogFooter className="flex flex-row justify-end gap-2">
            <AlertDialogCancel
              disabled={deleteUserMutation.isPending}
              onClick={() => setIsDeleteDialogOpen(false)}
              className="rounded-full border-2 border-border-strong px-4 py-2 font-display text-xs"
            >
              Cancel
            </AlertDialogCancel>
            <AlertDialogAction
              disabled={!isDeleteConfirmationMatching || deleteUserMutation.isPending}
              onClick={(event) => {
                event.preventDefault();
                handleConfirmDeleteAccount();
              }}
              className="rounded-full bg-destructive px-5 py-2 font-display text-xs text-destructive-foreground hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-50"
            >
              {deleteUserMutation.isPending ? "Deleting..." : "Permanently delete account"}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}
