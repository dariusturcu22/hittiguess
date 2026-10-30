import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { SongCard } from "./game-pieces";

const LONG_TITLE = "A title that is far too long to ever fit on one line of a song card";
const LONG_ARTISTS = "First Artist, Second Artist, Third Artist";

describe("SongCard text", () => {
  it("renders the full title and artists without truncation classes", () => {
    const { container } = render(<SongCard artist={LONG_ARTISTS} title={LONG_TITLE} year={1999} />);

    expect(screen.getByText(LONG_TITLE)).toBeVisible();
    expect(screen.getByText(LONG_ARTISTS)).toBeVisible();
    expect(container.innerHTML).not.toContain("line-clamp");
    expect(container.innerHTML).not.toContain("truncate");
  });

  it("keeps the year in its own row between the artist and the title", () => {
    render(<SongCard artist="Artist" title="Title" year={1999} />);

    const card = screen.getByText("1999").closest("article");
    expect(card?.className).toContain("grid-rows-[minmax(0,1fr)_auto_minmax(0,1fr)]");
    expect(card?.children[1]).toHaveTextContent("1999");
  });
});
