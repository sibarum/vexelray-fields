package dev.vexelray.fields.art;

/**
 * Which part of a drawing is the shape: how much of each pixel is shape, from {@code 0} to {@code 1}. The shape is
 * where that is at least one half.
 */
public enum Channel {

    /** {@link #ALPHA} if any pixel is less than opaque, else {@link #LUMINANCE}: a mask by its transparency, a
     *  black-and-white drawing by its dark. What an SVG is read by, its background being transparent. */
    AUTO,
    /** Where the drawing is opaque, whatever its colour. */
    ALPHA,
    /** Where the drawing is dark, laid over white first, so a transparent background reads as background. */
    LUMINANCE,
    /** Where red is high. Alpha is ignored: a channel of a texture is often not colour at all. */
    RED,
    /** Where green is high. Alpha is ignored. */
    GREEN,
    /** Where blue is high. Alpha is ignored. */
    BLUE;

    /** Below this alpha somewhere, {@link #AUTO} takes a raster as a mask with transparency rather than black on white. */
    static final int OPAQUE_ENOUGH = 250;

    /** The shape's share of the pixel at {@code i} of straight RGBA8 {@code rgba}. Not for {@link #AUTO}. */
    float share(byte[] rgba, int i) {
        int o = i * 4;
        return switch (this) {
            case ALPHA -> (rgba[o + 3] & 0xFF) / 255f;
            case LUMINANCE -> {
                float a = (rgba[o + 3] & 0xFF) / 255f;
                float luma = (0.2126f * (rgba[o] & 0xFF) + 0.7152f * (rgba[o + 1] & 0xFF)
                        + 0.0722f * (rgba[o + 2] & 0xFF)) / 255f;
                yield 1f - (a * luma + (1f - a));
            }
            case RED -> (rgba[o] & 0xFF) / 255f;
            case GREEN -> (rgba[o + 1] & 0xFF) / 255f;
            case BLUE -> (rgba[o + 2] & 0xFF) / 255f;
            case AUTO -> throw new IllegalStateException("AUTO is resolved against a raster first");
        };
    }

    /** {@link #AUTO} as it resolves for {@code rgba}; any other channel as it is. */
    Channel resolve(byte[] rgba) {
        if (this != AUTO) {
            return this;
        }
        for (int i = 3; i < rgba.length; i += 4) {
            if ((rgba[i] & 0xFF) < OPAQUE_ENOUGH) {
                return ALPHA;
            }
        }
        return LUMINANCE;
    }
}
