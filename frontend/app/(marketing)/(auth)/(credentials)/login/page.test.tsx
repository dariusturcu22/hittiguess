import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { vi } from "vitest";

import LoginPage from "./page";

const loginMutate = vi.fn();

vi.mock("next/link", () => ({
  default: ({ children, href }: { children: ReactNode; href: string }) => <a href={href}>{children}</a>,
}));
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
}));
vi.mock("@/hooks/generated/authentication-management/authentication-management", () => ({
  useLogin: () => ({ mutate: loginMutate, isPending: false }),
}));

describe("LoginPage", () => {
  beforeEach(() => loginMutate.mockReset());

  it("does not submit invalid credentials", async () => {
    render(<LoginPage />);
    fireEvent.click(screen.getByRole("button", { name: "Log in" }));
    expect((await screen.findAllByText(/too small/i)).length).toBeGreaterThan(0);
    expect(loginMutate).not.toHaveBeenCalled();
  });

  it("submits valid credentials", async () => {
    render(<LoginPage />);
    fireEvent.change(screen.getByLabelText("Email"), { target: { value: "player@example.com" } });
    fireEvent.change(document.querySelector('input[type="password"]')!, { target: { value: "CorrectHorseBatteryStaple1!" } });
    fireEvent.click(screen.getByRole("button", { name: "Log in" }));
    await waitFor(() => expect(loginMutate).toHaveBeenCalledWith({ data: { email: "player@example.com", password: "CorrectHorseBatteryStaple1!" } }));
  });
});
