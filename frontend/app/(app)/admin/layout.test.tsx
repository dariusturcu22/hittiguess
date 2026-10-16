import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import AdminLayout from "./layout";

let currentUserResponse: { data?: { role?: string }; isLoading: boolean } = { isLoading: false };

vi.mock("@/hooks/generated/user-management/user-management", () => ({
  useGetCurrentUser: () => currentUserResponse,
}));

function renderLayout() {
  return render(
    <AdminLayout>
      <p>Admin page content</p>
    </AdminLayout>,
  );
}

describe("AdminLayout", () => {
  beforeEach(() => {
    currentUserResponse = { isLoading: false };
  });

  it("shows a loading state while the account is loading", () => {
    currentUserResponse = { isLoading: true };
    renderLayout();

    expect(screen.getByText("Checking admin access...")).toBeInTheDocument();
    expect(screen.queryByText("Admin page content")).not.toBeInTheDocument();
  });

  it("shows the denial for a non-admin account", () => {
    currentUserResponse = { data: { role: "USER" }, isLoading: false };
    renderLayout();

    expect(screen.getByRole("heading", { name: "Admins only" })).toBeInTheDocument();
    expect(screen.queryByText("Admin page content")).not.toBeInTheDocument();
  });

  it("shows the denial when the account could not be loaded", () => {
    renderLayout();

    expect(screen.getByRole("heading", { name: "Admins only" })).toBeInTheDocument();
  });

  it("renders the page for an admin account", () => {
    currentUserResponse = { data: { role: "ADMIN" }, isLoading: false };
    renderLayout();

    expect(screen.getByText("Admin page content")).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Admins only" })).not.toBeInTheDocument();
  });
});
