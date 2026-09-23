import { render } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import OAuth2RedirectHandler from "./RedirectHandler";

const pushedPaths: string[] = [];
let redirectSearchParams = new URLSearchParams();

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: (path: string) => pushedPaths.push(path) }),
  useSearchParams: () => redirectSearchParams,
}));

vi.mock("@/components/logo", () => ({
  LogoIcon: () => <span>logo</span>,
}));

vi.mock("@/components/auth-page-background", () => ({
  AuthPageBackground: () => <div aria-hidden="true" />,
}));

vi.mock("@/components/theme-toggle", () => ({
  ThemeToggle: () => <button type="button" aria-label="theme">toggle</button>,
}));

vi.mock("next/link", () => ({
  default: ({ children, href }: { children: React.ReactNode; href: string }) => (
    <a href={href}>{children}</a>
  ),
}));

describe("OAuth2RedirectHandler", () => {
  it("routes a clean callback into the library", () => {
    pushedPaths.length = 0;
    redirectSearchParams = new URLSearchParams();
    render(<OAuth2RedirectHandler />);

    expect(pushedPaths).toEqual(["/playlists"]);
  });

  it("routes a failed callback back to login with the error", () => {
    pushedPaths.length = 0;
    redirectSearchParams = new URLSearchParams("error=oauth2_failed");
    render(<OAuth2RedirectHandler />);

    expect(pushedPaths).toEqual(["/login?error=oauth2_failed"]);
  });

  it("routes a clean callback back to the invite that started it", () => {
    pushedPaths.length = 0;
    redirectSearchParams = new URLSearchParams("returnTo=/groups/join/abc123");
    render(<OAuth2RedirectHandler />);

    expect(pushedPaths).toEqual(["/groups/join/abc123"]);
  });

  it("ignores an off-site callback destination", () => {
    pushedPaths.length = 0;
    redirectSearchParams = new URLSearchParams("returnTo=https://evil.example.com");
    render(<OAuth2RedirectHandler />);

    expect(pushedPaths).toEqual(["/playlists"]);
  });
});
