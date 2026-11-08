package dev.vexelray.fields.sdg;

import dev.vexelray.fields.Field;
import dev.vexelray.fields.art.Raster;

/**
 * An {@link Sdg} drawn on the CPU, one sample a pixel, exactly as a shader draws it: per layer, the distance read
 * between texels (channels first, then the median), turned from texels to units by the stencil's scale and from units
 * to pixels by the view's, and cut half a pixel either side of the edge; that coverage, times the paint's alpha and the
 * layer's opacity, of the paint's colour, over what is under it. This is the reference a GPU drawing is checked
 * against.
 */
public final class SdgRender {

    private SdgRender() {
    }

    /**
     * The canvas fitted into {@code width × height} pixels, its aspect kept and centred, on transparent; straight
     * RGBA8.
     */
    public static Raster render(Sdg sdg, int width, int height) {
        double unitsPerPixel = Math.max(sdg.width() / width, sdg.height() / height);
        double ox = (sdg.width() - width * unitsPerPixel) / 2, oy = (sdg.height() - height * unitsPerPixel) / 2;
        return render(sdg, width, height, ox, oy, unitsPerPixel);
    }

    /**
     * Any view: pixel {@code (i, j)} is sampled at the canvas point {@code (originX + (i + .5) · unitsPerPixel,
     * originY + (j + .5) · unitsPerPixel)}.
     */
    public static Raster render(Sdg sdg, int width, int height, double originX, double originY,
                                double unitsPerPixel) {
        float[] px = new float[width * height * 4];   // premultiplied
        float[] colour = new float[4];
        for (Sdg.Layer layer : sdg.layers()) {
            if (!layer.known() || layer.opacity() <= 0) {
                continue;
            }
            Sdg.Stencil s = layer.stencil();
            Sdg.Page page = sdg.pages().get(s.page());
            // only the pixels whose centres can fall in the stencil's rectangle
            double cx0 = s.originX(), cy0 = s.originY();
            double cx1 = cx0 + s.width() / s.scale(), cy1 = cy0 + s.height() / s.scale();
            int i0 = Math.max(0, (int) Math.floor((cx0 - originX) / unitsPerPixel - 0.5));
            int j0 = Math.max(0, (int) Math.floor((cy0 - originY) / unitsPerPixel - 0.5));
            int i1 = Math.min(width, (int) Math.ceil((cx1 - originX) / unitsPerPixel + 0.5));
            int j1 = Math.min(height, (int) Math.ceil((cy1 - originY) / unitsPerPixel + 0.5));
            for (int j = j0; j < j1; j++) {
                double cy = originY + (j + 0.5) * unitsPerPixel;
                for (int i = i0; i < i1; i++) {
                    double cx = originX + (i + 0.5) * unitsPerPixel;
                    double d = distance(s, page, cx, cy);
                    float cover = (float) Math.clamp(0.5 + d / s.scale() / unitsPerPixel, 0, 1);
                    if (cover <= 0) {
                        continue;
                    }
                    paint(sdg, layer.paint(), cx, cy, colour);
                    float a = cover * colour[3] * layer.opacity();
                    float keep = 1 - a;
                    int o = (j * width + i) * 4;
                    px[o] = colour[0] * a + px[o] * keep;
                    px[o + 1] = colour[1] * a + px[o + 1] * keep;
                    px[o + 2] = colour[2] * a + px[o + 2] * keep;
                    px[o + 3] = a + px[o + 3] * keep;
                }
            }
        }
        byte[] rgba = new byte[px.length];
        for (int o = 0; o < px.length; o += 4) {
            float a = px[o + 3];
            float inv = a > 0 ? 1 / a : 0;
            rgba[o] = byte8(px[o] * inv);
            rgba[o + 1] = byte8(px[o + 1] * inv);
            rgba[o + 2] = byte8(px[o + 2] * inv);
            rgba[o + 3] = byte8(a);
        }
        return new Raster(width, height, rgba);
    }

    /**
     * The stencil's signed distance at canvas point {@code (cx, cy)}, in its texels; negative infinity past its
     * rectangle.
     */
    public static double distance(Sdg.Stencil s, Sdg.Page page, double cx, double cy) {
        double u = (cx - s.originX()) * s.scale(), v = (cy - s.originY()) * s.scale();
        if (u < 0 || v < 0 || u > s.width() || v > s.height()) {
            return Double.NEGATIVE_INFINITY;
        }
        if (s.kind().equals(Sdg.Stencil.SDF)) {
            return (bilinear(page, s.x(), s.y(), s.width(), s.height(), u, v, s.channel()) - 0.5) * s.range();
        }
        float r = bilinear(page, s.x(), s.y(), s.width(), s.height(), u, v, 0);
        float g = bilinear(page, s.x(), s.y(), s.width(), s.height(), u, v, 1);
        float b = bilinear(page, s.x(), s.y(), s.width(), s.height(), u, v, 2);
        return (Field.median(r, g, b) - 0.5) * s.range();
    }

    /** The paint's straight colour at canvas point {@code (cx, cy)}, {@code r, g, b, a} in {@code 0..1}. */
    public static void paint(Sdg sdg, Sdg.Paint paint, double cx, double cy, float[] out) {
        switch (paint) {
            case Sdg.Paint.Solid s -> {
                int c = s.rgba();
                out[0] = (c >>> 24) / 255f;
                out[1] = (c >>> 16 & 0xFF) / 255f;
                out[2] = (c >>> 8 & 0xFF) / 255f;
                out[3] = (c & 0xFF) / 255f;
            }
            case Sdg.Paint.Sampled s -> {
                Sdg.Page page = sdg.pages().get(s.page());
                double u = (cx - s.originX()) * s.scale(), v = (cy - s.originY()) * s.scale();
                for (int c = 0; c < 4; c++) {
                    out[c] = bilinear(page, s.x(), s.y(), s.width(), s.height(), u, v, c);
                }
            }
            case Sdg.Paint.Unknown u -> throw new IllegalArgumentException("a paint of kind " + u.kind());
        }
    }

    /**
     * Channel {@code c} of the rectangle at {@code (u, v)} in its texels, {@code 0..1}, read between texel centres and
     * clamped half a texel in from its edge, so nothing beyond the rectangle is ever read: as a shader clamps its
     * coordinates before a linear sample.
     */
    static float bilinear(Sdg.Page page, int x, int y, int w, int h, double u, double v, int c) {
        double fx = Math.clamp(u, 0.5, w - 0.5) - 0.5, fy = Math.clamp(v, 0.5, h - 0.5) - 0.5;
        int x0 = (int) Math.floor(fx), y0 = (int) Math.floor(fy);
        int x1 = Math.min(x0 + 1, w - 1), y1 = Math.min(y0 + 1, h - 1);
        float ax = (float) (fx - x0), ay = (float) (fy - y0);
        float a = texel(page, x + x0, y + y0, c), b = texel(page, x + x1, y + y0, c);
        float d = texel(page, x + x0, y + y1, c), e = texel(page, x + x1, y + y1, c);
        return (a + (b - a) * ax) * (1 - ay) + (d + (e - d) * ax) * ay;
    }

    private static float texel(Sdg.Page page, int x, int y, int c) {
        return (page.rgba()[(y * page.width() + x) * 4 + c] & 0xFF) / 255f;
    }

    private static byte byte8(float v) {
        return (byte) Math.round(Math.clamp(v, 0f, 1f) * 255f);
    }
}
