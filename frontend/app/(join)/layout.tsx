import type { Metadata } from "next";

// Invite links render standalone: logged-out visitors must reach the join
// prompt without a session, so these routes sit outside the (app) group and
// its sidebar, voice, and session widget shell.
export const metadata: Metadata = {
  robots: {
    index: false,
    follow: false,
  },
  openGraph: {
    title: "Join Game Session | hittiguess",
    description: "You have been invited to play hittiguess with friends.",
    images: [
      {
        url: "/opengraph-image",
        width: 1200,
        height: 630,
        alt: "hittiguess game session invitation",
      },
    ],
  },
  twitter: {
    card: "summary_large_image",
    title: "Join Game Session | hittiguess",
    description: "You have been invited to play hittiguess with friends.",
    images: ["/twitter-image"],
  },
};

export default function JoinLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return <div className="min-h-screen flex flex-col">{children}</div>;
}
