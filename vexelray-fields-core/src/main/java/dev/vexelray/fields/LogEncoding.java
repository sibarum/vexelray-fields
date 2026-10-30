package dev.vexelray.fields;

/**
 * A field as bytes in log space: experimental. A distance {@code d} in texels is stored as
 * {@code 0.5 + 0.5 * asinh(d / knee) / asinh(reach / knee)}, clamped to {@code 0..1}. Within the knee that is nearly
 * {@link Encoding}'s straight line, its error growing as the cube of the distance; beyond it, it is a logarithm, so
 * every byte step is the same share of the distance and a byte reaches {@code reach} texels each way however small
 * the knee.
 *
 * <p>The trade: near the edge a step is {@link #edgeRange} {@code / 255} texels, as a linear range of that size would
 * give, while far out it is about {@code |d| * 2 * asinh(reach / knee) / 255}. {@link #matching} picks the knee that
 * keeps the edge as precise as a given linear range.
 *
 * <p>A shader reads it as it reads the linear layout, the filter and the median first, then
 * {@code d = knee * sinh((e - 0.5) * 2 * asinh(reach / knee))}. The curve is odd and rising, so the median commutes with
 * it and the edge stays at one half; what the filter does to the edge between texels is the cube term, small within
 * the knee.
 */
public record LogEncoding(double knee, double reach) {

    public LogEncoding {
        if (!(knee > 0) || !(reach > 0)) {
            throw new IllegalArgumentException("a log encoding needs a knee and a reach above 0; was " + knee
                    + " and " + reach);
        }
    }

    /**
     * The encoding as precise at the edge as {@link Encoding}'s at {@code range}, reaching {@code reach} texels: its
     * knee is where {@code 2 * knee * asinh(reach / knee)} is {@code range}. The range must be less than
     * {@code 2 * reach}, the linear encoding's own reach.
     */
    public static LogEncoding matching(double range, double reach) {
        if (!(range > 0) || !(range < 2 * reach)) {
            throw new IllegalArgumentException("a linear range of " + range + " already reaches " + range / 2
                    + " texels; asked for " + reach);
        }
        // 2k asinh(R/k) rises with k, from 0 towards 2R
        double lo = 0, hi = reach;
        while (2 * hi * asinh(reach / hi) < range) {
            hi *= 2;
        }
        for (int i = 0; i < 100; i++) {
            double k = (lo + hi) / 2;
            if (2 * k * asinh(reach / k) < range) {
                lo = k;
            } else {
                hi = k;
            }
        }
        return new LogEncoding((lo + hi) / 2, reach);
    }

    /** The linear range a byte step at the edge matches: {@code 2 * knee * asinh(reach / knee)}. */
    public double edgeRange() {
        return 2 * knee * asinh(reach / knee);
    }

    /** Texels per byte step at distance {@code d}. */
    public double step(double d) {
        return Math.hypot(knee, d) * 2 * asinh(reach / knee) / 255;
    }

    /** {@code d} on the curve, unclamped and unquantised: one half at the edge, 0 and 1 at the reach. */
    public double value(double d) {
        return 0.5 + 0.5 * asinh(d / knee) / asinh(reach / knee);
    }

    /** {@link #value}'s inverse, for any {@code e}: a filtered value back to a distance. */
    public double distance(double e) {
        return knee * Math.sinh((e - 0.5) * 2 * asinh(reach / knee));
    }

    /** One distance as a byte. */
    public byte encode(double d) {
        return (byte) Math.round(Math.clamp(value(d), 0, 1) * 255);
    }

    /** A byte back as a distance in texels: within half a {@link #step} there of what was encoded. */
    public float decode(byte b) {
        return (float) distance((b & 0xFF) / 255.0);
    }

    /** The field's own channels, one byte each, top row first, as {@link Encoding#packed}. */
    public byte[] packed(Field f) {
        float[] d = f.data();
        byte[] out = new byte[d.length];
        for (int i = 0; i < d.length; i++) {
            out[i] = encode(d[i]);
        }
        return out;
    }

    /** RGBA8 as {@link Encoding#rgba} lays it out, every channel on this curve. */
    public byte[] rgba(Field f) {
        int n = f.width() * f.height();
        int ch = f.channels();
        float[] d = f.data();
        byte[] out = new byte[n * 4];
        for (int i = 0; i < n; i++) {
            for (int c = 0; c < 3; c++) {
                out[i * 4 + c] = encode(d[i * ch + (ch == 1 ? 0 : c)]);
            }
            out[i * 4 + 3] = ch == 4 ? encode(d[i * ch + 3]) : (byte) 255;
        }
        return out;
    }

    /** {@link #packed} bytes back as a field. */
    public Field unpack(FieldKind kind, int width, int height, byte[] bytes) {
        float[] d = new float[bytes.length];
        for (int i = 0; i < d.length; i++) {
            d[i] = decode(bytes[i]);
        }
        return new Field(kind, width, height, d);
    }

    private static double asinh(double x) {
        double a = Math.abs(x);
        return Math.copySign(Math.log(a + Math.sqrt(a * a + 1)), x);
    }
}
