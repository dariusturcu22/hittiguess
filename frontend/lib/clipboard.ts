// navigator.clipboard exists only in secure contexts (HTTPS or localhost),
// so plain-HTTP LAN testing needs the legacy path below. Returns whether
// the text reached the clipboard through either route.
export async function copyText(value: string): Promise<boolean> {
  if (typeof navigator !== "undefined" && navigator.clipboard) {
    try {
      await navigator.clipboard.writeText(value);
      return true;
    } catch {
      return fallbackCopy(value);
    }
  }
  return fallbackCopy(value);
}

function fallbackCopy(value: string): boolean {
  const textArea = document.createElement("textarea");
  textArea.value = value;
  textArea.setAttribute("readonly", "");
  textArea.style.position = "fixed";
  textArea.style.opacity = "0";
  document.body.appendChild(textArea);
  textArea.select();
  try {
    return document.execCommand("copy");
  } catch {
    return false;
  } finally {
    document.body.removeChild(textArea);
  }
}
