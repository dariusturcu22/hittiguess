package org.dariusturcu.backend.util;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import org.dariusturcu.backend.model.song.Song;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;

@Component
public class QRGenerator {
    private static final int QR_SIZE = 500;

    public static BufferedImage generateQRPage(List<Song> songs, PaperSize paperSize) {
        int cardSize = CardGenerator.CARD_SIZE;
        int pageWidth = paperSize.getWidthPixels();
        int pageHeight = paperSize.getHeightPixels();
        int cardsPerRow = paperSize.cardsPerRow(cardSize);
        int marginX = paperSize.marginX(cardSize);
        int marginY = paperSize.marginY(cardSize);

        BufferedImage page = new BufferedImage(pageWidth, pageHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics2D = page.createGraphics();

        graphics2D.setColor(Color.WHITE);
        graphics2D.fillRect(0, 0, pageWidth, pageHeight);

        for (int i = 0; i < songs.size(); i++) {
            int row = i / cardsPerRow;
            int col = i % cardsPerRow;

            // Mirrored so a double-sided, flip-on-the-long-edge print lines each QR code up
            // behind its corresponding front card.
            int backColumn = (cardsPerRow - 1) - col;

            int x = marginX + backColumn * cardSize;
            int y = marginY + row * cardSize;

            drawQRCard(graphics2D, x, y, cardSize, songs.get(i));
        }

        graphics2D.dispose();
        return page;
    }

    private static void drawQRCard(Graphics2D graphics2D, int x, int y, int cardSize, Song song) {
        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();

            String qrContent = song.getYoutubeId();

            BitMatrix bitMatrix = qrCodeWriter.encode(
                    qrContent,
                    BarcodeFormat.QR_CODE,
                    QR_SIZE,
                    QR_SIZE
            );

            BufferedImage qrCode = MatrixToImageWriter.toBufferedImage(bitMatrix);

            int offset = (cardSize - QR_SIZE) / 2;
            graphics2D.drawImage(qrCode, x + offset, y + offset, null);

            graphics2D.setColor(new Color(200, 200, 200));
            graphics2D.drawRect(x, y, cardSize, cardSize);
        } catch (WriterException e) {
            graphics2D.setColor(Color.RED);
            graphics2D.drawString("QR Error: " + song.getId(), x + 20, y + 50);
        }
    }
}
