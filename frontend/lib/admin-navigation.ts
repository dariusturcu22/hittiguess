import { Database, Flag, Layers, LayoutDashboard, type LucideIcon } from "lucide-react";

export interface AdminNavigationItem {
  label: string;
  href: string;
  icon: LucideIcon;
}

const ADMIN_DASHBOARD_HREF = "/admin";

export const ADMIN_NAVIGATION_ITEMS: AdminNavigationItem[] = [
  { label: "Dashboard", href: ADMIN_DASHBOARD_HREF, icon: LayoutDashboard },
  { label: "Backfill queue", href: "/admin/backfill-queue", icon: Layers },
  { label: "Song catalog", href: "/admin/songs", icon: Database },
  { label: "Reports", href: "/admin/reports", icon: Flag },
];

export function isAdminNavigationItemActive(item: AdminNavigationItem, pathname: string | null) {
  if (item.href === ADMIN_DASHBOARD_HREF) {
    return pathname === ADMIN_DASHBOARD_HREF;
  }
  return pathname?.startsWith(item.href) ?? false;
}
