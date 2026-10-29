package dev.vexelray.fields;

/**
 * What a field holds per texel. The keys are {@code vexelray-codec-asset}'s {@code DistanceField} kinds, so a field
 * made here is stored there under its own name.
 */
public enum FieldKind {

    /** One true signed distance. Corners round off once drawn well above the field's own size. */
    SDF("sdf", 1),
    /** Three signed pseudo-distances whose median is the edge: corners stay sharp at any magnification. */
    MSDF("msdf", 3),
    /** {@link #MSDF}'s three, and a true distance in a fourth for what wants one: a glow, a shadow, an outline. */
    MTSDF("mtsdf", 4);

    /** The name an asset file records. */
    public final String key;
    /** Floats per texel. */
    public final int channels;

    FieldKind(String key, int channels) {
        this.key = key;
        this.channels = channels;
    }

    /** The kind named {@code key}. */
    public static FieldKind of(String key) {
        for (FieldKind k : values()) {
            if (k.key.equals(key)) {
                return k;
            }
        }
        throw new IllegalArgumentException("no field kind '" + key + "'");
    }
}
