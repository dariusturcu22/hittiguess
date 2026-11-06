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

export default function JoinOpenGraphImage() {
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
            fontSize: 34,
            fontWeight: 700,
            color: TEXT_LIGHT,
            textAlign: "center",
            margin: "0 0 14px 0",
          }}
        >
          You are invited to play!
        </p>
        <p
          style={{
            fontSize: 22,
            fontWeight: 500,
            color: TEXT_MUTED,
            textAlign: "center",
            maxWidth: 720,
            margin: 0,
            lineHeight: 1.4,
          }}
        >
          Join your friends for a multiplayer music timeline guessing session.
        </p>
      </div>
    ),
    {
      ...size,
    }
  );
}
