import type { NextConfig } from "next";

// The dev server rejects non-localhost origins on dev-only assets, which
// leaves a LAN-loaded page with no client JavaScript at all. Derive the
// extra origin from the API URL so a LAN playtest needs no manual edit here.
function lanDevOrigin(): string | undefined {
  const apiHostname = new URL(process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080").hostname;
  return apiHostname === "localhost" || apiHostname === "127.0.0.1" ? undefined : apiHostname;
}

const extraDevOrigin = lanDevOrigin();

const nextConfig: NextConfig = {
  allowedDevOrigins: extraDevOrigin === undefined ? [] : [extraDevOrigin],
};

export default nextConfig;
