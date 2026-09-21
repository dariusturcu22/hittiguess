import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { PlaylistCoverMosaic } from "./playlist-cover-mosaic";

describe("PlaylistCoverMosaic", () => {
  it("fills tiles progressively with thumbnails then placeholders", () => {
    render(<PlaylistCoverMosaic previewYoutubeIds={["abc12345678", "def12345678"]} />);

    const thumbnails = screen.getAllByRole("presentation", { hidden: true });
    expect(thumbnails).toHaveLength(2);
    expect(thumbnails[0]).toHaveAttribute(
      "src",
      "https://i.ytimg.com/vi/abc12345678/hqdefault.jpg",
    );
    expect(thumbnails[0]).toHaveClass("object-cover");
  });

  it("renders four placeholders when the playlist is empty", () => {
    const { container } = render(<PlaylistCoverMosaic previewYoutubeIds={[]} />);

    expect(container.querySelectorAll("img")).toHaveLength(0);
    expect(container.firstElementChild?.children).toHaveLength(4);
  });

  it("caps the mosaic at four thumbnails", () => {
    const { container } = render(
      <PlaylistCoverMosaic
        previewYoutubeIds={["a", "b", "c", "d", "e"]}
      />,
    );

    expect(container.querySelectorAll("img")).toHaveLength(4);
  });
});
