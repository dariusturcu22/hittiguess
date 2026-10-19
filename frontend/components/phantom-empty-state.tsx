import React from "react";
import { Ghost } from "lucide-react";

export function PhantomTrio({ className }: { className?: string }) {
  return (
    <div className={`flex items-center justify-center gap-6 ${className ?? ""}`}>
      <span className="translate-y-5">
        <Ghost
          className="ghost-float-a size-10 text-accent/30"
          strokeWidth={2}
          aria-hidden="true"
        />
      </span>
      <span className="-translate-y-3">
        <Ghost
          className="ghost-float-b size-16 text-accent drop-shadow-[4px_4px_0_var(--shadow-color)]"
          strokeWidth={2}
          aria-label="Ghost"
        />
      </span>
      <span className="translate-y-7">
        <Ghost
          className="ghost-float-c size-9 text-accent/30"
          strokeWidth={2}
          aria-hidden="true"
        />
      </span>
    </div>
  );
}

export function PhantomEmptyState({
  title,
  message,
  action,
}: {
  title: string;
  message?: string;
  action?: React.ReactNode;
}) {
  return (
    <div className="flex flex-1 min-h-0 flex-col items-center justify-center text-center px-6">
      <PhantomTrio className="mb-6" />
      <h2 className="font-display text-2xl text-accent mb-3 [text-shadow:3px_3px_0_var(--text-shadow-on-page)]">
        {title}
      </h2>
      {message ? (
        <p className="max-w-[420px] text-[13px] text-muted-foreground leading-[1.6]">
          {message}
        </p>
      ) : null}
      {action ? <div className="mt-6">{action}</div> : null}
    </div>
  );
}
