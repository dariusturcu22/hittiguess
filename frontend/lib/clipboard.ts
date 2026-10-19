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

// An open dialog or menu traps focus inside itself, so a textarea mounted on the
// body loses focus, and its selection, before the copy runs. Mounting it next to
// the focused element keeps it inside whatever trap is active.
function fallbackCopyContainer(): HTMLElement {
  const focusedElement = document.activeElement;
  if (focusedElement instanceof HTMLElement && focusedElement !== document.body) {
    return focusedElement.parentElement ?? document.body;
  }
  return document.body;
}

function fallbackCopy(value: string): boolean {
  const previouslyFocusedElement = document.activeElement;
  const textArea = document.createElement("textarea");
  textArea.value = value;
  textArea.setAttribute("readonly", "");
  textArea.setAttribute("aria-hidden", "true");
  textArea.style.position = "fixed";
  textArea.style.top = "0";
  textArea.style.left = "0";
  textArea.style.opacity = "0";
  const container = fallbackCopyContainer();
  container.appendChild(textArea);
  textArea.focus({ preventScroll: true });
  textArea.select();
  textArea.setSelectionRange(0, value.length);
  try {
    return document.execCommand("copy");
  } catch {
    return false;
  } finally {
    container.removeChild(textArea);
    if (previouslyFocusedElement instanceof HTMLElement) previouslyFocusedElement.focus({ preventScroll: true });
  }
}
