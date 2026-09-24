// Validates a post-login redirect target from a query param: same-origin
// absolute paths only, so a crafted link can never bounce a user off-site.
export function safeReturnToPath(value: string | null): string | null {
  if (!value || !value.startsWith("/") || value.startsWith("//") || value.includes("\\")) {
    return null;
  }
  return value;
}
