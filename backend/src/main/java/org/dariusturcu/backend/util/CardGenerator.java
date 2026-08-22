package org.dariusturcu.backend.util;

import org.dariusturcu.backend.model.song.Country;
import org.dariusturcu.backend.model.song.Song;
import org.dariusturcu.backend.model.song.SongTag;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

@Component
public class CardGenerator {
    private static final int CARD_SIZE = 800;
    private static final int CARDS_PER_ROW = 3;
    private static final int ROWS_PER_PAGE = 4;
    private static final int PAGE_WIDTH = CARD_SIZE * CARDS_PER_ROW;
    private static final int PAGE_HEIGHT = CARD_SIZE * ROWS_PER_PAGE;
    private static final int MARGIN_X = (PAGE_WIDTH - CARD_SIZE * CARDS_PER_ROW) / 2;
    private static final int MARGIN_Y = (PAGE_HEIGHT - CARD_SIZE * ROWS_PER_PAGE) / 2;


    public static BufferedImage generateInfoPage(List<Song> songs) {
        BufferedImage page = new BufferedImage(PAGE_WIDTH, PAGE_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics2D = page.createGraphics();

        graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics2D.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        graphics2D.setColor(Color.WHITE);
        graphics2D.fillRect(0, 0, PAGE_WIDTH, PAGE_HEIGHT);

        for (int i = 0; i < songs.size(); i++) {
            int x = MARGIN_X + (i % CARDS_PER_ROW) * CARD_SIZE;
            int y = MARGIN_Y + (i / CARDS_PER_ROW) * CARD_SIZE;
            drawFrontCard(graphics2D, x, y, CARD_SIZE, songs.get(i));
        }

        graphics2D.dispose();
        return page;
    }

    private static void drawFrontCard(Graphics2D graphics2D, int x, int y, int size, Song song) {
        Color color1 = decodeColorSafe(song.getGradientColor1());
        Color color2 = decodeColorSafe(song.getGradientColor2());

        GradientPaint gradientPaint = new GradientPaint(x, y, color1, x, y + size, color2);
        graphics2D.setPaint(gradientPaint);
        graphics2D.fillRect(x, y, size, size);

        graphics2D.setColor(Color.WHITE);
        String fontName = "Kanit";

        Font artistFont = new Font(fontName, Font.PLAIN, 55);
        Font yearFont = new Font(fontName, Font.BOLD, 250);
        Font titleFont = new Font(fontName, Font.ITALIC, 55);

        int padding = 67;

        graphics2D.setFont(artistFont);
        drawCentered(graphics2D, song.getArtist(), x + padding, y + (int) (size * 0.22), size - padding * 2, 70);

        graphics2D.setFont(yearFont);
        FontMetrics yearMetrics = graphics2D.getFontMetrics(yearFont);
        int yearY = y + (size / 2) + (yearMetrics.getAscent() / 3);
        drawCentered(graphics2D, String.valueOf(song.getReleaseYear()), x + padding, yearY, size - padding * 2, 50);

        graphics2D.setFont(titleFont);
        drawCentered(graphics2D, song.getTitle(), x + padding, y + (int) (size * 0.88), size - padding * 2, 65);

        drawTagTriangle(graphics2D, x, y, size, song);
        drawCountryFlag(graphics2D, x, y, size, song);

        graphics2D.setColor(new Color(0, 0, 0, 40));
        graphics2D.drawRect(x, y, size, size);
    }

    private static void drawCentered(Graphics2D graphics2D, String text, int x, int y, int maxWidth, int lineHeight) {
        FontMetrics fontMetrics = graphics2D.getFontMetrics();
        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        List<String> lines = new ArrayList<>();

        for (String word : words) {
            String test = line.isEmpty() ? word : line + " " + word;
            if (fontMetrics.stringWidth(test) > maxWidth) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(test);
            }
        }
        if (!line.isEmpty()) lines.add(line.toString());

        int totalHeight = lines.size() * lineHeight;
        int startY = y - totalHeight / 2;

        for (String l : lines) {
            int textWidth = fontMetrics.stringWidth(l);
            graphics2D.drawString(l, x + (maxWidth - textWidth) / 2, startY);
            startY += lineHeight;
        }
    }

    private static Color decodeColorSafe(String hex) {
        try {
            return Color.decode("#" + hex);
        } catch (Exception e) {
            return Color.WHITE;
        }
    }

    private static void drawTagTriangle(Graphics2D graphics2D, int x, int y, int size, Song song) {
        if (song.getSongTag() == null || song.getSongTag() == SongTag.NONE) return;

        Color tagColor;
        switch (song.getSongTag()) {
            case SPECIAL -> tagColor = new Color(0, 255, 0);
            case ANIME -> tagColor = new Color(255, 105, 180);
            case PLAYLIST -> tagColor = decodeColorSafe(song.getPlaylist().getColor());
            default -> {
                return;
            }
        }

        int triangleSize = (int) (size * 0.10);
        int[] xPoints = {x, x + triangleSize, x};
        int[] yPoints = {y, y, y + triangleSize};

        graphics2D.setColor(tagColor);
        graphics2D.fillPolygon(xPoints, yPoints, 3);
    }

    private static void drawCountryFlag(Graphics2D graphics2D, int x, int y, int size, Song song) {
        if (song.getCountry() == null || song.getCountry() == Country.NONE) return;

        int flagW = (int) (size * 0.10);
        int flagH = (int) (flagW * 0.6);
        int flagX = x + size - flagW;
        int flagY = y + size - flagH;

        switch (song.getCountry()) {
            case RO -> drawRomanianFlag(graphics2D, flagX, flagY, flagW, flagH);
        }
    }

    private static void drawRomanianFlag(Graphics2D graphics2D, int flagX, int flagY, int flagW, int flagH) {
        int third = flagW / 3;
        graphics2D.setColor(new Color(0, 43, 127));
        graphics2D.fillRect(flagX, flagY, third, flagH);
        graphics2D.setColor(new Color(252, 209, 22));
        graphics2D.fillRect(flagX + third, flagY, third, flagH);
        graphics2D.setColor(new Color(206, 17, 38));
        graphics2D.fillRect(flagX + third * 2, flagY, flagW - third * 2, flagH);
    }
}
