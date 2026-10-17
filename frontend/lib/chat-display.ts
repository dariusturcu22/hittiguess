export const CHAT_AVATAR_COLOR_CLASSES = [
  "bg-peach",
  "bg-blue",
  "bg-warning",
  "bg-pink",
  "bg-green",
  "bg-accent",
] as const;

export function chatAvatarColorClass(senderId?: number): string {
  const paletteLength = CHAT_AVATAR_COLOR_CLASSES.length;
  const senderIndex = Math.abs(senderId ?? 0) % paletteLength;
  return CHAT_AVATAR_COLOR_CLASSES[senderIndex];
}

export function formatChatMessageTime(createdAt?: string): string {
  if (!createdAt) return "";
  return new Date(createdAt).toLocaleTimeString([], {
    hour: "2-digit",
    minute: "2-digit",
    hourCycle: "h23",
  });
}
