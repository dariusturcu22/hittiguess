import type { Metadata } from "next";

// Invite links render standalone: logged-out visitors must reach the join
// prompt without a session, so these routes sit outside the (app) group and
// its sidebar, voice, and session widget shell.
export const metadata: Metadata = {
  robots: {
    index: false,
    follow: false,
  },
};

export default function JoinLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return <div className="min-h-screen flex flex-col">{children}</div>;
}
