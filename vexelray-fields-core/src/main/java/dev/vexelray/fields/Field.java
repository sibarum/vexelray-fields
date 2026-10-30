package dev.vexelray.fields;

import java.util.Arrays;

/**
 * A distance field before it is encoded: per texel, {@link FieldKind#channels} signed distances in texels, positive
 * inside, top row first and channels interleaved. Distances are kept unclamped; {@link Encoding} decides how far a
 * byte reaches.
 */
public final class Field {

    private final FieldKind kind;
    private final int width;
    private final int height;
    private final float[] data;

    public Field(FieldKind kind, int width, int height) {
        this(kind, width, height, new float[width * height * kind.channels]);
    }

    public Field(FieldKind kind, int width, int height, float[] data) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("a field " + width + " × " + height);
        }
        if (data.length != width * height * kind.channels) {
            throw new IllegalArgumentException("a " + kind.key + " field " + width + " × " + height + " is "
                    + width * height * kind.channels + " floats; was given " + data.length);
        }
        this.kind = kind;
        this.width = width;
        this.height = height;
        this.data = data;
    }

    public FieldKind kind() {
        return kind;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int channels() {
        return kind.channels;
    }

    /** The backing array, live: {@code (y * width + x) * channels + c}. */
    public float[] data() {
        return data;
    }

    public float get(int x, int y, int c) {
        return data[(y * width + x) * kind.channels + c];
    }

    public void set(int x, int y, int c, float v) {
        data[(y * width + x) * kind.channels + c] = v;
    }

    /**
     * The distance at texel {@code (x, y)} as a shader reads it: the one channel of an SDF, the median of the first
     * three otherwise.
     */
    public float distance(int x, int y) {
        int i = (y * width + x) * kind.channels;
        return kind == FieldKind.SDF ? data[i] : median(data[i], data[i + 1], data[i + 2]);
    }

    /**
     * {@link #distance} between texel centres, as a GPU's bilinear filter and the median after it would see it:
     * channels interpolated first, the median taken last. {@code (u, v)} in texel coordinates, clamped at the edge.
     */
    public float sample(double u, double v) {
        double fx = u - 0.5, fy = v - 0.5;
        int x0 = (int) Math.floor(fx), y0 = (int) Math.floor(fy);
        float ax = (float) (fx - x0), ay = (float) (fy - y0);
        int n = kind.channels;
        float[] c = new float[Math.min(n, 3)];
        for (int k = 0; k < c.length; k++) {
            float a = at(x0, y0, k), b = at(x0 + 1, y0, k);
            float d = at(x0, y0 + 1, k), e = at(x0 + 1, y0 + 1, k);
            c[k] = (a + (b - a) * ax) * (1 - ay) + (d + (e - d) * ax) * ay;
        }
        return c.length == 1 ? c[0] : median(c[0], c[1], c[2]);
    }

    /**
     * {@link #sample} anywhere, past the field's edge too, as a shader would extrapolate a field whose texture stops
     * short of what an effect needs. Within the field it is {@link #sample}. Beyond it, the shape is all on the
     * field's side: one texel in from the nearest edge point, the gradient is followed uphill to the shape's nearest
     * point there, and the distance is to that point. That point is on the shape, so the distance is never too
     * small. Nor is it let below {@code √(out² + d²)}, with {@code out} the way past the edge and {@code d} the
     * distance at the edge point, which the true distance never is: the field is a rectangle, and every point of it
     * is at least that far.
     *
     * <p>It is exact straight out from a straight edge, and off by a share of the distance that falls as it grows:
     * about 2% for a star 96 texels out. What it needs is that the edge texels hold true distances, not ones an
     * encoding has clamped, and are outside the shape. An MTSDF's true distance, its fourth channel, is the one
     * followed; an MSDF's median strays far from its edges, and makes a poor gradient.
     */
    public float extended(double u, double v) {
        double cu = Math.clamp(u, 0.5, width - 0.5), cv = Math.clamp(v, 0.5, height - 0.5);
        double out = Math.hypot(u - cu, v - cv);
        if (out == 0) {
            return sample(u, v);
        }
        double edge = trueSample(cu, cv);
        double floor = Math.hypot(out, edge);
        double gu = Math.clamp(cu, Math.min(1.5, width / 2.0), Math.max(width - 1.5, width / 2.0));
        double gv = Math.clamp(cv, Math.min(1.5, height / 2.0), Math.max(height - 1.5, height / 2.0));
        double d = trueSample(gu, gv);
        double gx = trueSample(gu + 0.5, gv) - trueSample(gu - 0.5, gv);
        double gy = trueSample(gu, gv + 0.5) - trueSample(gu, gv - 0.5);
        double len = Math.hypot(gx, gy);
        if (!(len > 1e-6)) {
            return (float) -floor;
        }
        double sx = gu - gx / len * d, sy = gv - gy / len * d;
        return (float) -Math.max(Math.hypot(u - sx, v - sy), floor);
    }

    /** {@link #sample}, but an MTSDF's fourth channel: the true distance. */
    private double trueSample(double u, double v) {
        if (kind != FieldKind.MTSDF) {
            return sample(u, v);
        }
        double fx = u - 0.5, fy = v - 0.5;
        int x0 = (int) Math.floor(fx), y0 = (int) Math.floor(fy);
        double ax = fx - x0, ay = fy - y0;
        double a = at(x0, y0, 3), b = at(x0 + 1, y0, 3), c = at(x0, y0 + 1, 3), e = at(x0 + 1, y0 + 1, 3);
        return (a + (b - a) * ax) * (1 - ay) + (c + (e - c) * ax) * ay;
    }

    private float at(int x, int y, int c) {
        return get(Math.clamp(x, 0, width - 1), Math.clamp(y, 0, height - 1), c);
    }

    public Field copy() {
        return new Field(kind, width, height, Arrays.copyOf(data, data.length));
    }

    public static float median(float a, float b, float c) {
        return Math.max(Math.min(a, b), Math.min(Math.max(a, b), c));
    }
}
