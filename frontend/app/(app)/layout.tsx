import type { Metadata } from "next";
import { AppSidebar } from "@/components/app-sidebar";
import { AppVoiceSidebar } from "@/components/app-voice-sidebar";
import { ActiveSessionWidget } from "@/components/active-session-widget";

export const metadata: Metadata = {
  robots: {
    index: false,
    follow: false,
  },
};

export default function AppLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <div className="flex h-screen overflow-hidden">
      <AppSidebar />
      <div className="bg-dotted h-full min-w-0 flex-1 overflow-y-auto">
        {children}
      </div>
      <AppVoiceSidebar />
      <ActiveSessionWidget />
    </div>
  );
}
