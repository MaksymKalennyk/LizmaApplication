package com.example.lizma.service;

import com.example.lizma.exception.InvalidActionException;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.function.BiFunction;

/**
 * Turns an uploaded picture into the one the server keeps: a profile photo (the centre square, 512×512) or a
 * screenshot for a report or feedback (whole, at most 2000 px on its long side). Both are re-encoded as JPEG,
 * which drops every bit of metadata (EXIF, GPS position, camera): only the decoded pixels are kept.
 */
final class UploadedImages {

    static final int AVATAR_SIZE = 512;
    /** Enough to read any text on a phone screenshot. */
    static final int SCREENSHOT_MAX_SIDE = 2000;
    static final int MAX_UPLOAD_BYTES = 10 * 1024 * 1024;
    private static final int MIN_SIDE = 64;
    /** Refused before decoding: a small file can claim huge dimensions and exhaust memory. */
    private static final long MAX_PIXELS = 50_000_000L;

    private UploadedImages() {
    }

    /** What part of the picture to keep and how big the result is. */
    private record Plan(Rectangle region, int width, int height) {
    }

    /** The profile photo: the centre square, 512×512. */
    static byte[] avatar(byte[] upload) {
        return convert(upload, 0.85f, (width, height) -> {
            int side = Math.min(width, height);
            return new Plan(new Rectangle((width - side) / 2, (height - side) / 2, side, side), AVATAR_SIZE, AVATAR_SIZE);
        });
    }

    /** A screenshot: the whole picture in its own proportions, made smaller only when it's bigger than needed. */
    static byte[] screenshot(byte[] upload) {
        return convert(upload, 0.8f, (width, height) -> {
            double scale = Math.min(1.0, (double) SCREENSHOT_MAX_SIDE / Math.max(width, height));
            return new Plan(new Rectangle(0, 0, width, height),
                    Math.max(1, (int) Math.round(width * scale)), Math.max(1, (int) Math.round(height * scale)));
        });
    }

    private static byte[] convert(byte[] upload, float quality, BiFunction<Integer, Integer, Plan> planner) {
        if (upload == null || upload.length == 0) {
            throw new InvalidActionException("Choose a photo");
        }
        if (upload.length > MAX_UPLOAD_BYTES) {
            throw new InvalidActionException("The photo is too large");
        }
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(upload))) {
            Iterator<ImageReader> readers = in == null ? null : ImageIO.getImageReaders(in);
            if (readers == null || !readers.hasNext()) {
                throw new InvalidActionException("This photo can't be read. Use a JPEG or PNG.");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < MIN_SIDE || height < MIN_SIDE) {
                    throw new InvalidActionException("The photo is too small");
                }
                if ((long) width * height > MAX_PIXELS) {
                    throw new InvalidActionException("The photo is too large");
                }
                Plan plan = planner.apply(width, height);
                ImageReadParam param = reader.getDefaultReadParam();
                param.setSourceRegion(plan.region());
                // Read big pictures at a fraction of their size; the last step down is done smoothly below.
                int step = Math.max(1, Math.min(plan.region().width / (plan.width() * 2), plan.region().height / (plan.height() * 2)));
                param.setSourceSubsampling(step, step, 0, 0);
                return encode(scale(reader.read(0, param), plan.width(), plan.height()), quality);
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof InvalidActionException invalid) {
                throw invalid;
            }
            throw new InvalidActionException("This photo can't be read. Use a JPEG or PNG.");
        }
    }

    private static BufferedImage scale(BufferedImage source, int width, int height) {
        BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = target.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            // Transparent parts of a PNG become white instead of black.
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);
            g.drawImage(source, 0, 0, width, height, null);
        } finally {
            g.dispose();
        }
        return target;
    }

    private static byte[] encode(BufferedImage image, float quality) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ImageOutputStream out = ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(out);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }
}
