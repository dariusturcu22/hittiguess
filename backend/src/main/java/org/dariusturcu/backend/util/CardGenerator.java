package org.dariusturcu.backend.util;

import org.dariusturcu.backend.model.song.ArtistRole;
import org.dariusturcu.backend.model.song.Song;
import org.dariusturcu.backend.model.song.SongArtist;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

// Matches the settled card design in docs/design/source/CardOptions.dc.html: square, one flat
// color, no gradient, a thick dark border, and a hard (unblurred) offset shadow. Artist, year,
// and title are the only content on the face, the settled design carries nothing else.
@Component
public class CardGenerator {
    public static final int CARD_SIZE = 800;
    private static final int CORNER_RADIUS = 105;
    private static final int BORDER_THICKNESS = 24;
    private static final int SHADOW_OFFSET = 28;
    private static final Color BORDER_COLOR = new Color(0x4c, 0x4f, 0x69);
    private static final Color SHADOW_COLOR = new Color(0x4c, 0x4f, 0x69, 64);
    private static final Color LIGHT_TEXT_COLOR = Color.WHITE;
    private static final Color DARK_TEXT_COLOR = new Color(0x1e, 0x1e, 0x2e);
    private static final int LUMINANCE_THRESHOLD_FOR_DARK_TEXT = 150;

    public static BufferedImage generateInfoPage(List<Song> songs, PaperSize paperSize) {
        int pageWidth = paperSize.getWidthPixels();
        int pageHeight = paperSize.getHeightPixels();
        int cardsPerRow = paperSize.cardsPerRow(CARD_SIZE);
        int marginX = paperSize.marginX(CARD_SIZE);
        int marginY = paperSize.marginY(CARD_SIZE);

        BufferedImage page = new BufferedImage(pageWidth, pageHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics2D = page.createGraphics();

        graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics2D.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        graphics2D.setColor(Color.WHITE);
        graphics2D.fillRect(0, 0, pageWidth, pageHeight);

        for (int i = 0; i < songs.size(); i++) {
            int x = marginX + (i % cardsPerRow) * CARD_SIZE;
            int y = marginY + (i / cardsPerRow) * CARD_SIZE;
            drawFrontCard(graphics2D, x, y, CARD_SIZE, songs.get(i));
        }

        graphics2D.dispose();
        return page;
    }

    private static void drawFrontCard(Graphics2D graphics2D, int x, int y, int size, Song song) {
        Color fillColor = decodeColorSafe(song.getGradientColor1());
        Color textColor = readableTextColorFor(fillColor);

        RoundRectangle2D shadowShape = new RoundRectangle2D.Double(
                x + SHADOW_OFFSET, y + SHADOW_OFFSET, size, size, CORNER_RADIUS, CORNER_RADIUS);
        graphics2D.setColor(SHADOW_COLOR);
        graphics2D.fill(shadowShape);

        RoundRectangle2D cardShape = new RoundRectangle2D.Double(x, y, size, size, CORNER_RADIUS, CORNER_RADIUS);
        graphics2D.setColor(fillColor);
        graphics2D.fill(cardShape);

        graphics2D.setColor(BORDER_COLOR);
        graphics2D.setStroke(new BasicStroke(BORDER_THICKNESS));
        graphics2D.draw(cardShape);

        graphics2D.setColor(textColor);
        String fontName = "Kanit";

        Font artistFont = new Font(fontName, Font.PLAIN, 55);
        Font yearFont = new Font(fontName, Font.BOLD, 250);
        Font titleFont = new Font(fontName, Font.ITALIC, 55);

        int padding = 67;

        graphics2D.setFont(artistFont);
        drawCentered(graphics2D, formatArtists(song), x + padding, y + (int) (size * 0.22), size - padding * 2, 70);

        graphics2D.setFont(yearFont);
        FontMetrics yearMetrics = graphics2D.getFontMetrics(yearFont);
        int yearY = y + (size / 2) + (yearMetrics.getAscent() / 3);
        drawCentered(graphics2D, String.valueOf(song.getReleaseYear()), x + padding, yearY, size - padding * 2, 50);

        graphics2D.setFont(titleFont);
        drawCentered(graphics2D, song.getTitle(), x + padding, y + (int) (size * 0.88), size - padding * 2, 65);
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

    // A physical card prints the main artist(s), then "featuring" the featured ones
    // (see docs/GAME_DESIGN.md); role is a display concern only, guessing treats every
    // artist on the list identically.
    private static String formatArtists(Song song) {
        String mainArtists = song.getArtists().stream()
                .filter(artist -> artist.getRole() == ArtistRole.MAIN)
                .map(SongArtist::getName)
                .collect(Collectors.joining(" & "));

        String featuredArtists = song.getArtists().stream()
                .filter(artist -> artist.getRole() == ArtistRole.FEATURED)
                .map(SongArtist::getName)
                .collect(Collectors.joining(", "));

        return featuredArtists.isEmpty() ? mainArtists : mainArtists + " (feat. " + featuredArtists + ")";
    }

    private static Color decodeColorSafe(String hex) {
        try {
            return Color.decode("#" + hex);
        } catch (Exception e) {
            return Color.WHITE;
        }
    }

    // The settled design picks dark or light text per card so it stays legible against
    // whichever flat color that song happens to store, rather than assuming one fixed color
    // works against every possible background.
    private static Color readableTextColorFor(Color backgroundColor) {
        double luminance = 0.299 * backgroundColor.getRed()
                + 0.587 * backgroundColor.getGreen()
                + 0.114 * backgroundColor.getBlue();
        return luminance > LUMINANCE_THRESHOLD_FOR_DARK_TEXT ? DARK_TEXT_COLOR : LIGHT_TEXT_COLOR;
    }
}
