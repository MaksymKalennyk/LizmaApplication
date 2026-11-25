package com.example.lizma.service.share;

import java.awt.image.BufferedImage;

/**
 * Tells whether two covers are the same picture, even at different sizes and compressions (Spotify's 300 px
 * thumbnail against Apple's 100 px artwork): both are shrunk to 16×16 shades of grey, and each cell is marked
 * brighter or darker than the picture's average. The same cover differs in a few cells, another one in about half.
 */
final class CoverFingerprint {

    static final int SIDE = 16;
    static final int CELLS = SIDE * SIDE;

    private final boolean[] brighter;

    private CoverFingerprint(boolean[] brighter) {
        this.brighter = brighter;
    }

    static CoverFingerprint of(BufferedImage picture) {
        int width = picture.getWidth();
        int height = picture.getHeight();
        double[] grey = new double[CELLS];
        double total = 0;
        // Each cell is the average of all the pixels it covers, so every pixel counts, whatever the size.
        for (int cellY = 0; cellY < SIDE; cellY++) {
            int top = cellY * height / SIDE;
            int bottom = Math.max(top + 1, (cellY + 1) * height / SIDE);
            for (int cellX = 0; cellX < SIDE; cellX++) {
                int left = cellX * width / SIDE;
                int right = Math.max(left + 1, (cellX + 1) * width / SIDE);
                double sum = 0;
                for (int y = top; y < bottom; y++) {
                    for (int x = left; x < right; x++) {
                        int rgb = picture.getRGB(x, y);
                        sum += 0.299 * ((rgb >> 16) & 0xFF) + 0.587 * ((rgb >> 8) & 0xFF) + 0.114 * (rgb & 0xFF);
                    }
                }
                double cell = sum / ((bottom - top) * (right - left));
                grey[cellY * SIDE + cellX] = cell;
                total += cell;
            }
        }
        double average = total / CELLS;
        boolean[] brighter = new boolean[CELLS];
        for (int i = 0; i < CELLS; i++) {
            brighter[i] = grey[i] > average;
        }
        return new CoverFingerprint(brighter);
    }

    /** In how many of the 256 cells the two covers differ. */
    int distanceTo(CoverFingerprint other) {
        int different = 0;
        for (int i = 0; i < CELLS; i++) {
            if (brighter[i] != other.brighter[i]) {
                different++;
            }
        }
        return different;
    }
}
