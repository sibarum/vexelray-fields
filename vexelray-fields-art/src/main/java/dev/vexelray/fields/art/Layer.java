package dev.vexelray.fields.art;

/**
 * Pixels being drawn: premultiplied RGBA in floats, transparent to begin with. A group with an opacity, a mask or a
 * clip is drawn into a layer of its own, which then goes over its parent's.
 */
final class Layer {

    final int width;
    final int height;
    final float[] px;

    Layer(int width, int height) {
        this.width = width;
        this.height = height;
        this.px = new float[width * height * 4];
    }

    /**
     * {@code shader}'s colour at each pixel's centre, at {@code opacity}, over every pixel {@code cover} has. A null
     * shader paints nothing.
     */
    void paint(Cover cover, Gradient.Shader shader, float opacity) {
        if (shader == null || opacity <= 0) {
            return;
        }
        float[] c = new float[4];
        for (int y = cover.y0; y < cover.y1; y++) {
            for (int x = cover.x0; x < cover.x1; x++) {
                int i = y * width + x;
                if (cover.bits[i]) {
                    shader.at(x + 0.5, y + 0.5, c);
                    int o = i * 4;
                    float a = c[3] * opacity;
                    float keep = 1 - a;
                    px[o] = c[0] * opacity + px[o] * keep;
                    px[o + 1] = c[1] * opacity + px[o + 1] * keep;
                    px[o + 2] = c[2] * opacity + px[o + 2] * keep;
                    px[o + 3] = a + px[o + 3] * keep;
                }
            }
        }
    }

    /** {@code src} over this, at {@code opacity}. */
    void over(Layer src, float opacity) {
        for (int i = 0; i < px.length; i += 4) {
            float sa = src.px[i + 3] * opacity;
            if (sa <= 0) {
                continue;
            }
            float keep = 1 - sa;
            px[i] = src.px[i] * opacity + px[i] * keep;
            px[i + 1] = src.px[i + 1] * opacity + px[i + 1] * keep;
            px[i + 2] = src.px[i + 2] * opacity + px[i + 2] * keep;
            px[i + 3] = sa + px[i + 3] * keep;
        }
    }

    /**
     * This seen through {@code mask}, as SVG masks: each pixel kept by the mask's luminance there, times its alpha
     * (which premultiplied colour already is). Outside {@code [x0, x1) × [y0, y1)}, the mask's region, nothing is kept.
     */
    void maskBy(Layer mask, int x0, int y0, int x1, int y1) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int o = (y * width + x) * 4;
                float k = x < x0 || x >= x1 || y < y0 || y >= y1 ? 0
                        : 0.2125f * mask.px[o] + 0.7154f * mask.px[o + 1] + 0.0721f * mask.px[o + 2];
                px[o] *= k;
                px[o + 1] *= k;
                px[o + 2] *= k;
                px[o + 3] *= k;
            }
        }
    }

    /** This with every pixel {@code clip} does not have made transparent. */
    void clipBy(boolean[] clip) {
        for (int i = 0; i < clip.length; i++) {
            if (!clip[i]) {
                int o = i * 4;
                px[o] = px[o + 1] = px[o + 2] = px[o + 3] = 0;
            }
        }
    }

    /** Straight RGBA8, as an image holds it. */
    Raster raster() {
        byte[] rgba = new byte[width * height * 4];
        for (int i = 0; i < rgba.length; i += 4) {
            float a = px[i + 3];
            float inv = a > 0 ? 1 / a : 0;
            rgba[i] = byte8(px[i] * inv);
            rgba[i + 1] = byte8(px[i + 1] * inv);
            rgba[i + 2] = byte8(px[i + 2] * inv);
            rgba[i + 3] = byte8(a);
        }
        return new Raster(width, height, rgba);
    }

    private static byte byte8(float v) {
        return (byte) Math.round(Math.clamp(v, 0f, 1f) * 255f);
    }
}
