import type { MetadataRoute } from "next";

const PRODUCTION_URL = "https://hittiguess.com";
const appBaseUrl = process.env.NEXT_PUBLIC_APP_URL || PRODUCTION_URL;

const DISALLOWED_APP_PATHS = [
  "/admin",
  "/admin/*",
  "/playlists",
  "/playlists/*",
  "/groups",
  "/groups/*",
  "/history",
  "/history/*",
  "/explore",
  "/explore/*",
  "/sessions",
  "/sessions/*",
  "/settings",
  "/settings/*",
  "/playlists/join/*",
  "/groups/join/*",
  "/oauth2/*",
];

const ALLOWED_PUBLIC_PATHS = [
  "/",
  "/about",
  "/credits",
  "/login",
  "/register",
  "/forgot-password",
  "/reset-password",
  "/verify-email",
];

export default function robots(): MetadataRoute.Robots {
  return {
    rules: [
      {
        userAgent: "*",
        allow: ALLOWED_PUBLIC_PATHS,
        disallow: DISALLOWED_APP_PATHS,
      },
    ],
    sitemap: `${appBaseUrl}/sitemap.xml`,
  };
}
