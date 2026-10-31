import { describe, it, expect } from "vitest";
import robots from "./robots";
import sitemap from "./sitemap";

describe("robots.ts and sitemap.ts", () => {
  it("generates robots.txt rules that disallow private routes and point to sitemap", () => {
    const robotsConfig = robots();

    expect(robotsConfig.sitemap).toBe("https://hittiguess.com/sitemap.xml");

    const rules = Array.isArray(robotsConfig.rules) ? robotsConfig.rules[0] : robotsConfig.rules;
    expect(rules).toBeDefined();
    expect(rules?.userAgent).toBe("*");

    const disallowedPaths = Array.isArray(rules?.disallow) ? rules.disallow : [rules?.disallow];
    expect(disallowedPaths).toContain("/admin");
    expect(disallowedPaths).toContain("/admin/*");
    expect(disallowedPaths).toContain("/playlists");
    expect(disallowedPaths).toContain("/groups");
    expect(disallowedPaths).toContain("/sessions");
    expect(disallowedPaths).toContain("/playlists/join/*");
    expect(disallowedPaths).toContain("/groups/join/*");

    const allowedPaths = Array.isArray(rules?.allow) ? rules.allow : [rules?.allow];
    expect(allowedPaths).toContain("/");
    expect(allowedPaths).toContain("/about");
    expect(allowedPaths).toContain("/credits");
    expect(allowedPaths).toContain("/login");
    expect(allowedPaths).toContain("/register");
  });

  it("generates sitemap.xml entries for public marketing and informational pages", () => {
    const entries = sitemap();

    expect(entries.length).toBeGreaterThanOrEqual(5);

    const urls = entries.map((entry) => entry.url);
    expect(urls).toContain("https://hittiguess.com/");
    expect(urls).toContain("https://hittiguess.com/about");
    expect(urls).toContain("https://hittiguess.com/credits");
    expect(urls).toContain("https://hittiguess.com/login");
    expect(urls).toContain("https://hittiguess.com/register");

    for (const entry of entries) {
      expect(entry.lastModified).toBeInstanceOf(Date);
      expect(typeof entry.priority).toBe("number");
      expect(entry.priority).toBeGreaterThan(0);
      expect(entry.priority).toBeLessThanOrEqual(1.0);
      expect(entry.changeFrequency).toBeDefined();
    }
  });
});
