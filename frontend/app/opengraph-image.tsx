import { ImageResponse } from "next/og";

export const runtime = "nodejs";

export const size = {
  width: 1200,
  height: 630,
};

export const contentType = "image/png";

const PRIMARY_ORANGE = "#ed6f27";
const BACKGROUND_DARK = "#181825";
const TEXT_LIGHT = "#eff1f5";
const TEXT_MUTED = "#bac2de";
const BADGE_BORDER = "rgba(205, 214, 244, 0.2)";
const BADGE_BACKGROUND = "rgba(205, 214, 244, 0.08)";

export default function OpenGraphImage() {
  return new ImageResponse(
    (
      <div
        style={{
          width: "100%",
          height: "100%",
          display: "flex",
          flexDirection: "column",
          alignItems: "center",
          justifyContent: "center",
          backgroundColor: BACKGROUND_DARK,
          color: TEXT_LIGHT,
          fontFamily: "system-ui, -apple-system, sans-serif",
          padding: 48,
        }}
      >
        <div
          style={{
            display: "flex",
            alignItems: "center",
            gap: 24,
            marginBottom: 28,
          }}
        >
          <div style={{ display: "flex", alignItems: "center", gap: 8, height: 72 }}>
            <div
              style={{
                width: 14,
                height: 48,
                borderRadius: 999,
                backgroundColor: PRIMARY_ORANGE,
              }}
            />
            <div
              style={{
                width: 14,
                height: 32,
                borderRadius: 999,
                backgroundColor: PRIMARY_ORANGE,
              }}
            />
            <div
              style={{
                width: 14,
                height: 72,
                borderRadius: 999,
                backgroundColor: PRIMARY_ORANGE,
              }}
            />
            <div
              style={{
                width: 14,
                height: 42,
                borderRadius: 999,
                backgroundColor: PRIMARY_ORANGE,
              }}
            />
            <div
              style={{
                width: 14,
                height: 60,
                borderRadius: 999,
                backgroundColor: PRIMARY_ORANGE,
              }}
            />
          </div>
          <span
            style={{
              fontSize: 76,
              fontWeight: 900,
              letterSpacing: "-0.03em",
              color: TEXT_LIGHT,
            }}
          >
            hittiguess
          </span>
        </div>
        <p
          style={{
            fontSize: 30,
            fontWeight: 600,
            color: TEXT_MUTED,
            textAlign: "center",
            maxWidth: 820,
            margin: 0,
            lineHeight: 1.4,
          }}
        >
          Hear the song. Guess the year. Build your timeline with friends.
        </p>
        <div
          style={{
            display: "flex",
            alignItems: "center",
            gap: 16,
            marginTop: 40,
            padding: "10px 24px",
            borderRadius: 999,
            backgroundColor: BADGE_BACKGROUND,
            border: `1px solid ${BADGE_BORDER}`,
            fontSize: 18,
            color: TEXT_MUTED,
          }}
        >
          <span>Free &amp; Open Source</span>
          <span>•</span>
          <span>No Ads</span>
          <span>•</span>
          <span>Zero Trackers</span>
        </div>
      </div>
    ),
    {
      ...size,
    }
  );
}
