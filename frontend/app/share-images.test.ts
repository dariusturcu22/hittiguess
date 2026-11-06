import { describe, it, expect, vi } from "vitest";

vi.mock("next/font/local", () => ({
  default: () => ({ variable: "--font-mock" }),
}));

import { metadata as rootMetadata } from "./layout";
import { metadata as joinMetadata } from "./(join)/layout";
import { size as ogSize, contentType as ogContentType } from "./opengraph-image";
import { size as twitterSize, contentType as twitterContentType } from "./twitter-image";
import { size as joinOgSize, contentType as joinOgContentType } from "./(join)/opengraph-image";
import { size as joinTwitterSize, contentType as joinTwitterContentType } from "./(join)/twitter-image";

describe("Open Graph and Twitter share images", () => {
  it("defines standard 1200x630 image dimensions and PNG content types", () => {
    expect(ogSize).toEqual({ width: 1200, height: 630 });
    expect(ogContentType).toBe("image/png");

    expect(twitterSize).toEqual({ width: 1200, height: 630 });
    expect(twitterContentType).toBe("image/png");

    expect(joinOgSize).toEqual({ width: 1200, height: 630 });
    expect(joinOgContentType).toBe("image/png");

    expect(joinTwitterSize).toEqual({ width: 1200, height: 630 });
    expect(joinTwitterContentType).toBe("image/png");
  });

  it("configures openGraph and twitter metadata on root layout", () => {
    const og = rootMetadata.openGraph as Record<string, unknown> | undefined;
    expect(og?.title).toBe("hittiguess | Multiplayer Music Guessing Game");
    expect(og?.type).toBe("website");
    expect(og?.images).toEqual([
      {
        url: "/opengraph-image",
        width: 1200,
        height: 630,
        alt: "hittiguess | Multiplayer Music Guessing Game",
      },
    ]);

    const twitter = rootMetadata.twitter as Record<string, unknown> | undefined;
    expect(twitter?.card).toBe("summary_large_image");
    expect(twitter?.title).toBe("hittiguess | Multiplayer Music Guessing Game");
    expect(twitter?.images).toEqual(["/twitter-image"]);
  });

  it("configures generic preview openGraph and twitter metadata for invite links", () => {
    const og = joinMetadata.openGraph as Record<string, unknown> | undefined;
    expect(og?.title).toBe("Join Game Session | hittiguess");
    expect(og?.description).toContain("invited to play");

    const twitter = joinMetadata.twitter as Record<string, unknown> | undefined;
    expect(twitter?.card).toBe("summary_large_image");
    expect(twitter?.title).toBe("Join Game Session | hittiguess");
  });
});
