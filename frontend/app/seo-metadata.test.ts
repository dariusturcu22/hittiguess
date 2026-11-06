import { describe, it, expect, vi } from "vitest";

vi.mock("next/font/local", () => ({
  default: () => ({ variable: "--font-mock" }),
}));

import { metadata as rootMetadata, viewport as rootViewport } from "./layout";
import { metadata as landingMetadata } from "./(marketing)/(landing)/page";
import { metadata as aboutMetadata } from "./(marketing)/about/page";
import { metadata as creditsMetadata } from "./(marketing)/credits/page";
import { metadata as loginMetadata } from "./(marketing)/(auth)/(credentials)/login/layout";
import { metadata as registerMetadata } from "./(marketing)/(auth)/(credentials)/register/layout";
import { metadata as forgotPasswordMetadata } from "./(marketing)/(auth)/forgot-password/layout";
import { metadata as resetPasswordMetadata } from "./(marketing)/(auth)/reset-password/layout";
import { metadata as verifyEmailMetadata } from "./(marketing)/(auth)/verify-email/layout";
import { metadata as oauth2RedirectMetadata } from "./(marketing)/(auth)/oauth2/redirect/layout";
import { metadata as appLayoutMetadata } from "./(app)/layout";
import { metadata as joinLayoutMetadata } from "./(join)/layout";

describe("SEO Metadata Configuration", () => {
  it("configures root layout metadata and viewport", () => {
    expect(rootMetadata.metadataBase?.toString()).toBe("https://hittiguess.com/");
    expect(rootMetadata.title).toEqual({
      template: "%s | hittiguess",
      default: "hittiguess | Multiplayer Music Guessing Game",
    });
    expect(rootMetadata.description).toBe(
      "A multiplayer music guessing game inspired by Hitster. Hear a song, guess when it was released, and build a chronological timeline with friends."
    );
    expect(rootMetadata.alternates?.canonical).toBe("./");

    expect(rootViewport.themeColor).toEqual([
      { media: "(prefers-color-scheme: light)", color: "#eff1f5" },
      { media: "(prefers-color-scheme: dark)", color: "#1e1e2e" },
    ]);
  });

  it("configures marketing landing page metadata", () => {
    expect(landingMetadata.title).toBe("hittiguess | Multiplayer Music Guessing Game");
    expect(landingMetadata.description).toBeDefined();
    expect(landingMetadata.alternates?.canonical).toBe("/");
  });

  it("configures about and credits page metadata", () => {
    expect(aboutMetadata.title).toBe("About");
    expect(aboutMetadata.description).toContain("Learn about hittiguess");
    expect(aboutMetadata.alternates?.canonical).toBe("/about");

    expect(creditsMetadata.title).toBe("Credits & Licences");
    expect(creditsMetadata.description).toContain("Attribution and licences");
    expect(creditsMetadata.alternates?.canonical).toBe("/credits");
  });

  it("configures public authentication route metadata", () => {
    expect(loginMetadata.title).toBe("Log in");
    expect(loginMetadata.description).toBeDefined();
    expect(loginMetadata.alternates?.canonical).toBe("/login");

    expect(registerMetadata.title).toBe("Create an Account");
    expect(registerMetadata.description).toBeDefined();
    expect(registerMetadata.alternates?.canonical).toBe("/register");

    expect(forgotPasswordMetadata.title).toBe("Forgot Password");
    expect(forgotPasswordMetadata.description).toBeDefined();
    expect(forgotPasswordMetadata.alternates?.canonical).toBe("/forgot-password");

    expect(resetPasswordMetadata.title).toBe("Reset Password");
    expect(resetPasswordMetadata.description).toBeDefined();
    expect(resetPasswordMetadata.alternates?.canonical).toBe("/reset-password");

    expect(verifyEmailMetadata.title).toBe("Verify Email");
    expect(verifyEmailMetadata.description).toBeDefined();
    expect(verifyEmailMetadata.alternates?.canonical).toBe("/verify-email");
  });

  it("sets noindex on authenticated app routes, invite links, and OAuth redirect", () => {
    expect(appLayoutMetadata.robots).toEqual({ index: false, follow: false });
    expect(joinLayoutMetadata.robots).toEqual({ index: false, follow: false });
    expect(oauth2RedirectMetadata.robots).toEqual({ index: false, follow: false });
  });
});
