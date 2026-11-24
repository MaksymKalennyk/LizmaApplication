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

/**
 * Turns an uploaded photo into the stored profile photo: the centre square, 512×512, re-encoded as JPEG.
 * Re-encoding drops every bit of metadata (EXIF, GPS position, camera), and only the decoded pixels are kept,
 * so whatever else was in the file never reaches other users.
 */
final class AvatarImages {

    static final int SIZE = 512;
    static final int MAX_UPLOAD_BYTES = 10 * 1024 * 1024;
    private static final int MIN_SIDE = 64;
    /** Refused before decoding: a small file can claim huge dimensions and exhaust memory. */
    private static final long MAX_PIXELS = 50_000_000L;

    private AvatarImages() {
    }

    static byte[] toSquareJpeg(byte[] upload) {
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
                int side = Math.min(width, height);
                ImageReadParam param = reader.getDefaultReadParam();
                param.setSourceRegion(new Rectangle((width - side) / 2, (height - side) / 2, side, side));
                // Read big photos at a fraction of their size; the last step down is done smoothly below.
                int step = Math.max(1, side / (SIZE * 2));
                param.setSourceSubsampling(step, step, 0, 0);
                return encode(scale(reader.read(0, param)));
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

    private static BufferedImage scale(BufferedImage source) {
        BufferedImage target = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = target.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            // Transparent parts of a PNG become white instead of black.
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, SIZE, SIZE);
            g.drawImage(source, 0, 0, SIZE, SIZE, null);
        } finally {
            g.dispose();
        }
        return target;
    }

    private static byte[] encode(BufferedImage image) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ImageOutputStream out = ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(out);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(0.85f);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }
}
