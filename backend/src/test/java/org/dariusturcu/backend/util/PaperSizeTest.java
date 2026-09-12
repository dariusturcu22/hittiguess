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
}
