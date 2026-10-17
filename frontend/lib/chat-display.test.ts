import { describe, expect, it } from "vitest";

import { CHAT_AVATAR_COLOR_CLASSES, chatAvatarColorClass, formatChatMessageTime } from "./chat-display";

const FIRST_SENDER_ID = 3;
const SECOND_SENDER_ID = 4;
const SENDER_ID_BEYOND_PALETTE = CHAT_AVATAR_COLOR_CLASSES.length + 2;
const EVENING_TIMESTAMP = "2024-05-01T20:45:00";
const MORNING_TIMESTAMP = "2024-05-01T09:05:00";

describe("chatAvatarColorClass", () => {
  it("returns the same color for a sender on every call", () => {
    expect(chatAvatarColorClass(FIRST_SENDER_ID)).toBe(chatAvatarColorClass(FIRST_SENDER_ID));
  });

  it("gives different senders different colors", () => {
    expect(chatAvatarColorClass(FIRST_SENDER_ID)).not.toBe(chatAvatarColorClass(SECOND_SENDER_ID));
  });

  it("wraps a sender id past the palette length", () => {
    const expectedColorForWrappedIndex = CHAT_AVATAR_COLOR_CLASSES[SENDER_ID_BEYOND_PALETTE % CHAT_AVATAR_COLOR_CLASSES.length];

    expect(chatAvatarColorClass(SENDER_ID_BEYOND_PALETTE)).toBe(expectedColorForWrappedIndex);
  });

  it("falls back to the first color without a sender id", () => {
    expect(chatAvatarColorClass(undefined)).toBe(CHAT_AVATAR_COLOR_CLASSES[0]);
  });
});

describe("formatChatMessageTime", () => {
  it("renders a 24-hour clock time", () => {
    expect(formatChatMessageTime(EVENING_TIMESTAMP)).toBe("20:45");
    expect(formatChatMessageTime(MORNING_TIMESTAMP)).toBe("09:05");
  });

  it("returns an empty string without a timestamp", () => {
    expect(formatChatMessageTime(undefined)).toBe("");
  });
});
