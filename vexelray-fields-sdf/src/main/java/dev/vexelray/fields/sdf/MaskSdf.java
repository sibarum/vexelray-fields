package dev.vexelray.fields.sdf;

import dev.vexelray.fields.Field;
import dev.vexelray.fields.FieldKind;

/**
 * A signed distance field from a raster mask, for shapes that come as pixels: whatever a rasteriser draws (an SVG
 * with arcs, clips and strokes, an icon) comes out as it means it, and nothing here parses a path. The algorithm is
 * {@code vexelray-canvas}'s {@code DistanceField}, for any rectangle and any oversampling: the exact Euclidean
 * distance of every fine pixel to the nearest pixel across the edge, in two separable passes (Felzenszwalb and
 * Huttenlocher), averaged down to the field's texels, which puts the edge within a small fraction of a texel.
 */
public final class MaskSdf {

    private static final float FAR = 1e20f;

    private MaskSdf() {
    }

    /**
     * The field of a mask {@code maskWidth × maskHeight} fine pixels, {@code inside[y * maskWidth + x]} for the
     * shape, {@code oversample} fine pixels to a texel each way, with {@code spread} texels of margin added round
     * it. The field is {@code maskWidth / oversample + 2 * spread} texels wide, and as tall by the same rule.
     */
    public static Field of(boolean[] inside, int maskWidth, int maskHeight, int oversample, int spread) {
        if (oversample <= 0 || spread < 0 || maskWidth % oversample != 0 || maskHeight % oversample != 0) {
            throw new IllegalArgumentException("a mask " + maskWidth + " × " + maskHeight + " in texels of "
                    + oversample + " with " + spread + " round it");
        }
        if (inside.length != maskWidth * maskHeight) {
            throw new IllegalArgumentException("a mask " + maskWidth + " × " + maskHeight + " is "
                    + maskWidth * maskHeight + " pixels; was given " + inside.length);
        }
        int pad = spread * oversample;
        int w = maskWidth + 2 * pad, h = maskHeight + 2 * pad;
        boolean[] grid = new boolean[w * h];
        for (int y = 0; y < maskHeight; y++) {
            System.arraycopy(inside, y * maskWidth, grid, (y + pad) * w + pad, maskWidth);
        }
        float[] fine = signed(grid, w, h);
        int tw = w / oversample, th = h / oversample;
        float[] out = new float[tw * th];
        float norm = 1f / (oversample * oversample * oversample);
        for (int ty = 0; ty < th; ty++) {
            for (int tx = 0; tx < tw; tx++) {
                float sum = 0;
                for (int y = ty * oversample; y < (ty + 1) * oversample; y++) {
                    for (int x = tx * oversample; x < (tx + 1) * oversample; x++) {
                        sum += fine[y * w + x];
                    }
                }
                out[ty * tw + tx] = sum * norm;
            }
        }
        return new Field(FieldKind.SDF, tw, th, out);
    }

    /** {@link #of(boolean[], int, int, int, int)} from straight RGBA8, the shape wherever alpha is at least one half. */
    public static Field ofRgba(byte[] rgba, int maskWidth, int maskHeight, int oversample, int spread) {
        boolean[] inside = new boolean[maskWidth * maskHeight];
        for (int i = 0; i < inside.length; i++) {
            inside[i] = (rgba[i * 4 + 3] & 0xFF) >= 128;
        }
        return of(inside, maskWidth, maskHeight, oversample, spread);
    }

    /**
     * The signed distance of every pixel to the edge of {@code inside}, in pixels: from a pixel's centre to the nearest
     * pixel centre across the edge, less half a pixel, so the edge falls between them. Positive inside.
     */
    static float[] signed(boolean[] inside, int w, int h) {
        float[] toInside = transform(inside, w, h, true);
        float[] toOutside = transform(inside, w, h, false);
        float[] d = new float[w * h];
        for (int i = 0; i < d.length; i++) {
            d[i] = inside[i]
                    ? (float) Math.sqrt(toOutside[i]) - 0.5f
                    : 0.5f - (float) Math.sqrt(toInside[i]);
        }
        return d;
    }

    /** The squared distance of every pixel to the nearest pixel that is {@code inside} as asked, in two passes. */
    private static float[] transform(boolean[] inside, int w, int h, boolean toInside) {
        float[] g = new float[w * h];
        for (int i = 0; i < g.length; i++) {
            g[i] = inside[i] == toInside ? 0f : FAR;
        }
        int n = Math.max(w, h);
        float[] f = new float[n];
        float[] out = new float[n];
        int[] v = new int[n];
        double[] z = new double[n + 1];
        for (int x = 0; x < w; x++) {   // down each column
            for (int y = 0; y < h; y++) {
                f[y] = g[y * w + x];
            }
            line(f, h, out, v, z);
            for (int y = 0; y < h; y++) {
                g[y * w + x] = out[y];
            }
        }
        for (int y = 0; y < h; y++) {   // then across each row
            System.arraycopy(g, y * w, f, 0, w);
            line(f, w, out, v, z);
            System.arraycopy(out, 0, g, y * w, w);
        }
        return g;
    }

    /**
     * The one-dimensional squared distance transform of {@code f}: the lower envelope of the parabolas rooted at each
     * {@code (q, f[q])}. In doubles, so a far value's parabola sits harmlessly high rather than losing the near ones.
     */
    private static void line(float[] f, int n, float[] out, int[] v, double[] z) {
        int k = 0;
        v[0] = 0;
        z[0] = Double.NEGATIVE_INFINITY;
        z[1] = Double.POSITIVE_INFINITY;
        for (int q = 1; q < n; q++) {
            double s = cross(f, q, v[k]);
            while (s <= z[k]) {
                k--;
                s = cross(f, q, v[k]);
            }
            k++;
            v[k] = q;
            z[k] = s;
            z[k + 1] = Double.POSITIVE_INFINITY;
        }
        k = 0;
        for (int q = 0; q < n; q++) {
            while (z[k + 1] < q) {
                k++;
            }
            double d = q - v[k];
            out[q] = (float) Math.min(FAR, d * d + f[v[k]]);
        }
    }

    /** Where the parabolas rooted at {@code q} and {@code p} cross. */
    private static double cross(float[] f, int q, int p) {
        return ((f[q] + (double) q * q) - (f[p] + (double) p * p)) / (2.0 * q - 2.0 * p);
    }
}
