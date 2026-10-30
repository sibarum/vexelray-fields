package dev.vexelray.fields.art;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A raster image, straight (not premultiplied) RGBA8, top row first: a drawing in pixels, or an SVG once drawn.
 * {@link #mask} reads a shape from it by one {@link Channel} and fits it onto a grid of fine pixels.
 *
 * @param width  pixels across
 * @param height pixels down
 * @param rgba   {@code width * height * 4} bytes
 */
public record Raster(int width, int height, byte[] rgba) {

    /** What lies past a raster's edge once it is fitted: background, whether or not the shape is inverted. */
    private static final float OUTSIDE = 0f;

    public Raster {
        if (width <= 0 || height <= 0 || rgba.length != width * height * 4) {
            throw new IllegalArgumentException("a raster " + width + " × " + height + " is " + width * height * 4
                    + " bytes; was given " + rgba.length);
        }
    }

    /**
     * The image at {@code file}, by the JDK's ImageIO: PNG, JPEG, BMP and GIF (its first frame), and whatever else a
     * plugin on the class path adds.
     */
    public static Raster read(Path file) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(Files.readAllBytes(file)));
        if (image == null) {
            throw new IOException("not an image ImageIO reads (PNG, JPEG, BMP, GIF): " + file);
        }
        return of(image);
    }

    public static Raster of(BufferedImage image) {
        int w = image.getWidth(), h = image.getHeight();
        int[] argb = image.getRGB(0, 0, w, h, null, 0, w);
        byte[] rgba = new byte[w * h * 4];
        for (int i = 0; i < argb.length; i++) {
            int p = argb[i];
            rgba[i * 4] = (byte) (p >> 16);
            rgba[i * 4 + 1] = (byte) (p >> 8);
            rgba[i * 4 + 2] = (byte) p;
            rgba[i * 4 + 3] = (byte) (p >>> 24);
        }
        return new Raster(w, h, rgba);
    }

    /** Each pixel's share of shape by {@code channel}, {@code 0..1}, turned over if {@code invert}. */
    public float[] shares(Channel channel, boolean invert) {
        Channel c = channel.resolve(rgba);
        float[] share = new float[width * height];
        for (int i = 0; i < share.length; i++) {
            float s = c.share(rgba, i);
            share[i] = invert ? 1f - s : s;
        }
        return share;
    }

    /**
     * The shape by {@code channel} as a mask {@code maskWidth × maskHeight}: the raster fitted in with its aspect kept
     * and centred, read between its pixels bilinearly, and cut at one half. A 32 px drawing on a mask of 512 is a
     * smooth-edged shape rather than its pixel staircase. Past the raster's edge is outside, inverted or not.
     */
    public boolean[] mask(Channel channel, boolean invert, int maskWidth, int maskHeight) {
        float[] share = shares(channel, invert);
        boolean[] mask = new boolean[maskWidth * maskHeight];
        if (maskWidth == width && maskHeight == height) {
            for (int i = 0; i < mask.length; i++) {
                mask[i] = share[i] >= 0.5f;
            }
            return mask;
        }
        float scale = Math.min((float) maskWidth / width, (float) maskHeight / height);
        float offX = (maskWidth - width * scale) / 2f;
        float offY = (maskHeight - height * scale) / 2f;
        for (int my = 0; my < maskHeight; my++) {
            float sy = (my + 0.5f - offY) / scale - 0.5f;
            for (int mx = 0; mx < maskWidth; mx++) {
                float sx = (mx + 0.5f - offX) / scale - 0.5f;
                mask[my * maskWidth + mx] = sample(share, sx, sy) >= 0.5f;
            }
        }
        return mask;
    }

    /** {@code share} between its pixels at {@code (x, y)}, edge pixels held out to half a pixel, nothing beyond. */
    private float sample(float[] share, float x, float y) {
        if (x < -0.5f || y < -0.5f || x > width - 0.5f || y > height - 0.5f) {
            return OUTSIDE;
        }
        int x0 = (int) Math.floor(x);
        int y0 = (int) Math.floor(y);
        float tx = x - x0;
        float ty = y - y0;
        float a = at(share, x0, y0);
        float b = at(share, x0 + 1, y0);
        float c = at(share, x0, y0 + 1);
        float d = at(share, x0 + 1, y0 + 1);
        return (a * (1 - tx) + b * tx) * (1 - ty) + (c * (1 - tx) + d * tx) * ty;
    }

    private float at(float[] share, int x, int y) {
        return share[Math.clamp(y, 0, height - 1) * width + Math.clamp(x, 0, width - 1)];
    }
}
