import { afterEach, describe, expect, it, vi } from "vitest";

import { copyText } from "./clipboard";

describe("copyText", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it("uses the Clipboard API when available", async () => {
    const writeText = vi.fn(() => Promise.resolve());
    vi.stubGlobal("navigator", { clipboard: { writeText } });

    await expect(copyText("invite link")).resolves.toBe(true);
    expect(writeText).toHaveBeenCalledWith("invite link");
  });

  it("falls back to execCommand without a Clipboard API", async () => {
    vi.stubGlobal("navigator", {});
    const execCommand = vi.fn(() => true);
    Object.defineProperty(document, "execCommand", { value: execCommand, configurable: true });

    await expect(copyText("invite link")).resolves.toBe(true);
    expect(execCommand).toHaveBeenCalledWith("copy");
  });

  it("mounts the fallback inside the focused element's container so a focus trap keeps the selection", async () => {
    vi.stubGlobal("navigator", {});
    const menu = document.createElement("div");
    const menuItem = document.createElement("button");
    menu.appendChild(menuItem);
    document.body.appendChild(menu);
    menuItem.focus();
    let selectedText = "";
    let textAreaParent: Node | null = null;
    const execCommand = vi.fn(() => {
      const textArea = document.activeElement as HTMLTextAreaElement;
      textAreaParent = textArea.parentNode;
      selectedText = textArea.value.slice(textArea.selectionStart, textArea.selectionEnd);
      return true;
    });
    Object.defineProperty(document, "execCommand", { value: execCommand, configurable: true });

    await expect(copyText("invite code")).resolves.toBe(true);
    expect(textAreaParent).toBe(menu);
    expect(selectedText).toBe("invite code");
    expect(document.activeElement).toBe(menuItem);
    menu.remove();
  });

  it("reports failure when both routes fail", async () => {
    vi.stubGlobal("navigator", {
      clipboard: { writeText: vi.fn(() => Promise.reject(new Error("denied"))) },
    });
    Object.defineProperty(document, "execCommand", { value: vi.fn(() => false), configurable: true });

    await expect(copyText("invite link")).resolves.toBe(false);
  });
});
