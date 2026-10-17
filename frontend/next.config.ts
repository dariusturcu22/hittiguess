import type { NextConfig } from "next";

// The dev server rejects non-localhost origins on dev-only assets, which
// leaves a LAN-loaded page with no client JavaScript at all. Derive the
// extra origin from the API URL so a LAN playtest needs no manual edit here.
function lanDevOrigin(): string | undefined {
  const apiHostname = new URL(process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080").hostname;
  return apiHostname === "localhost" || apiHostname === "127.0.0.1" ? undefined : apiHostname;
}

// The browser Sentry SDK posts to the origin in its DSN, so the CSP has to allow it.
function sentryIngestOrigin(): string | undefined {
  const dsn = process.env.NEXT_PUBLIC_SENTRY_DSN;
  if (!dsn) {
    return undefined;
  }
  try {
    return new URL(dsn).origin;
  } catch {
    return undefined;
  }
}

const extraDevOrigin = lanDevOrigin();
const sentryOrigin = sentryIngestOrigin();
const apiUrl = new URL(process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080");
const apiWebSocketUrl = new URL(apiUrl);
apiWebSocketUrl.protocol = apiUrl.protocol === "https:" ? "wss:" : "ws:";
const contentSecurityPolicy = [
  "default-src 'self'",
  // React uses eval in development only, to rebuild call stacks for its debugging tools.
  `script-src 'self' 'unsafe-inline'${process.env.NODE_ENV === "development" ? " 'unsafe-eval'" : ""}`,
  "style-src 'self' 'unsafe-inline'",
  `img-src 'self' data: blob: ${apiUrl.origin} https://i.ytimg.com https://*.googleusercontent.com`,
  `connect-src 'self' ${apiUrl.origin} ${apiWebSocketUrl.origin}${sentryOrigin === undefined ? "" : ` ${sentryOrigin}`}`,
  "font-src 'self'",
  "media-src 'self' blob:",
  "worker-src 'self' blob:",
  "frame-ancestors 'none'",
].join("; ");

const nextConfig: NextConfig = {
  allowedDevOrigins: extraDevOrigin === undefined ? [] : [extraDevOrigin],
  async headers() {
    return [{
      source: "/(.*)",
      headers: [
        { key: "Content-Security-Policy", value: contentSecurityPolicy },
        { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
      ],
    }];
  },
};

export default nextConfig;
