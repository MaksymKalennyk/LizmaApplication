package com.example.lizma.service.share;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Random;

/** Made-up album covers for tests: each seed paints a different one. */
public final class TestCovers {

    private TestCovers() {
    }

    /** A 300 px cover, like Spotify's thumbnail. */
    public static BufferedImage cover(long seed) {
        BufferedImage image = new BufferedImage(300, 300, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        Random random = new Random(seed);
        // A mosaic underneath gives every cover detail all over, as real covers have; a few big shapes on top.
        for (int row = 0; row < 8; row++) {
            for (int column = 0; column < 8; column++) {
                graphics.setColor(new Color(random.nextInt(0xFFFFFF)));
                graphics.fillRect(column * 300 / 8, row * 300 / 8, 300 / 8 + 1, 300 / 8 + 1);
            }
        }
        for (int i = 0; i < 6; i++) {
            graphics.setColor(new Color(random.nextInt(0xFFFFFF)));
            graphics.fillRect(random.nextInt(260), random.nextInt(260), 30 + random.nextInt(170), 30 + random.nextInt(170));
        }
        graphics.dispose();
        return image;
    }

    /** The same cover the way Apple's search hands it out: 100 px and JPEG-compressed. */
    public static BufferedImage asAppleArtwork(BufferedImage cover) {
        BufferedImage small = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = small.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.drawImage(cover, 0, 0, 100, 100, null);
        graphics.dispose();
        try {
            ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
            ImageIO.write(small, "jpg", jpeg);
            return ImageIO.read(new ByteArrayInputStream(jpeg.toByteArray()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
