"use client";

import { useEffect, useRef, useState } from "react";
import { Send, X } from "lucide-react";

import { useGetHistory } from "@/hooks/generated/group-chat/group-chat";
import { Button } from "@/components/shadcn/button";
import { chatAvatarColorClass, formatChatMessageTime } from "@/lib/chat-display";

const MAX_CHAT_MESSAGE_LENGTH = 500;
const SCROLL_PIN_THRESHOLD_PIXELS = 24;
const CHAT_PANEL_MAX_HEIGHT_CLASSES = "max-h-[580px]";

function initial(name?: string): string { return name?.trim().charAt(0).toUpperCase() || "?"; }

export function GroupChatOverlay({
  groupId,
  connectionState,
  onClose,
  sendChat,
  floating = false,
}: {
  groupId: number;
  connectionState: "connecting" | "connected" | "disconnected" | "error";
  onClose: () => void;
  sendChat: (content: string) => boolean;
  floating?: boolean;
}) {
  const historyQuery = useGetHistory(groupId, { query: { retry: false } });
  const [message, setMessage] = useState("");
  const messageListReference = useRef<HTMLDivElement>(null);
  const isPinnedToLatestReference = useRef(true);
  const orderedHistory = [...(historyQuery.data ?? [])].reverse();

  useEffect(() => {
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === "Escape") onClose();
    };
    window.addEventListener("keydown", closeOnEscape);
    return () => window.removeEventListener("keydown", closeOnEscape);
  }, [onClose]);

  useEffect(() => {
    const messageList = messageListReference.current;
    if (!messageList || !isPinnedToLatestReference.current) return;
    messageList.scrollTop = messageList.scrollHeight;
  }, [orderedHistory.length]);

  function handleMessageListScroll() {
    const messageList = messageListReference.current;
    if (!messageList) return;
    const distanceFromBottom = messageList.scrollHeight - messageList.scrollTop - messageList.clientHeight;
    isPinnedToLatestReference.current = distanceFromBottom <= SCROLL_PIN_THRESHOLD_PIXELS;
  }

  function submitMessage() {
    if (sendChat(message)) setMessage("");
  }

  return (
    <>
      <button
        type="button"
        aria-label="Close chat overlay"
        onClick={onClose}
        className="fixed inset-0 z-10 cursor-default"
      />
      <aside
        className={`absolute z-30 flex ${CHAT_PANEL_MAX_HEIGHT_CLASSES} w-[380px] max-w-[calc(100vw-3rem)] flex-col rounded-[18px] border-[3px] border-border bg-card/95 backdrop-blur-sm shadow-[6px_6px_0_var(--shadow-color)] ${floating ? "bottom-full left-0 mb-[12px]" : "bottom-0 left-0 sm:left-5"}`}
      >
        <header className="flex items-center justify-between border-b-2 border-border/70 px-5 py-4">
          <div>
            <h2 className="font-bold text-sm text-card-foreground">Chat</h2>
            <span className="text-[10px] text-muted-foreground">{connectionState === "connected" ? "Live" : "Reconnecting"}</span>
          </div>
          <Button type="button" variant="ghost" size="icon-xs" onClick={onClose} aria-label="Close chat" className="text-muted-foreground hover:text-card-foreground">
            <X className="size-4" />
          </Button>
        </header>
        <div
          ref={messageListReference}
          onScroll={handleMessageListScroll}
          className="flex-1 space-y-[13px] overflow-y-auto px-5 py-4 max-h-[440px]"
        >
          {orderedHistory.map((chatMessage) => (
            <article key={chatMessage.id ?? `${chatMessage.senderId}-${chatMessage.createdAt}`} className="flex gap-2.5">
              <span className={`avatar-initial flex size-7 shrink-0 items-center justify-center rounded-full font-display text-[10px] text-primary-foreground ${chatAvatarColorClass(chatMessage.senderId)}`}>
                {initial(chatMessage.senderDisplayName)}
              </span>
              <div className="min-w-0">
                <div className="flex gap-2">
                  <strong className="text-[11px] text-card-foreground">{chatMessage.senderDisplayName ?? "Player"}</strong>
                  <span className="text-[9px] text-muted-foreground">{formatChatMessageTime(chatMessage.createdAt)}</span>
                </div>
                <p className="mt-0.5 break-words text-xs text-card-foreground">{chatMessage.content}</p>
              </div>
            </article>
          ))}
          {historyQuery.isLoading ? <p className="text-xs text-muted-foreground">Loading chat...</p> : null}
          {orderedHistory.length === 0 ? <p className="text-xs text-muted-foreground">No messages yet.</p> : null}
        </div>
        <form onSubmit={(event) => { event.preventDefault(); submitMessage(); }} className="border-t-2 border-border/70 px-4 py-3.5">
          <div className="flex items-center gap-[9px]">
            <input autoFocus value={message} maxLength={MAX_CHAT_MESSAGE_LENGTH} onChange={(event) => setMessage(event.target.value)} placeholder="Type a message..." aria-label="Chat message" className="min-w-0 flex-1 rounded-full border-2 border-border bg-background/85 px-4 py-2.5 text-xs text-foreground outline-none placeholder:text-muted-foreground focus-visible:ring-2 focus-visible:ring-ring" />
            <Button type="submit" size="icon-xs" disabled={!message.trim() || connectionState !== "connected"} aria-label="Send message" className="size-[34px] shrink-0 rounded-full bg-accent text-accent-foreground hover:bg-accent/90 disabled:opacity-50">
              <Send className="size-[15px]" />
            </Button>
          </div>
          <span className="mt-1 block text-right text-[10px] text-muted-foreground">{message.length}/{MAX_CHAT_MESSAGE_LENGTH}</span>
        </form>
      </aside>
    </>
  );
}
