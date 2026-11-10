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
        draw(sdg, sdg.layers(), px, width, height, originX, originY, unitsPerPixel);
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

    /** {@code layers}, back to front, over {@code px}: premultiplied, {@code width × height}. */
    private static void draw(Sdg sdg, java.util.List<Sdg.Layer> layers, float[] px, int width, int height,
                             double originX, double originY, double unitsPerPixel) {
        for (Sdg.Layer layer : layers) {
            if (layer.isGroup()) {
                group(sdg, layer, px, width, height, originX, originY, unitsPerPixel);
            } else if (layer.isText()) {
                text(sdg, layer, px, width, height, originX, originY, unitsPerPixel);
            } else {
                shape(sdg, layer, px, width, height, originX, originY, unitsPerPixel);
            }
        }
    }

    /**
     * A group (spec §2, since 1.2): its layers drawn apart into {@code g}, its mask's into {@code m}; then per pixel
     * {@code g · (opacity · k · c)} over {@code px}, {@code k} the mask's luminance (the luma of its premultiplied colour,
     * which is the luma of its colour times its alpha) or its alpha, and {@code c} the clip's coverage.
     */
    private static void group(Sdg sdg, Sdg.Layer layer, float[] px, int width, int height, double originX,
                              double originY, double unitsPerPixel) {
        Sdg.Group grp = layer.group();
        if (layer.opacity() <= 0 || grp.mask() != null && !grp.mask().known()) {
            return;   // nothing to see; a mask of a kind this reader does not know hides its group, as an unknown layer is skipped
        }
        float[] g = new float[px.length];
        draw(sdg, grp.layers(), g, width, height, originX, originY, unitsPerPixel);
        float[] m = null;
        if (grp.mask() != null) {
            m = new float[px.length];
            draw(sdg, grp.mask().layers(), m, width, height, originX, originY, unitsPerPixel);
        }
        boolean luminance = grp.mask() != null && grp.mask().kind().equals(Sdg.Mask.LUMINANCE);
        Sdg.Stencil clip = grp.clip() != null && grp.clip().known() ? grp.clip() : null;
        Sdg.Page clipPage = clip == null ? null : sdg.pages().get(clip.page());
        for (int j = 0; j < height; j++) {
            double cy = originY + (j + 0.5) * unitsPerPixel;
            for (int i = 0; i < width; i++) {
                int o = (j * width + i) * 4;
                if (g[o + 3] <= 0) {
                    continue;
                }
                float f = layer.opacity();
                if (m != null) {
                    f *= luminance ? 0.2126f * m[o] + 0.7152f * m[o + 1] + 0.0722f * m[o + 2] : m[o + 3];
                }
                if (clip != null) {
                    double cx = originX + (i + 0.5) * unitsPerPixel;
                    double d = distance(clip, clipPage, cx, cy);
                    f *= (float) Math.clamp(0.5 + d / clip.scale() / unitsPerPixel, 0, 1);
                }
                if (f <= 0) {
                    continue;
                }
                float keep = 1 - g[o + 3] * f;
                for (int c = 0; c < 4; c++) {
                    px[o + c] = g[o + c] * f + px[o + c] * keep;
                }
            }
        }
    }

    /** A shape: its stencil's coverage, times its paint's alpha and its opacity, of its paint's colour, over {@code px}. */
    private static void shape(Sdg sdg, Sdg.Layer layer, float[] px, int width, int height, double originX,
                              double originY, double unitsPerPixel) {
        float[] colour = new float[4];
        {
            if (!layer.known() || layer.opacity() <= 0) {
                return;
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
    }

    /**
     * Text (spec §2, since 1.3): each glyph's coverage as a stencil's, placed by {@link Sdg.Glyph#placed}; the layer's
     * coverage at a pixel the greatest of them, so glyphs that overlap cover it once; then painted as a shape is.
     */
    private static void text(Sdg sdg, Sdg.Layer layer, float[] px, int width, int height, double originX,
                             double originY, double unitsPerPixel) {
        if (layer.opacity() <= 0 || layer.paint() instanceof Sdg.Paint.Unknown) {
            return;
        }
        Sdg.Text t = layer.text();
        Sdg.Font font = sdg.fonts().get(t.font());
        float[] cover = new float[width * height];
        int bi0 = width, bj0 = height, bi1 = 0, bj1 = 0;   // the pixels any glyph covers
        for (Sdg.Placed p : t.glyphs()) {
            Sdg.Glyph glyph = font.glyphs().get(p.glyph());
            if (glyph.field() == null || !glyph.field().known()) {
                continue;   // nothing to draw, or a kind this reader does not know: that glyph is left out
            }
            Sdg.Stencil s = glyph.placed(p.x(), p.y(), t.size());
            Sdg.Page page = sdg.pages().get(s.page());
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
                    float c = (float) Math.clamp(0.5 + d / s.scale() / unitsPerPixel, 0, 1);
                    int o = j * width + i;
                    if (c > cover[o]) {
                        cover[o] = c;
                    }
                }
            }
            if (i0 < i1 && j0 < j1) {
                bi0 = Math.min(bi0, i0);
                bj0 = Math.min(bj0, j0);
                bi1 = Math.max(bi1, i1);
                bj1 = Math.max(bj1, j1);
            }
        }
        float[] colour = new float[4];
        for (int j = bj0; j < bj1; j++) {
            double cy = originY + (j + 0.5) * unitsPerPixel;
            for (int i = bi0; i < bi1; i++) {
                float c = cover[j * width + i];
                if (c <= 0) {
                    continue;
                }
                double cx = originX + (i + 0.5) * unitsPerPixel;
                paint(sdg, layer.paint(), cx, cy, colour);
                float a = c * colour[3] * layer.opacity();
                float keep = 1 - a;
                int o = (j * width + i) * 4;
                px[o] = colour[0] * a + px[o] * keep;
                px[o + 1] = colour[1] * a + px[o + 1] * keep;
                px[o + 2] = colour[2] * a + px[o + 2] * keep;
                px[o + 3] = a + px[o + 3] * keep;
            }
        }
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
