"use client";

import * as React from "react";
import { useRouter } from "next/navigation";
import { useQueryClient } from "@tanstack/react-query";
import {
  IconCopy,
  IconDownload,
  IconShieldCheck,
  IconShieldLock,
  IconTrash,
} from "@tabler/icons-react";
import QRCode from "qrcode";
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
  useConfirmTwoFactor,
  useDisableTwoFactor,
  useSetupTwoFactor,
} from "@/hooks/generated/authentication-management/authentication-management";
import {
  exportPersonalData,
  getGetCurrentUserQueryKey,
  useDeleteUser,
  useGetCurrentUser,
  useUpdateUser,
} from "@/hooks/generated/user-management/user-management";
import type { TwoFactorSetupResponse } from "@/hooks/models/twoFactorSetupResponse";

const USERNAME_MINIMUM_LENGTH = 3;
const USERNAME_MAXIMUM_LENGTH = 20;
const ISO_DATE_SUBSTRING_LENGTH = 10;
const JSON_INDENTATION_SPACES = 2;
const QR_CODE_WIDTH = 192;
const QR_CODE_MARGIN = 1;
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

  // Two-Factor Authentication state
  const isTwoFactorEnabled = Boolean(currentUser?.twoFactorEnabled);
  const [isSettingUp2fa, setIsSettingUp2fa] = React.useState(false);
  const [setupData, setSetupData] = React.useState<TwoFactorSetupResponse | null>(null);
  const [qrCodeDataUrl, setQrCodeDataUrl] = React.useState<string | null>(null);
  const [totpConfirmCode, setTotpConfirmCode] = React.useState("");
  const [setupErrorMessage, setSetupErrorMessage] = React.useState<string | null>(null);
  const [backupCodes, setBackupCodes] = React.useState<string[] | null>(null);

  const [isDisableDialogOpen, setIsDisableDialogOpen] = React.useState(false);
  const [disablePasswordInput, setDisablePasswordInput] = React.useState("");
  const [disableCodeInput, setDisableCodeInput] = React.useState("");
  const [disableErrorMessage, setDisableErrorMessage] = React.useState<string | null>(null);

  React.useEffect(() => {
    if (currentUser && !hasInitializedForm) {
      setUsernameInput(currentUser.username ?? "");
      setEmailInput(currentUser.email ?? "");
      setHasInitializedForm(true);
    }
  }, [currentUser, hasInitializedForm]);

  const updateProfileMutation = useUpdateUser();
  const deleteUserMutation = useDeleteUser();
  const setup2faMutation = useSetupTwoFactor();
  const confirm2faMutation = useConfirmTwoFactor();
  const disable2faMutation = useDisableTwoFactor();

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

  function handleStart2faSetup() {
    setSetupErrorMessage(null);
    setup2faMutation.mutate(undefined, {
      onSuccess: async (response) => {
        setSetupData(response);
        if (response.provisioningUri) {
          try {
            const dataUrl = await QRCode.toDataURL(response.provisioningUri, {
              margin: QR_CODE_MARGIN,
              width: QR_CODE_WIDTH,
            });
            setQrCodeDataUrl(dataUrl);
          } catch {
            setQrCodeDataUrl(null);
          }
        } else {
          setQrCodeDataUrl(null);
        }
        setIsSettingUp2fa(true);
      },
      onError: (error) => {
        const message = extractErrorMessage(error, "Failed to begin two-factor setup.");
        toast.error(message);
      },
    });
  }

  function handleConfirm2faSetup(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSetupErrorMessage(null);

    const trimmedCode = totpConfirmCode.trim();
    if (!trimmedCode) {
      setSetupErrorMessage("Enter the verification code from your authenticator app.");
      return;
    }

    confirm2faMutation.mutate(
      {
        data: {
          code: trimmedCode,
        },
      },
      {
        onSuccess: (response) => {
          setIsSettingUp2fa(false);
          setSetupData(null);
          setQrCodeDataUrl(null);
          setTotpConfirmCode("");
          setBackupCodes(response.backupCodes ?? []);
          void queryClient.invalidateQueries({ queryKey: getGetCurrentUserQueryKey() });
          toast.success("Two-factor authentication enabled.");
        },
        onError: (error) => {
          const message = extractErrorMessage(error, "Invalid verification code. Please try again.");
          setSetupErrorMessage(message);
          toast.error(message);
        },
      },
    );
  }

  function handleCopyBackupCodes() {
    if (!backupCodes || backupCodes.length === 0) return;
    void navigator.clipboard.writeText(backupCodes.join("\n"));
    toast.success("Backup codes copied to clipboard.");
  }

  function handleDownloadBackupCodes() {
    if (!backupCodes || backupCodes.length === 0) return;
    const content = `Hittiguess Two-Factor Backup Codes\nGenerated: ${new Date().toISOString()}\n\nEach code can be used once if you lose access to your authenticator app:\n\n${backupCodes.join("\n")}\n`;
    const blob = new Blob([content], { type: "text/plain" });
    const objectUrl = URL.createObjectURL(blob);
    const anchor = document.createElement("a");
    anchor.href = objectUrl;
    anchor.download = "hittiguess-backup-codes.txt";
    document.body.appendChild(anchor);
    anchor.click();
    document.body.removeChild(anchor);
    URL.revokeObjectURL(objectUrl);
    toast.success("Backup codes downloaded.");
  }

  function handleConfirmDisable2fa(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setDisableErrorMessage(null);

    const trimmedPassword = disablePasswordInput.trim();
    const trimmedCode = disableCodeInput.trim();

    if (!trimmedPassword && !trimmedCode) {
      const message = "Enter your current password or an authentication code to confirm.";
      setDisableErrorMessage(message);
      return;
    }

    disable2faMutation.mutate(
      {
        data: {
          currentPassword: trimmedPassword || undefined,
          code: trimmedCode || undefined,
        },
      },
      {
        onSuccess: () => {
          setIsDisableDialogOpen(false);
          setDisablePasswordInput("");
          setDisableCodeInput("");
          void queryClient.invalidateQueries({ queryKey: getGetCurrentUserQueryKey() });
          toast.success("Two-factor authentication disabled.");
        },
        onError: (error) => {
          const message = extractErrorMessage(error, "Failed to disable two-factor authentication.");
          setDisableErrorMessage(message);
          toast.error(message);
        },
      },
    );
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

      {/* Section 2: Two-Factor Authentication */}
      <section
        className="rounded-[18px] border-[3px] border-border-strong bg-card p-7 shadow-md"
        aria-label="Two-factor authentication"
      >
        <div className="flex items-center justify-between gap-4">
          <div>
            <h2 className="font-display text-base text-foreground">Two-factor authentication</h2>
            <p className="mt-1 text-sm text-muted-foreground">
              Add an extra layer of security using an authenticator app (such as Google Authenticator, 1Password, or Authy).
            </p>
          </div>
          <span
            className={`rounded-full px-3 py-1 font-display text-[10px] uppercase tracking-wider ${
              isTwoFactorEnabled
                ? "border border-primary/40 bg-primary/20 text-primary"
                : "border border-border bg-muted text-muted-foreground"
            }`}
          >
            {isTwoFactorEnabled ? "Active" : "Disabled"}
          </span>
        </div>

        {/* When backup codes were just generated after setup confirmation */}
        {backupCodes && (
          <div className="mt-6 space-y-4 rounded-xl border-2 border-primary/40 bg-primary/10 p-5">
            <div className="flex items-center gap-2 font-display text-sm text-primary">
              <IconShieldCheck className="size-5 shrink-0" />
              <span>Two-factor authentication is now active</span>
            </div>
            <p className="text-xs leading-relaxed text-muted-foreground">
              Save these one-time backup codes in a safe place. If you lose access to your authenticator app, each code can be used once to log in. They will not be shown again.
            </p>
            <div className="grid grid-cols-2 gap-2 sm:grid-cols-5">
              {backupCodes.map((code) => (
                <div
                  key={code}
                  className="rounded-lg border border-border bg-background px-2.5 py-2 text-center font-mono text-xs font-bold text-foreground"
                >
                  {code}
                </div>
              ))}
            </div>
            <div className="flex flex-wrap gap-2 pt-2">
              <button
                type="button"
                onClick={handleCopyBackupCodes}
                className="inline-flex cursor-pointer items-center gap-1.5 rounded-full border-2 border-border-strong bg-secondary px-4 py-2 font-display text-xs text-secondary-foreground hover:bg-secondary/80"
              >
                <IconCopy className="size-3.5" />
                Copy codes
              </button>
              <button
                type="button"
                onClick={handleDownloadBackupCodes}
                className="inline-flex cursor-pointer items-center gap-1.5 rounded-full border-2 border-border-strong bg-secondary px-4 py-2 font-display text-xs text-secondary-foreground hover:bg-secondary/80"
              >
                <IconDownload className="size-3.5" />
                Download (.txt)
              </button>
              <button
                type="button"
                onClick={() => setBackupCodes(null)}
                className="ml-auto inline-flex cursor-pointer items-center gap-1.5 rounded-full bg-primary px-5 py-2 font-display text-xs text-primary-foreground hover:opacity-90"
              >
                Done
              </button>
            </div>
          </div>
        )}

        {/* When user is actively setting up 2FA */}
        {!backupCodes && isSettingUp2fa && setupData && (
          <div className="mt-6 space-y-5 rounded-xl border-2 border-border-strong bg-background/50 p-5">
            <h3 className="font-display text-sm text-foreground">Set up authenticator</h3>

            {setupErrorMessage && (
              <div role="alert" className="rounded-xl border border-destructive/40 bg-destructive/10 p-3 text-xs font-medium text-destructive">
                {setupErrorMessage}
              </div>
            )}

            <div className="flex flex-col gap-6 sm:flex-row sm:items-center">
              {qrCodeDataUrl ? (
                // eslint-disable-next-line @next/next/no-img-element
                <img
                  src={qrCodeDataUrl}
                  alt="Two-factor authentication QR code"
                  className="size-44 shrink-0 rounded-xl border-2 border-border bg-white p-2"
                />
              ) : (
                <div className="flex size-44 items-center justify-center rounded-xl border-2 border-border bg-background text-xs text-muted-foreground">
                  Loading QR...
                </div>
              )}
              <div className="space-y-3">
                <p className="text-xs leading-relaxed text-muted-foreground">
                  Scan this QR code with your authenticator app (such as Google Authenticator, Authy, or 1Password).
                </p>
                <div>
                  <span className="mb-1 block text-[11px] font-bold uppercase tracking-wider text-muted-foreground">
                    Or enter this secret key manually:
                  </span>
                  <div className="flex items-center gap-2">
                    <code className="rounded-lg border border-border bg-background px-3 py-1.5 font-mono text-xs font-bold text-primary select-all">
                      {setupData.secret}
                    </code>
                    <button
                      type="button"
                      onClick={() => {
                        void navigator.clipboard.writeText(setupData.secret ?? "");
                        toast.success("Secret copied to clipboard.");
                      }}
                      title="Copy secret"
                      className="flex size-7 items-center justify-center rounded-md text-muted-foreground hover:text-foreground"
                    >
                      <IconCopy className="size-4" />
                    </button>
                  </div>
                </div>
              </div>
            </div>

            <form onSubmit={handleConfirm2faSetup} className="space-y-4 pt-2">
              <div>
                <label htmlFor="totp-confirm-code-input" className="mb-1.5 block text-xs font-bold uppercase tracking-wider text-muted-foreground">
                  Enter 6-digit verification code
                </label>
                <input
                  id="totp-confirm-code-input"
                  name="code"
                  type="text"
                  autoComplete="one-time-code"
                  placeholder="123456"
                  maxLength={6}
                  value={totpConfirmCode}
                  onChange={(event) => setTotpConfirmCode(event.target.value)}
                  className="w-full max-w-[240px] rounded-xl border-2 border-border bg-background px-4 py-2.5 font-mono text-base tracking-widest text-foreground outline-none focus:border-primary"
                  required
                />
              </div>

              <div className="flex items-center gap-3">
                <button
                  type="submit"
                  disabled={confirm2faMutation.isPending || !totpConfirmCode.trim()}
                  className="inline-flex cursor-pointer items-center justify-center gap-2 rounded-full bg-primary px-6 py-2.5 font-display text-xs text-primary-foreground shadow-sm hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-50"
                >
                  {confirm2faMutation.isPending ? "Verifying..." : "Confirm and enable"}
                </button>
                <button
                  type="button"
                  onClick={() => {
                    setIsSettingUp2fa(false);
                    setSetupData(null);
                    setQrCodeDataUrl(null);
                    setTotpConfirmCode("");
                    setSetupErrorMessage(null);
                  }}
                  className="inline-flex cursor-pointer items-center justify-center rounded-full border-2 border-border-strong px-5 py-2.5 font-display text-xs text-muted-foreground hover:text-foreground"
                >
                  Cancel
                </button>
              </div>
            </form>
          </div>
        )}

        {/* When not setting up and not showing new backup codes */}
        {!backupCodes && !isSettingUp2fa && (
          <div className="mt-5">
            {isTwoFactorEnabled ? (
              <button
                type="button"
                onClick={() => {
                  setDisableErrorMessage(null);
                  setDisablePasswordInput("");
                  setDisableCodeInput("");
                  setIsDisableDialogOpen(true);
                }}
                className="inline-flex cursor-pointer items-center justify-center gap-2 rounded-full border-2 border-border-strong bg-secondary px-5 py-2.5 font-display text-xs text-secondary-foreground hover:bg-secondary/80"
              >
                <IconShieldLock className="size-4" />
                Disable two-factor authentication
              </button>
            ) : (
              <button
                type="button"
                onClick={handleStart2faSetup}
                disabled={setup2faMutation.isPending}
                className="inline-flex cursor-pointer items-center justify-center gap-2 rounded-full bg-primary px-6 py-2.5 font-display text-xs text-primary-foreground shadow-sm hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-50"
              >
                <IconShieldLock className="size-4" />
                {setup2faMutation.isPending ? "Starting setup..." : "Set up two-factor authentication"}
              </button>
            )}
          </div>
        )}

        {/* Disable Two-Factor Dialog */}
        <AlertDialog open={isDisableDialogOpen} onOpenChange={setIsDisableDialogOpen}>
          <AlertDialogContent className="max-w-[480px] rounded-[18px] border-[3px] border-border-strong bg-card p-6 shadow-xl">
            <AlertDialogHeader>
              <AlertDialogTitle className="font-display text-lg text-foreground">
                Disable two-factor authentication
              </AlertDialogTitle>
              <AlertDialogDescription className="text-sm text-muted-foreground">
                To turn off two-factor authentication, provide your current password or an authentication code from your authenticator app.
              </AlertDialogDescription>
            </AlertDialogHeader>

            <form onSubmit={handleConfirmDisable2fa} className="my-4 space-y-4">
              {disableErrorMessage && (
                <div role="alert" className="rounded-xl border border-destructive/40 bg-destructive/10 p-2.5 text-xs font-medium text-destructive">
                  {disableErrorMessage}
                </div>
              )}

              <div>
                <label htmlFor="disable-password-input" className="mb-1 block text-xs font-semibold text-muted-foreground">
                  Current password (optional if code is entered)
                </label>
                <input
                  id="disable-password-input"
                  name="currentPassword"
                  type="password"
                  value={disablePasswordInput}
                  onChange={(event) => setDisablePasswordInput(event.target.value)}
                  placeholder="••••••••••"
                  className="w-full rounded-xl border-2 border-border bg-background px-4 py-2.5 text-sm text-foreground outline-none focus:border-primary"
                />
              </div>

              <div>
                <label htmlFor="disable-code-input" className="mb-1 block text-xs font-semibold text-muted-foreground">
                  Authentication code or backup code (optional if password is entered)
                </label>
                <input
                  id="disable-code-input"
                  name="code"
                  type="text"
                  value={disableCodeInput}
                  onChange={(event) => setDisableCodeInput(event.target.value)}
                  placeholder="123456 or backup code"
                  className="w-full rounded-xl border-2 border-border bg-background px-4 py-2.5 text-sm text-foreground outline-none focus:border-primary"
                />
              </div>

              <AlertDialogFooter className="flex flex-row justify-end gap-2 pt-2">
                <AlertDialogCancel
                  disabled={disable2faMutation.isPending}
                  onClick={() => setIsDisableDialogOpen(false)}
                  className="rounded-full border-2 border-border-strong px-4 py-2 font-display text-xs"
                >
                  Cancel
                </AlertDialogCancel>
                <button
                  type="submit"
                  disabled={disable2faMutation.isPending || (!disablePasswordInput.trim() && !disableCodeInput.trim())}
                  className="inline-flex cursor-pointer items-center justify-center rounded-full bg-destructive px-5 py-2 font-display text-xs text-destructive-foreground hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-50"
                >
                  {disable2faMutation.isPending ? "Disabling..." : "Confirm disable"}
                </button>
              </AlertDialogFooter>
            </form>
          </AlertDialogContent>
        </AlertDialog>
      </section>

      {/* Section 3: Personal Data Export */}
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

      {/* Section 4: Danger Zone */}
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
