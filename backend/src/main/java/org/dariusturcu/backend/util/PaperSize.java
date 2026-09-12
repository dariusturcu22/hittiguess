package org.dariusturcu.backend.util;

public enum PaperSize {
    A4(2480, 3508),
    LETTER(2550, 3300);

    private final int widthPixels;
    private final int heightPixels;

    PaperSize(int widthPixels, int heightPixels) {
        this.widthPixels = widthPixels;
        this.heightPixels = heightPixels;
    }

    public int getWidthPixels() {
        return widthPixels;
    }

    public int getHeightPixels() {
        return heightPixels;
    }

    public int cardsPerRow(int cardSizePixels) {
        return widthPixels / cardSizePixels;
    }

    public int rowsPerPage(int cardSizePixels) {
        return heightPixels / cardSizePixels;
    }

    public int cardsPerPage(int cardSizePixels) {
        return cardsPerRow(cardSizePixels) * rowsPerPage(cardSizePixels);
    }

    public int marginX(int cardSizePixels) {
        return (widthPixels - cardsPerRow(cardSizePixels) * cardSizePixels) / 2;
    }

    public int marginY(int cardSizePixels) {
        return (heightPixels - rowsPerPage(cardSizePixels) * cardSizePixels) / 2;
    }
}
