package dev.vexelray.fields.art;

import dev.vexelray.fields.Field;
import dev.vexelray.fields.FieldKind;
import dev.vexelray.fields.Framing;
import dev.vexelray.fields.msdf.Msdf;
import dev.vexelray.fields.sdf.MaskSdf;
import dev.vexelray.fields.sdf.ShapeSdf;
import dev.vexelray.fields.shape.Shape;

import java.util.Optional;

/**
 * Art made into a field of any kind: an SVG, a black-and-white raster, or one channel of a raster. Laid out as
 * {@code vexelray-canvas}'s {@code DistanceField} and the icon atlases are: the art fitted into {@code width × height}
 * texels with its aspect kept and centred, and {@code spread} texels of margin round that, for the distance to run on
 * into, so a field is {@code width + 2·spread} by {@code height + 2·spread}.
 *
 * <p>Two routes, by what the art has:
 *
 * <ul>
 *   <li><b>Outlines.</b> An SVG of opaque fills alone has its edges read exactly ({@link Svg#outline}), and its SDF
 *       is {@link ShapeSdf}'s, its MSDF and MTSDF {@link Msdf}'s: corners stay sharp under magnification.</li>
 *   <li><b>A mask.</b> Anything else (a raster, or an SVG with strokes, masks, clips or opacity) is a mask drawn at
 *       {@value #OVERSAMPLE} pixels to a texel each way, cut at one half of its {@link Channel}, and made into a
 *       true distance by {@link MaskSdf}, as the icon bake makes it. A mask has no edges to colour, so its MSDF is
 *       that one distance in all three channels, and its MTSDF in all four: the layout a multi-channel reader expects,
 *       with corners as round as an SDF's.</li>
 * </ul>
 */
public final class ArtField {

    /** Mask pixels to a texel each way: {@code vexelray-canvas}'s {@code DistanceField.OVERSAMPLE}. */
    public static final int OVERSAMPLE = 8;

    private ArtField() {
    }

    /** The field of {@code svg} where it is opaque. */
    public static Field of(Svg svg, FieldKind kind, int width, int height, int spread) {
        return of(svg, Channel.ALPHA, false, kind, width, height, spread);
    }

    /**
     * The field of {@code svg}, the shape where {@code channel} says ({@link Channel#AUTO} is {@link Channel#ALPHA} for
     * an SVG, its background being transparent), or where it does not if {@code invert}. By its outlines when it is
     * only opaque fills read by their alpha; otherwise drawn, as a mask.
     */
    public static Field of(Svg svg, Channel channel, boolean invert, FieldKind kind, int width, int height,
                           int spread) {
        check(width, height, spread);
        Channel c = channel == Channel.AUTO ? Channel.ALPHA : channel;
        if (c == Channel.ALPHA && !invert && !svg.stretches()) {
            Optional<Shape> outline = svg.outline();
            if (outline.isPresent() && !outline.get().contours().isEmpty()) {
                Framing framing = framing(svg, width, height, spread);
                int w = width + 2 * spread, h = height + 2 * spread;
                return kind == FieldKind.SDF
                        ? ShapeSdf.generate(outline.get(), framing, w, h)
                        : Msdf.generate(kind, outline.get(), framing, w, h);
            }
        }
        Raster drawn = svg.render(width * OVERSAMPLE, height * OVERSAMPLE);
        return fromMask(drawn.mask(c, invert, drawn.width(), drawn.height()), kind, width, height, spread);
    }

    /** The field of {@code raster}, the shape where {@code channel} says, or where it does not if {@code invert}. */
    public static Field of(Raster raster, Channel channel, boolean invert, FieldKind kind, int width, int height,
                           int spread) {
        check(width, height, spread);
        int fw = width * OVERSAMPLE, fh = height * OVERSAMPLE;
        return fromMask(raster.mask(channel, invert, fw, fh), kind, width, height, spread);
    }

    /**
     * Where {@code svg}'s view box lands in a field {@link #of} makes: shape units to texels, y down. For drawing
     * something else into the same place.
     */
    public static Framing framing(Svg svg, int width, int height, int spread) {
        return Framing.fit(svg.viewBox(), width + 2 * spread, height + 2 * spread, spread, false);
    }

    private static Field fromMask(boolean[] mask, FieldKind kind, int width, int height, int spread) {
        Field sdf = MaskSdf.of(mask, width * OVERSAMPLE, height * OVERSAMPLE, OVERSAMPLE, spread);
        return widen(sdf, kind);
    }

    /** One distance in every channel of {@code kind}: an SDF as a multi-channel field. */
    public static Field widen(Field sdf, FieldKind kind) {
        if (sdf.kind() != FieldKind.SDF) {
            throw new IllegalArgumentException("a " + sdf.kind().key + " field is widened from an SDF only");
        }
        if (kind == FieldKind.SDF) {
            return sdf;
        }
        int n = kind.channels;
        float[] from = sdf.data();
        float[] to = new float[from.length * n];
        for (int i = 0; i < from.length; i++) {
            for (int c = 0; c < n; c++) {
                to[i * n + c] = from[i];
            }
        }
        return new Field(kind, sdf.width(), sdf.height(), to);
    }

    private static void check(int width, int height, int spread) {
        if (width <= 0 || height <= 0 || spread < 0) {
            throw new IllegalArgumentException("a field of " + width + " × " + height + " texels with " + spread
                    + " round it");
        }
    }
}
