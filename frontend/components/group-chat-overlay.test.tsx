import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import { GroupChatOverlay } from "./group-chat-overlay";

vi.mock("@/hooks/generated/group-chat/group-chat", () => ({
  useGetHistory: () => ({ data: [], isLoading: false }),
}));

const GROUP_IDENTIFIER = 42;
const MESSAGE_TEXT = "Ready to play";

describe("GroupChatOverlay", () => {
  it("keeps sending disabled until the realtime connection is ready", () => {
    render(
      <GroupChatOverlay
        groupId={GROUP_IDENTIFIER}
        connectionState="connecting"
        onClose={vi.fn()}
        sendChat={vi.fn()}
      />,
    );

    fireEvent.change(screen.getByPlaceholderText("Type a message..."), {
      target: { value: MESSAGE_TEXT },
    });

    expect(screen.getByRole("button", { name: "Send message" })).toBeDisabled();
  });

  it("sends a typed message once realtime is connected", () => {
    const sendChat = vi.fn(() => true);
    render(
      <GroupChatOverlay
        groupId={GROUP_IDENTIFIER}
        connectionState="connected"
        onClose={vi.fn()}
        sendChat={sendChat}
      />,
    );

    const messageInput = screen.getByPlaceholderText("Type a message...");
    fireEvent.change(messageInput, { target: { value: MESSAGE_TEXT } });
    fireEvent.click(screen.getByRole("button", { name: "Send message" }));

    expect(sendChat).toHaveBeenCalledWith(MESSAGE_TEXT);
    expect(messageInput).toHaveValue("");
  });

  it("limits chat messages to the backend character limit", () => {
    render(
      <GroupChatOverlay
        groupId={GROUP_IDENTIFIER}
        connectionState="connected"
        onClose={vi.fn()}
        sendChat={vi.fn(() => true)}
      />,
    );

    const messageInput = screen.getByRole("textbox", { name: "Chat message" });

    expect(messageInput).toHaveAttribute("maxLength", "500");
    expect(screen.getByText("0/500")).toBeVisible();

    fireEvent.change(messageInput, { target: { value: "hello" } });

    expect(screen.getByText("5/500")).toBeVisible();
  });
});
