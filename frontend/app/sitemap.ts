import type { MetadataRoute } from "next";

const PRODUCTION_URL = "https://hittiguess.com";
const appBaseUrl = process.env.NEXT_PUBLIC_APP_URL || PRODUCTION_URL;

const LANDING_PRIORITY = 1.0;
const ABOUT_PRIORITY = 0.8;
const CREDITS_PRIORITY = 0.7;
const AUTH_PAGE_PRIORITY = 0.5;

export default function sitemap(): MetadataRoute.Sitemap {
  const currentDate = new Date();

  return [
    {
      url: `${appBaseUrl}/`,
      lastModified: currentDate,
      changeFrequency: "weekly",
      priority: LANDING_PRIORITY,
    },
    {
      url: `${appBaseUrl}/about`,
      lastModified: currentDate,
      changeFrequency: "monthly",
      priority: ABOUT_PRIORITY,
    },
    {
      url: `${appBaseUrl}/credits`,
      lastModified: currentDate,
      changeFrequency: "monthly",
      priority: CREDITS_PRIORITY,
    },
    {
      url: `${appBaseUrl}/login`,
      lastModified: currentDate,
      changeFrequency: "monthly",
      priority: AUTH_PAGE_PRIORITY,
    },
    {
      url: `${appBaseUrl}/register`,
      lastModified: currentDate,
      changeFrequency: "monthly",
      priority: AUTH_PAGE_PRIORITY,
    },
  ];
}
