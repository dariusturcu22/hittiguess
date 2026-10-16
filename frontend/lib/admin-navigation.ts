import { Database, Flag, Layers, type LucideIcon } from "lucide-react";

export interface AdminNavigationItem {
  label: string;
  href: string;
  icon: LucideIcon;
}

export const ADMIN_NAVIGATION_ITEMS: AdminNavigationItem[] = [
  { label: "Backfill queue", href: "/admin/catalog-backlog", icon: Layers },
  { label: "Song catalog", href: "/admin/songs", icon: Database },
  { label: "Reports", href: "/admin/reports", icon: Flag },
];

export function isAdminNavigationItemActive(item: AdminNavigationItem, pathname: string | null) {
  return pathname?.startsWith(item.href) ?? false;
}
