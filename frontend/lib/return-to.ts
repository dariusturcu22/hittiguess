// Validates a post-login redirect target from a query param: same-origin
// absolute paths only, so a crafted link can never bounce a user off-site.
// Browsers strip tabs and newlines from URLs, so "/\t/host" would become "//host".
const CONTROL_CHARACTER = /[\u0000-\u001F\u007F]/;

export function safeReturnToPath(value: string | null): string | null {
  if (!value || !value.startsWith("/") || value.startsWith("//") || value.includes("\\") || CONTROL_CHARACTER.test(value)) {
    return null;
  }
  return value;
}
