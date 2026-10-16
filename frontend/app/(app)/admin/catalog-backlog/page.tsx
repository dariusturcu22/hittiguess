import { redirect } from "next/navigation";

export default function CatalogBacklogRedirect() {
  redirect("/admin/backfill-queue");
}
