"use client";

import { useEffect, useState } from "react";
import { Send, X } from "lucide-react";

import { useGetHistory } from "@/hooks/generated/group-chat/group-chat";
import { Button } from "@/components/shadcn/button";

const CHAT_COLORS = ["bg-peach", "bg-blue", "bg-warning", "bg-pink", "bg-green", "bg-accent"];

function initial(name?: string): string { return name?.trim().charAt(0).toUpperCase() || "?"; }

export function GroupChatOverlay({
  groupId,
  connectionState,
  onClose,
  sendChat,
}: {
  groupId: number;
  connectionState: "connecting" | "connected" | "disconnected" | "error";
  onClose: () => void;
  sendChat: (content: string) => boolean;
}) {
  const historyQuery = useGetHistory(groupId, { query: { retry: false } });
  const [message, setMessage] = useState("");

  useEffect(() => {
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === "Escape") onClose();
    };
    window.addEventListener("keydown", closeOnEscape);
    return () => window.removeEventListener("keydown", closeOnEscape);
  }, [onClose]);

  function submitMessage() {
    if (sendChat(message)) setMessage("");
  }

  return <aside className="absolute bottom-0 left-0 z-30 flex h-[390px] w-full max-w-[360px] flex-col rounded-t-[18px] border-[3px] border-border bg-card/95 shadow-[6px_6px_0_rgba(0,0,0,0.35)] backdrop-blur sm:left-5"><header className="flex items-center justify-between border-b-2 border-border/70 px-4 py-3"><div><h2 className="font-display text-sm text-card-foreground">Chat</h2><span className="text-[10px] text-muted-foreground">{connectionState === "connected" ? "Live" : "Reconnecting"}</span></div><Button type="button" variant="ghost" size="icon-xs" onClick={onClose} aria-label="Close chat" className="text-muted-foreground hover:text-card-foreground"><X className="size-4" /></Button></header><div className="flex-1 space-y-3 overflow-y-auto px-4 py-4">{historyQuery.data?.map((chatMessage, index) => <article key={chatMessage.id ?? `${chatMessage.senderId}-${chatMessage.createdAt}`} className="flex gap-2.5"><span className={`flex size-7 shrink-0 items-center justify-center rounded-full font-display text-[10px] text-primary-foreground ${CHAT_COLORS[index % CHAT_COLORS.length]}`}>{initial(chatMessage.senderDisplayName)}</span><div className="min-w-0"><div className="flex gap-2"><strong className="text-[11px] text-card-foreground">{chatMessage.senderDisplayName ?? "Player"}</strong><span className="text-[9px] text-muted-foreground">{chatMessage.createdAt ? new Date(chatMessage.createdAt).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" }) : ""}</span></div><p className="mt-0.5 break-words text-xs text-card-foreground">{chatMessage.content}</p></div></article>)}{historyQuery.isLoading ? <p className="text-xs text-muted-foreground">Loading chat...</p> : null}{historyQuery.data?.length === 0 ? <p className="text-xs text-muted-foreground">No messages yet.</p> : null}</div><form onSubmit={(event) => { event.preventDefault(); submitMessage(); }} className="flex items-center gap-2 border-t-2 border-border/70 p-4"><input value={message} onChange={(event) => setMessage(event.target.value)} placeholder="Type a message..." className="min-w-0 flex-1 rounded-full border-2 border-border bg-background px-3 py-2 text-xs text-foreground outline-none placeholder:text-muted-foreground focus-visible:ring-2 focus-visible:ring-ring" /><Button type="submit" size="icon-xs" disabled={!message.trim() || connectionState !== "connected"} aria-label="Send message" className="size-8 rounded-full"><Send className="size-3.5" /></Button></form></aside>;
}
