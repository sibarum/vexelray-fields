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
