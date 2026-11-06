import { describe, it, expect } from "vitest";
import fs from "node:fs";
import path from "node:path";
import manifest from "./manifest";

describe("Web App Manifest and Icon Set", () => {
  it("configures web app manifest with required properties and icons", () => {
    const manifestData = manifest();

    expect(manifestData.name).toBe("hittiguess");
    expect(manifestData.short_name).toBe("hittiguess");
    expect(manifestData.display).toBe("standalone");
    expect(manifestData.start_url).toBe("/");
    expect(manifestData.theme_color).toBe("#eff1f5");
    expect(manifestData.background_color).toBe("#181825");

    const iconSizes = manifestData.icons?.map((icon) => icon.sizes);
    expect(iconSizes).toContain("192x192");
    expect(iconSizes).toContain("512x512");

    const maskableIcons = manifestData.icons?.filter((icon) => icon.purpose === "maskable");
    expect(maskableIcons?.length).toBeGreaterThanOrEqual(1);
    expect(maskableIcons?.some((icon) => icon.sizes === "512x512")).toBe(true);
  });

  it("verifies icon files exist in public directory with valid PNG headers", () => {
    const publicDir = path.resolve(__dirname, "../public");
    const requiredIcons = [
      "icon-192.png",
      "icon-512.png",
      "icon-maskable-192.png",
      "icon-maskable-512.png",
    ];

    for (const iconName of requiredIcons) {
      const iconPath = path.join(publicDir, iconName);
      expect(fs.existsSync(iconPath)).toBe(true);

      const buffer = fs.readFileSync(iconPath);
      // PNG header magic bytes: 0x89 0x50 0x4E 0x47 0x0D 0x0A 0x1A 0x0A
      expect(buffer[0]).toBe(0x89);
      expect(buffer[1]).toBe(0x50);
      expect(buffer[2]).toBe(0x4e);
      expect(buffer[3]).toBe(0x47);
    }
  });

  it("verifies icon.svg supports light and dark tab bar themes", () => {
    const iconSvgPath = path.resolve(__dirname, "icon.svg");
    expect(fs.existsSync(iconSvgPath)).toBe(true);

    const svgContent = fs.readFileSync(iconSvgPath, "utf-8");
    expect(svgContent).toContain("prefers-color-scheme:dark");
    expect(svgContent).toContain("#499f36");
    expect(svgContent).toContain("#a6e3a1");
  });
});
