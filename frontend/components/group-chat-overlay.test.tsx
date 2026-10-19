import { fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { GroupChatOverlay } from "./group-chat-overlay";

interface TestChatMessage {
  id: number;
  senderId: number;
  senderDisplayName: string;
  content: string;
  createdAt: string;
}

const historyState = vi.hoisted(() => ({ messages: [] as TestChatMessage[] }));

vi.mock("@/hooks/generated/group-chat/group-chat", () => ({
  useGetHistory: () => ({ data: historyState.messages, isLoading: false }),
}));

const GROUP_IDENTIFIER = 42;
const MESSAGE_TEXT = "Ready to play";
const FIRST_SENDER_ID = 7;
const SECOND_SENDER_ID = 8;
const FIRST_SENDER_NAME = "Sam";
const SECOND_SENDER_NAME = "Alex";
const OLDEST_MESSAGE_TEXT = "first message";
const NEWEST_MESSAGE_TEXT = "second message";
const OLDEST_MESSAGE_TIMESTAMP = "2024-05-01T20:45:00";
const NEWEST_MESSAGE_TIMESTAMP = "2024-05-01T20:46:00";

describe("GroupChatOverlay", () => {
  beforeEach(() => {
    historyState.messages = [];
  });

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

  it("focuses the message input on open", () => {
    render(
      <GroupChatOverlay
        groupId={GROUP_IDENTIFIER}
        connectionState="connected"
        onClose={vi.fn()}
        sendChat={vi.fn(() => true)}
      />,
    );

    expect(screen.getByRole("textbox", { name: "Chat message" })).toHaveFocus();
  });

  it("closes from the labeled control and Escape key", () => {
    const onClose = vi.fn();
    render(
      <GroupChatOverlay
        groupId={GROUP_IDENTIFIER}
        connectionState="connected"
        onClose={onClose}
        sendChat={vi.fn(() => true)}
      />,
    );

    fireEvent.click(screen.getByRole("button", { name: "Close chat" }));
    fireEvent.keyDown(window, { key: "Escape" });

    expect(onClose).toHaveBeenCalledTimes(2);
  });

  it("closes when clicking outside the panel", () => {
    const onClose = vi.fn();
    render(
      <GroupChatOverlay
        groupId={GROUP_IDENTIFIER}
        connectionState="connected"
        onClose={onClose}
        sendChat={vi.fn(() => true)}
      />,
    );

    fireEvent.click(screen.getByRole("button", { name: "Close chat overlay" }));

    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it("closes from the backdrop in the floating variant", () => {
    const onClose = vi.fn();
    render(
      <GroupChatOverlay
        groupId={GROUP_IDENTIFIER}
        connectionState="connected"
        onClose={onClose}
        sendChat={vi.fn(() => true)}
        floating
      />,
    );

    fireEvent.click(screen.getByRole("button", { name: "Close chat overlay" }));

    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it("keeps a sender's avatar color when a newer message arrives", () => {
    historyState.messages = [
      {
        id: 1,
        senderId: FIRST_SENDER_ID,
        senderDisplayName: FIRST_SENDER_NAME,
        content: OLDEST_MESSAGE_TEXT,
        createdAt: OLDEST_MESSAGE_TIMESTAMP,
      },
    ];
    const view = render(
      <GroupChatOverlay
        groupId={GROUP_IDENTIFIER}
        connectionState="connected"
        onClose={vi.fn()}
        sendChat={vi.fn(() => true)}
      />,
    );
    const colorBeforeNewMessage = screen
      .getByText(FIRST_SENDER_NAME)
      .closest("article")
      ?.querySelector(".avatar-initial")?.className;

    historyState.messages = [
      {
        id: 2,
        senderId: SECOND_SENDER_ID,
        senderDisplayName: SECOND_SENDER_NAME,
        content: NEWEST_MESSAGE_TEXT,
        createdAt: NEWEST_MESSAGE_TIMESTAMP,
      },
      {
        id: 1,
        senderId: FIRST_SENDER_ID,
        senderDisplayName: FIRST_SENDER_NAME,
        content: OLDEST_MESSAGE_TEXT,
        createdAt: OLDEST_MESSAGE_TIMESTAMP,
      },
    ];
    view.rerender(
      <GroupChatOverlay
        groupId={GROUP_IDENTIFIER}
        connectionState="connected"
        onClose={vi.fn()}
        sendChat={vi.fn(() => true)}
      />,
    );

    expect(
      screen.getByText(FIRST_SENDER_NAME).closest("article")?.querySelector(".avatar-initial")?.className,
    ).toBe(colorBeforeNewMessage);
  });

  it("renders message times on a 24-hour clock", () => {
    historyState.messages = [
      {
        id: 1,
        senderId: FIRST_SENDER_ID,
        senderDisplayName: FIRST_SENDER_NAME,
        content: OLDEST_MESSAGE_TEXT,
        createdAt: OLDEST_MESSAGE_TIMESTAMP,
      },
    ];
    render(
      <GroupChatOverlay
        groupId={GROUP_IDENTIFIER}
        connectionState="connected"
        onClose={vi.fn()}
        sendChat={vi.fn(() => true)}
      />,
    );

    expect(screen.getByText("20:45")).toBeVisible();
  });

  it("lists the oldest message first", () => {
    historyState.messages = [
      {
        id: 2,
        senderId: SECOND_SENDER_ID,
        senderDisplayName: SECOND_SENDER_NAME,
        content: NEWEST_MESSAGE_TEXT,
        createdAt: NEWEST_MESSAGE_TIMESTAMP,
      },
      {
        id: 1,
        senderId: FIRST_SENDER_ID,
        senderDisplayName: FIRST_SENDER_NAME,
        content: OLDEST_MESSAGE_TEXT,
        createdAt: OLDEST_MESSAGE_TIMESTAMP,
      },
    ];
    render(
      <GroupChatOverlay
        groupId={GROUP_IDENTIFIER}
        connectionState="connected"
        onClose={vi.fn()}
        sendChat={vi.fn(() => true)}
      />,
    );

    const [oldestRenderedMessage, newestRenderedMessage] = screen
      .getAllByRole("article")
      .map((article) => article.textContent);

    expect(oldestRenderedMessage).toContain(OLDEST_MESSAGE_TEXT);
    expect(newestRenderedMessage).toContain(NEWEST_MESSAGE_TEXT);
  });
});
