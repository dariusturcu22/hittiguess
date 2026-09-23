package org.dariusturcu.backend.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PaperSizeTest {

    private static final int CARD_SIZE_PIXELS = 800;

    @Test
    void a4FitsThreeCardsPerRowAndFourRowsPerPage() {
        assertThat(PaperSize.A4.cardsPerRow(CARD_SIZE_PIXELS)).isEqualTo(3);
        assertThat(PaperSize.A4.rowsPerPage(CARD_SIZE_PIXELS)).isEqualTo(4);
        assertThat(PaperSize.A4.cardsPerPage(CARD_SIZE_PIXELS)).isEqualTo(12);
    }

    @Test
    void letterFitsThreeCardsPerRowAndFourRowsPerPage() {
        assertThat(PaperSize.LETTER.cardsPerRow(CARD_SIZE_PIXELS)).isEqualTo(3);
        assertThat(PaperSize.LETTER.rowsPerPage(CARD_SIZE_PIXELS)).isEqualTo(4);
        assertThat(PaperSize.LETTER.cardsPerPage(CARD_SIZE_PIXELS)).isEqualTo(12);
    }

    @Test
    void marginsCenterTheCardGridWithinThePage() {
        int expectedMarginX = (PaperSize.A4.getWidthPixels() - PaperSize.A4.cardsPerRow(CARD_SIZE_PIXELS) * CARD_SIZE_PIXELS) / 2;
        int expectedMarginY = (PaperSize.A4.getHeightPixels() - PaperSize.A4.rowsPerPage(CARD_SIZE_PIXELS) * CARD_SIZE_PIXELS) / 2;

        assertThat(PaperSize.A4.marginX(CARD_SIZE_PIXELS)).isEqualTo(expectedMarginX);
        assertThat(PaperSize.A4.marginY(CARD_SIZE_PIXELS)).isEqualTo(expectedMarginY);
    }

    @Test
    void a4AndLetterHaveDifferentPixelDimensions() {
        assertThat(PaperSize.A4.getWidthPixels()).isNotEqualTo(PaperSize.LETTER.getWidthPixels());
        assertThat(PaperSize.A4.getHeightPixels()).isNotEqualTo(PaperSize.LETTER.getHeightPixels());
    }

    @Test
    void everyPaperSizeFitsAtLeastOneCardPerRowAndRowPerPage() {
        for (PaperSize paperSize : PaperSize.values()) {
            assertThat(paperSize.cardsPerRow(CARD_SIZE_PIXELS)).isGreaterThanOrEqualTo(1);
            assertThat(paperSize.rowsPerPage(CARD_SIZE_PIXELS)).isGreaterThanOrEqualTo(1);
            assertThat(paperSize.cardsPerPage(CARD_SIZE_PIXELS)).isGreaterThanOrEqualTo(1);
        }
    }

    @Test
    void everyPaperSizeCentersItsCardGridWithNonNegativeMargins() {
        for (PaperSize paperSize : PaperSize.values()) {
            int expectedMarginX = (paperSize.getWidthPixels() - paperSize.cardsPerRow(CARD_SIZE_PIXELS) * CARD_SIZE_PIXELS) / 2;
            int expectedMarginY = (paperSize.getHeightPixels() - paperSize.rowsPerPage(CARD_SIZE_PIXELS) * CARD_SIZE_PIXELS) / 2;

            assertThat(paperSize.marginX(CARD_SIZE_PIXELS)).isEqualTo(expectedMarginX).isGreaterThanOrEqualTo(0);
            assertThat(paperSize.marginY(CARD_SIZE_PIXELS)).isEqualTo(expectedMarginY).isGreaterThanOrEqualTo(0);
        }
    }

    @Test
    void legalIsTallerThanLetterAtTheSameWidth() {
        assertThat(PaperSize.LEGAL.getWidthPixels()).isEqualTo(PaperSize.LETTER.getWidthPixels());
        assertThat(PaperSize.LEGAL.getHeightPixels()).isGreaterThan(PaperSize.LETTER.getHeightPixels());
    }

    @Test
    void a3IsApproximatelyDoubleA5InEachDimension() {
        // The A-series doubles area each step down; each size is independently rounded from its
        // millimeter dimensions to pixels, so the ratio is close to but not exactly 2, unlike A4's
        // literal-halving relationship to Letter, which shares no such series relationship at all.
        int pixelRoundingTolerance = 15;
        assertThat(PaperSize.A3.getWidthPixels()).isCloseTo(PaperSize.A5.getWidthPixels() * 2, org.assertj.core.data.Offset.offset(pixelRoundingTolerance));
        assertThat(PaperSize.A3.getHeightPixels()).isCloseTo(PaperSize.A5.getHeightPixels() * 2, org.assertj.core.data.Offset.offset(pixelRoundingTolerance));
    }

    @Test
    void a3IsTheLargestPaperSizeByArea() {
        long a3Area = (long) PaperSize.A3.getWidthPixels() * PaperSize.A3.getHeightPixels();
        for (PaperSize paperSize : PaperSize.values()) {
            long area = (long) paperSize.getWidthPixels() * paperSize.getHeightPixels();
            assertThat(a3Area).isGreaterThanOrEqualTo(area);
        }
    }
}
