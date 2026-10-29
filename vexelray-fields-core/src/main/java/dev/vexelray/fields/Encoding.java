package dev.vexelray.fields;

/**
 * A field as bytes. A distance {@code d} in texels is stored as {@code 0.5 + d / range}, clamped to {@code 0..1}: the
 * edge at one half, {@code range / 2} texels each way the farthest a byte tells apart. That is
 * {@code vexelray-canvas}'s {@code DistanceField} layout with {@code range = 2 * spread}, and msdfgen's with its
 * {@code pxRange}.
 */
public final class Encoding {

    private Encoding() {
    }

    /** The field's own channels, one byte each, top row first. */
    public static byte[] packed(Field f, double range) {
        float[] d = f.data();
        byte[] out = new byte[d.length];
        for (int i = 0; i < d.length; i++) {
            out[i] = encode(d[i], range);
        }
        return out;
    }

    /**
     * RGBA8, what a GPU uploads and the canvas draws: an SDF's distance in every colour channel, an MSDF's three in
     * red, green and blue, an MTSDF's four in all four; alpha opaque where the field has none.
     */
    public static byte[] rgba(Field f, double range) {
        int n = f.width() * f.height();
        int ch = f.channels();
        float[] d = f.data();
        byte[] out = new byte[n * 4];
        for (int i = 0; i < n; i++) {
            for (int c = 0; c < 3; c++) {
                out[i * 4 + c] = encode(d[i * ch + (ch == 1 ? 0 : c)], range);
            }
            out[i * 4 + 3] = ch == 4 ? encode(d[i * ch + 3], range) : (byte) 255;
        }
        return out;
    }

    /** One distance as a byte. */
    public static byte encode(double d, double range) {
        return (byte) Math.round(Math.clamp(0.5 + d / range, 0, 1) * 255);
    }

    /** A byte back as a distance in texels: within {@code range / 510} of what was encoded, if it was in range. */
    public static float decode(byte b, double range) {
        return (float) (((b & 0xFF) / 255.0 - 0.5) * range);
    }

    /** {@link #packed} bytes back as a field. */
    public static Field unpack(FieldKind kind, int width, int height, byte[] bytes, double range) {
        float[] d = new float[bytes.length];
        for (int i = 0; i < d.length; i++) {
            d[i] = decode(bytes[i], range);
        }
        return new Field(kind, width, height, d);
    }
}
