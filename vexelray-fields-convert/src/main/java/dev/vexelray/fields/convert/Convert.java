package dev.vexelray.fields.convert;

import dev.vexelray.fields.Coverage;
import dev.vexelray.fields.Encoding;
import dev.vexelray.fields.Field;
import dev.vexelray.fields.FieldKind;
import dev.vexelray.fields.Framing;
import dev.vexelray.fields.art.ArtField;
import dev.vexelray.fields.art.Channel;
import dev.vexelray.fields.art.Raster;
import dev.vexelray.fields.art.Svg;
import dev.vexelray.fields.atlas.ArtPair;
import dev.vexelray.fields.msdf.Msdf;
import dev.vexelray.fields.sdf.MaskSdf;
import dev.vexelray.fields.sdf.ShapeSdf;
import dev.vexelray.fields.sdg.Sdg;
import dev.vexelray.fields.sdg.SdgRender;
import dev.vexelray.fields.sdg.SvgToSdg;
import dev.vexelray.fields.sdg.Trace;
import dev.vexelray.fields.shape.Shape;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Every conversion in {@code docs/conversions.md}: each edge a factory, each named route a composition of edges that
 * names its hops. What a conversion needs that its source does not hold is an argument here, with no default.
 *
 * <p>Each factory's javadoc is its row: the kinds, the {@link Fidelity}, and what the arguments are for. Each has a row
 * in {@code docs/benchmarks.md} as well, under its {@link Conversion#name()}.
 */
public final class Convert {

    /** The kinds, by the names conversions.md gives them. */
    public static final String SHAPE = "Shape", SVG = "SVG", RASTER = "Raster", MASK = "Mask", SDF = "SDF",
            MSDF = "MSDF", MTSDF = "MTSDF", COVERAGE = "Coverage", SDG = "SDG", ART_PAIR = "Art pair",
            RASTER_SIZES = "Raster sizes", ICON = "Icon", PROBABILITY = "Probability";

    private Convert() {
    }

    // ------------------------------------------------------------------ shapes

    /**
     * Shape → SDF, EXACT: one true distance per texel, {@code width × height} texels, the shape placed by
     * {@code framing}. {@code ShapeSdf.generate}.
     */
    public static Conversion<Shape, Field> shapeToSdf(Framing framing, int width, int height) {
        return Conversion.edge(SHAPE, SDF, Fidelity.EXACT, s -> ShapeSdf.generate(s, framing, width, height));
    }

    /** Shape → MSDF, EXACT: corners sharp at any magnification. {@code Msdf.generate}. */
    public static Conversion<Shape, Field> shapeToMsdf(Framing framing, int width, int height) {
        return Conversion.edge(SHAPE, MSDF, Fidelity.EXACT, s -> Msdf.generate(s, framing, width, height));
    }

    /** Shape → MTSDF, EXACT: an MSDF and the true distance. {@code Msdf.generateMtsdf}. */
    public static Conversion<Shape, Field> shapeToMtsdf(Framing framing, int width, int height) {
        return Conversion.edge(SHAPE, MTSDF, Fidelity.EXACT, s -> Msdf.generateMtsdf(s, framing, width, height));
    }

    /**
     * The framing that puts a shape traced from a mask ({@link #maskToShape}), which is in the mask's texels, into a
     * field with {@code spread} texels of margin round it: the framing {@link #maskToSdf} uses, so the two routes from
     * a mask land on the same texels.
     */
    public static Framing margin(int spread) {
        return new Framing(1, -spread, -spread, false);
    }

    // ------------------------------------------------------------------ SVG

    /**
     * SVG → SDG, EXACT: a layer per fill and per stroke, {@code texels} across its longer side. Refuses, with the
     * reason, a group's opacity, mask or clip, which composite several parts at once. {@code SvgToSdg.convert}.
     */
    public static Conversion<Svg, Sdg> svgToSdg(int texels) {
        return Conversion.edge(SVG, SDG, Fidelity.EXACT,
                svg -> SvgToSdg.convert(svg, SvgToSdg.Options.across(svg, texels)));
    }

    /**
     * SVG → Shape, LOSSY (paints): the filled area in the view box's units. Only a drawing of opaque nonzero fills that
     * neither overlap nor touch has one ({@code Svg.outline}); any other is refused, and goes SVG → SDG → Mask → Shape.
     */
    public static Conversion<Svg, Shape> svgToShape() {
        return Conversion.edge(SVG, SHAPE, Fidelity.lossy("paints"), svg -> svg.outline().orElseThrow(
                () -> new IllegalArgumentException("the drawing is more than opaque fills that do not touch; "
                        + "take it SVG → SDG → Mask → Shape")));
    }

    /**
     * SVG → Raster, EXACT: the drawing itself in {@code width × height}, fitted, each pixel the mean of
     * {@value #SVG_SAMPLES} × {@value #SVG_SAMPLES} samples. Any SVG the reader draws, a group's opacity, mask or
     * clip included, which SVG → SDG refuses. {@code Svg.render}.
     */
    public static Conversion<Svg, Raster> svgToRaster(int width, int height) {
        return Conversion.edge(SVG, RASTER, Fidelity.EXACT, svg -> svg.render(width, height, SVG_SAMPLES));
    }

    /** Samples a side per pixel when an SVG is drawn as a raster. */
    public static final int SVG_SAMPLES = 4;

    /**
     * SVG → Raster sizes, EXACT: as {@link #sdgToRasterSizes}, each size drawn from the SVG itself, or from the smallest
     * of {@code small} that covers it: for an icon whose drawing composites (a group's opacity, a clip), which cannot be
     * an SDG.
     */
    public static Conversion<Svg, List<Raster>> svgToRasterSizes(int[] sizes, List<SmallSvg> small) {
        int[] sorted = sizes.clone();
        Arrays.sort(sorted);
        List<SmallSvg> byUpTo = new ArrayList<>(small);
        byUpTo.sort(Comparator.comparingInt(SmallSvg::upTo));
        Fidelity f = small.isEmpty() ? Fidelity.EXACT : Fidelity.synthesising("a drawing per small size");
        return Conversion.edge(SVG, RASTER_SIZES, f, svg -> {
            List<Raster> out = new ArrayList<>();
            for (int size : sorted) {
                Svg from = svg;
                for (SmallSvg sm : byUpTo) {
                    if (size <= sm.upTo()) {
                        from = sm.drawing();
                        break;
                    }
                }
                out.add(from.render(size, size, SVG_SAMPLES));
            }
            return List.copyOf(out);
        });
    }

    /** An SVG redrawn for the smallest sizes: every size up to {@code upTo} is drawn from it. */
    public record SmallSvg(int upTo, Svg drawing) {
    }

    // ------------------------------------------------------------------ rasters and masks

    /**
     * Raster → Mask, SYNTHESISING (a channel and a threshold): inside where {@code channel}, {@code 0..1}, is at least
     * {@code threshold}, or below it with {@code invert}. A pixel a sample, {@code oversample} to a texel. A raster
     * holds colour, not a fill, so which channel is the shape and where it is cut are both supplied; {@code AUTO} is
     * refused, being a guess.
     */
    public static Conversion<Raster, Mask> rasterToMask(Channel channel, float threshold, boolean invert,
                                                        int oversample) {
        if (channel == Channel.AUTO) {
            throw new IllegalArgumentException("a channel chosen for you is a guess: name ALPHA, LUMINANCE, RED, "
                    + "GREEN or BLUE");
        }
        return Conversion.edge(RASTER, MASK, Fidelity.synthesising("a channel", "a threshold"), r -> {
            byte[] rgba = r.rgba();
            boolean[] in = new boolean[r.width() * r.height()];
            for (int i = 0; i < in.length; i++) {
                in[i] = share(channel, rgba, i) >= threshold != invert;
            }
            return new Mask(r.width(), r.height(), in, oversample, 0, 0, 1);
        });
    }

    private static float share(Channel c, byte[] rgba, int i) {
        int o = i * 4;
        float r = (rgba[o] & 0xFF) / 255f, g = (rgba[o + 1] & 0xFF) / 255f, b = (rgba[o + 2] & 0xFF) / 255f;
        float a = (rgba[o + 3] & 0xFF) / 255f;
        return switch (c) {
            case ALPHA -> a;
            case LUMINANCE -> (0.2126f * r + 0.7152f * g + 0.0722f * b) * a + (1 - a);
            case RED -> r;
            case GREEN -> g;
            case BLUE -> b;
            case AUTO -> throw new IllegalStateException("AUTO is refused when the conversion is made");
        };
    }

    /**
     * Mask → SDF, EXACT to the oversample: an exact Euclidean distance transform averaged down to texels, with
     * {@code spread} texels of margin round the mask. {@code MaskSdf.of}.
     */
    public static Conversion<Mask, Field> maskToSdf(int spread) {
        return Conversion.edge(MASK, SDF, Fidelity.EXACT,
                m -> MaskSdf.of(m.inside(), m.width(), m.height(), m.oversample(), spread));
    }

    /**
     * Mask → Shape, EXACT to the oversample: the boundary traced and fitted with the fewest lines within
     * {@code flatness} samples of it, corners kept as vertices. The shape is in the texels of the field the mask was
     * sampled for (sample {@code (x, y)} is texel {@code (x, y) / oversample}); {@link #margin} frames it.
     */
    public static Conversion<Mask, Shape> maskToShape(double flatness) {
        return Conversion.edge(MASK, SHAPE, Fidelity.EXACT, m -> {
            Shape s = MaskTrace.trace(new Mask(m.width(), m.height(), m.inside(), m.oversample(), 0, 0,
                    1.0 / m.oversample()), flatness);
            return s;
        });
    }

    /**
     * Mask → Shape, EXACT: the samples as squares, their boundary exactly, a vertex only where it turns: pixel art's
     * own outline, which an MSDF then keeps square at any size. In the texels of the field the mask was sampled for,
     * as {@link #maskToShape}'s.
     */
    public static Conversion<Mask, Shape> maskToCells() {
        return Conversion.edge(MASK, SHAPE, Fidelity.EXACT, m -> MaskTrace.cells(new Mask(m.width(), m.height(),
                m.inside(), m.oversample(), 0, 0, 1.0 / m.oversample())));
    }

    // ------------------------------------------------------------------ fields

    /**
     * SDF → MSDF or MTSDF as is, LOSSY (corners: round at magnification): the one distance in every channel. Named
     * {@code widen}, never {@code toMsdf}: the exact route goes out to a shape and back.
     */
    public static Conversion<Field, Field> widen(FieldKind kind) {
        if (kind == FieldKind.SDF) {
            throw new IllegalArgumentException("an SDF widened to an SDF is itself");
        }
        return Conversion.edge(SDF, kind == FieldKind.MSDF ? MSDF : MTSDF,
                Fidelity.lossy("corners: round at magnification"), f -> ArtField.widen(f, kind));
    }

    /** SDF → Coverage, EXACT: drawn back over {@code width × height} pixels as the shaders draw it. */
    public static Conversion<Field, CoverageMap> sdfToCoverage(int width, int height) {
        return toCoverage(SDF, FieldKind.SDF, width, height);
    }

    /** MSDF → Coverage, EXACT, as {@link #sdfToCoverage}. */
    public static Conversion<Field, CoverageMap> msdfToCoverage(int width, int height) {
        return toCoverage(MSDF, FieldKind.MSDF, width, height);
    }

    /** MTSDF → Coverage, EXACT, as {@link #sdfToCoverage}. */
    public static Conversion<Field, CoverageMap> mtsdfToCoverage(int width, int height) {
        return toCoverage(MTSDF, FieldKind.MTSDF, width, height);
    }

    private static Conversion<Field, CoverageMap> toCoverage(String name, FieldKind kind, int width, int height) {
        return Conversion.edge(name, COVERAGE, Fidelity.EXACT, f -> {
            kindIs(f, kind);
            return new CoverageMap(width, height, Coverage.render(f, width, height));
        });
    }

    /**
     * Coverage → Mask, EXACT to the pixel: inside where at least half the pixel is, which is where the edge is by
     * definition. A pixel a sample, {@code oversample} to a texel.
     */
    public static Conversion<CoverageMap, Mask> coverageToMask(int oversample) {
        return Conversion.edge(COVERAGE, MASK, Fidelity.EXACT, c -> {
            boolean[] in = new boolean[c.cover().length];
            for (int i = 0; i < in.length; i++) {
                in[i] = c.cover()[i] >= 0.5f;
            }
            return new Mask(c.width(), c.height(), in, oversample, 0, 0, 1);
        });
    }

    /**
     * SDF, MSDF or MTSDF → SDG, SYNTHESISING (a paint): one layer, its stencil the field encoded at {@code range}, its
     * paint {@code paint}. A field is where, not what colour; the colour is supplied. The canvas is the field's texels.
     */
    public static Conversion<Field, Sdg> fieldToSdg(FieldKind kind, double range, Sdg.Paint.Solid paint) {
        return Conversion.edge(nameOf(kind), SDG, Fidelity.synthesising("a paint"), f -> {
            kindIs(f, kind);
            Sdg.Page page = new Sdg.Page(f.width(), f.height(), Encoding.rgba(f, range));
            return new Sdg(f.width(), f.height(), List.of(page), List.of(new Sdg.Layer("field", stencil(f, range),
                    paint, 1f)));
        });
    }

    /**
     * SDF, MSDF or MTSDF → SDG, SYNTHESISING (a paint): as {@link #fieldToSdg(FieldKind, double, Sdg.Paint.Solid)}, its
     * paint the texture {@code paint} stretched over the field, read between its texels.
     */
    public static Conversion<Field, Sdg> fieldToSdg(FieldKind kind, double range, Raster paint) {
        return Conversion.edge(nameOf(kind), SDG, Fidelity.synthesising("a paint"), f -> {
            kindIs(f, kind);
            Sdg.Page field = new Sdg.Page(f.width(), f.height(), Encoding.rgba(f, range));
            Sdg.Page colour = new Sdg.Page(paint.width(), paint.height(), paint.rgba());
            double scale = (double) paint.width() / f.width();
            Sdg.Paint.Sampled sampled = new Sdg.Paint.Sampled(1, 0, 0, paint.width(), paint.height(), 0, 0, scale);
            return new Sdg(f.width(), f.height(), List.of(field, colour),
                    List.of(new Sdg.Layer("field", stencil(f, range), sampled, 1f)));
        });
    }

    private static Sdg.Stencil stencil(Field f, double range) {
        String kind = f.kind() == FieldKind.SDF ? Sdg.Stencil.SDF : Sdg.Stencil.MSDF;
        return new Sdg.Stencil(kind, 0, 0, 0, 0, f.width(), f.height(), 0, 0, 1, range);
    }

    // ------------------------------------------------------------------ SDG

    /**
     * Raster → SDG, LOSSY (colours: clustered into regions): traced, a layer per colour region. {@code Trace.trace}.
     */
    public static Conversion<Raster, Sdg> rasterToSdg(Trace.Options options) {
        return Conversion.edge(RASTER, SDG, Fidelity.lossy("colours: clustered into regions"),
                r -> Trace.trace(r, options));
    }

    /** SDG → Raster, EXACT: the canvas fitted into {@code width × height}, as a shader draws it. {@code SdgRender}. */
    public static Conversion<Sdg, Raster> sdgToRaster(int width, int height) {
        return Conversion.edge(SDG, RASTER, Fidelity.EXACT, s -> SdgRender.render(s, width, height));
    }

    /**
     * SDG → Raster sizes, EXACT: the canvas fitted into a square of each of {@code sizes}, except that a size at or
     * under a {@link Small} drawing's {@code upTo} is drawn from the smallest such drawing that covers it. The small
     * drawings are the data the source does not hold (an icon redrawn for 16 px is a different drawing), so they are
     * a required list: empty when there are none.
     */
    public static Conversion<Sdg, List<Raster>> sdgToRasterSizes(int[] sizes, List<Small> small) {
        int[] sorted = sizes.clone();
        Arrays.sort(sorted);
        List<Small> byUpTo = new ArrayList<>(small);
        byUpTo.sort(Comparator.comparingInt(Small::upTo));
        Fidelity f = small.isEmpty() ? Fidelity.EXACT : Fidelity.synthesising("a drawing per small size");
        return Conversion.edge(SDG, RASTER_SIZES, f, s -> {
            List<Raster> out = new ArrayList<>();
            for (int size : sorted) {
                Sdg from = s;
                for (Small sm : byUpTo) {
                    if (size <= sm.upTo()) {
                        from = sm.drawing();
                        break;
                    }
                }
                out.add(SdgRender.render(from, size, size));
            }
            return List.copyOf(out);
        });
    }

    /** A drawing redrawn for the smallest sizes: every size up to {@code upTo} is drawn from it. */
    public record Small(int upTo, Sdg drawing) {
    }

    /**
     * SDG → Mask, LOSSY (paints): where the drawing is at least half opaque, the union of its layers as drawn, sampled
     * {@code oversample} to a texel of a {@code width × height} field the canvas is fitted into (aspect kept, centred,
     * as {@link SdgRender#render(Sdg, int, int)} fits it). The layers named in {@code without} are left out, so a part
     * can be lifted from the shape and kept in the colour.
     */
    public static Conversion<Sdg, Mask> sdgToMask(int width, int height, int oversample, Set<String> without) {
        Set<String> leave = Set.copyOf(without);
        return Conversion.edge(SDG, MASK, Fidelity.lossy("paints"), s -> {
            Sdg kept = leave.isEmpty() ? s : new Sdg(s.width(), s.height(), s.pages(),
                    s.layers().stream().filter(l -> !leave.contains(l.name())).toList());
            int mw = width * oversample, mh = height * oversample;
            Raster r = SdgRender.render(kept, mw, mh);
            boolean[] in = new boolean[mw * mh];
            byte[] rgba = r.rgba();
            for (int i = 0; i < in.length; i++) {
                in[i] = (rgba[i * 4 + 3] & 0xFF) >= 128;
            }
            double ups = Math.max(s.width() / mw, s.height() / mh);
            return new Mask(mw, mh, in, oversample, (s.width() - mw * ups) / 2, (s.height() - mh * ups) / 2, ups);
        });
    }

    /**
     * SDG → SDF, EXACT: layer {@code index}'s stencil as its own field, in its own texels, if it is an SDF stencil.
     */
    public static Conversion<Sdg, Field> sdgLayerSdf(int index) {
        return layer(index, SDF, Sdg.Stencil.SDF);
    }

    /** SDG → MSDF, EXACT: layer {@code index}'s stencil as its own field, if it is an MSDF stencil. */
    public static Conversion<Sdg, Field> sdgLayerMsdf(int index) {
        return layer(index, MSDF, Sdg.Stencil.MSDF);
    }

    private static Conversion<Sdg, Field> layer(int index, String name, String kind) {
        return Conversion.edge(SDG, name, Fidelity.EXACT, s -> {
            Sdg.Stencil st = s.layers().get(index).stencil();
            if (!st.kind().equals(kind)) {
                throw new IllegalArgumentException("layer " + index + " is " + st.kind() + ", not " + kind);
            }
            Sdg.Page page = s.pages().get(st.page());
            boolean sdf = kind.equals(Sdg.Stencil.SDF);
            Field f = new Field(sdf ? FieldKind.SDF : FieldKind.MSDF, st.width(), st.height());
            for (int y = 0; y < st.height(); y++) {
                for (int x = 0; x < st.width(); x++) {
                    int o = ((st.y() + y) * page.width() + st.x() + x) * 4;
                    for (int c = 0; c < (sdf ? 1 : 3); c++) {
                        int b = page.rgba()[o + (sdf ? st.channel() : c)] & 0xFF;
                        f.set(x, y, c, (float) ((b / 255.0 - 0.5) * st.range()));
                    }
                }
            }
            return f;
        });
    }

    // ------------------------------------------------------------------ probability

    /** A value per texel, for a conversion that needs one the source does not hold. */
    @FunctionalInterface
    public interface PerTexel {
        double at(int x, int y);
    }

    /**
     * SDF → Probability, SYNTHESISING (a footprint's σ): per texel {@code Φ(d / σ)}, the chance that a Gaussian
     * footprint of {@code σ} texels centred there lands inside, near an edge. {@code sigma} is the same everywhere.
     */
    public static Conversion<Field, ProbabilityField> sdfToProbability(double sigma) {
        if (!(sigma > 0)) {
            throw new IllegalArgumentException("a footprint's σ of " + sigma);
        }
        return sdfToProbability((x, y) -> sigma);
    }

    /** SDF → Probability, SYNTHESISING (a footprint's σ), its σ per texel. */
    public static Conversion<Field, ProbabilityField> sdfToProbability(PerTexel sigma) {
        return Conversion.edge(SDF, PROBABILITY, Fidelity.synthesising("a footprint's σ"), f -> {
            kindIs(f, FieldKind.SDF);
            int n = f.width() * f.height();
            float[] p = new float[n], s = new float[n];
            float[] d = f.data();
            for (int i = 0; i < n; i++) {
                double sg = sigma.at(i % f.width(), i / f.width());
                if (!(sg > 0)) {
                    throw new IllegalArgumentException("a footprint's σ of " + sg + " at texel " + i);
                }
                s[i] = (float) sg;
                p[i] = (float) Footprint.phi(d[i] / sg);
            }
            return new ProbabilityField(f.width(), f.height(), p, s);
        });
    }

    /**
     * Probability → SDF, LOSSY (distances beyond the band: flat): per texel {@code σ · Φ⁻¹(p)}, {@code p} held to
     * {@code [band, 1 − band]}, past which a chance no longer tells one distance from another.
     */
    public static Conversion<ProbabilityField, Field> probabilityToSdf(double band) {
        if (!(band > 0 && band < 0.5)) {
            throw new IllegalArgumentException("a band of " + band);
        }
        return Conversion.edge(PROBABILITY, SDF, Fidelity.lossy("distances beyond the band: flat"), pf -> {
            int n = pf.width() * pf.height();
            float[] d = new float[n];
            for (int i = 0; i < n; i++) {
                d[i] = (float) (pf.sigma()[i] * Footprint.inverse(Math.clamp(pf.p()[i], band, 1 - band)));
            }
            return new Field(FieldKind.SDF, pf.width(), pf.height(), d);
        });
    }

    // ------------------------------------------------------------------ icons

    /** Raster sizes → Icon, EXACT: packed as one {@code .ico}, a PNG per size. */
    public static Conversion<List<Raster>, Icon> icon() {
        return Conversion.edge(RASTER_SIZES, ICON, Fidelity.EXACT, Icon::new);
    }

    // ------------------------------------------------------------------ named routes

    /** SVG → Shape → MTSDF, LOSSY (paints). */
    public static Conversion<Svg, Field> svgToMtsdf(Framing framing, int width, int height) {
        return svgToShape().then(shapeToMtsdf(framing, width, height));
    }

    /**
     * SDG → Mask → Shape → MTSDF, LOSSY (paints): the exact route, corners sharp. A {@code field} texel square with
     * {@code spread} round it, sampled {@code oversample} to a texel and traced within {@code flatness} samples.
     */
    public static Conversion<Sdg, Field> sdgToMtsdf(int field, int spread, int oversample, double flatness,
                                                    Set<String> without) {
        return sdgToMask(field, field, oversample, without)
                .then(maskToShape(flatness))
                .then(shapeToMtsdf(margin(spread), field + 2 * spread, field + 2 * spread));
    }

    /**
     * SDG → Mask → Shape → SDF, LOSSY (paints): the exact route to one true distance per texel, the traced shape's own,
     * where {@link #sdgToMtsdf} gives three pseudo-distances. As {@link #sdgToMtsdf}, the same texels.
     */
    public static Conversion<Sdg, Field> sdgToSdf(int field, int spread, int oversample, double flatness,
                                                  Set<String> without) {
        return sdgToMask(field, field, oversample, without)
                .then(maskToShape(flatness))
                .then(shapeToSdf(margin(spread), field + 2 * spread, field + 2 * spread));
    }

    /**
     * SDG → Mask → SDF → MTSDF, LOSSY (paints; corners: round at magnification): the fast route, no tracing. As
     * {@link #sdgToMtsdf}, the same texels.
     */
    public static Conversion<Sdg, Field> sdgToMtsdfFast(int field, int spread, int oversample, Set<String> without) {
        return sdgToMask(field, field, oversample, without)
                .then(maskToSdf(spread))
                .then(widen(FieldKind.MTSDF));
    }

    /**
     * (SDG → Mask → Shape → SDF) beside (SDG → Raster sizes) → Art pair: what {@code Condense} draws, from a drawing.
     * The field {@code field} texels square with {@code margin} of it each side, made by the exact route; the colour
     * at each of {@code sizes}; the layer named {@code lift}, if not null, left out of the field and kept in the colour,
     * its place recorded. Replaces {@code ArtPair.bake} for anything that has an SDG.
     *
     * <p>The field is an SDF, one true distance, never the traced shape's MTSDF: {@link ArtPair}'s near channels are
     * one distance, and {@code Condense} reads them as that distance well off the edge (its rings, rim, cut and
     * bevel). An MTSDF's pseudo-distances part from the true one past a corner, by up to nineteen texels on the
     * VexelRay mark's points, and the opening drew its light there out of place.
     */
    public static Conversion<Sdg, ArtPair> sdgToArtPair(int field, float margin, int[] sizes, String lift) {
        return artPair(field, margin, sizes, lift, true);
    }

    /** As {@link #sdgToArtPair}, its field by the fast route: (SDG → Mask → SDF → MTSDF) beside .... */
    public static Conversion<Sdg, ArtPair> sdgToArtPairFast(int field, float margin, int[] sizes, String lift) {
        return artPair(field, margin, sizes, lift, false);
    }

    /** The oversample and flatness an art pair's field is traced at. */
    public static final int PAIR_OVERSAMPLE = 8;
    public static final double PAIR_FLATNESS = 0.25;

    private static Conversion<Sdg, ArtPair> artPair(int field, float margin, int[] sizes, String lift,
                                                    boolean exact) {
        int spread = Math.round(margin * field);
        Set<String> without = lift == null ? Set.of() : Set.of(lift);
        Conversion<Sdg, Field> shape = exact
                ? sdgToSdf(field, spread, PAIR_OVERSAMPLE, PAIR_FLATNESS, without)
                : sdgToMtsdfFast(field, spread, PAIR_OVERSAMPLE, without);
        return Conversion.beside(shape, sdgToRasterSizes(sizes, List.of()), ART_PAIR, Fidelity.EXACT,
                (sdg, d, colours) -> ArtPair.assemble(d, field, spread, colours, lift == null ? null : lift(sdg, lift)));
    }

    /** Where layer {@code name} sits, as shares of the fitted square: the bounds of its alpha at 512 px. */
    private static ArtPair.Lift lift(Sdg sdg, String name) {
        List<Sdg.Layer> only = sdg.layers().stream().filter(l -> l.name().equals(name)).toList();
        if (only.isEmpty()) {
            throw new IllegalArgumentException("no layer named " + name + " to lift");
        }
        int side = 512;
        byte[] a = SdgRender.render(new Sdg(sdg.width(), sdg.height(), sdg.pages(), only), side, side).rgba();
        int x0 = side, y0 = side, x1 = -1, y1 = -1;
        for (int y = 0; y < side; y++) {
            for (int x = 0; x < side; x++) {
                if ((a[(y * side + x) * 4 + 3] & 0xFF) >= 128) {
                    x0 = Math.min(x0, x);
                    y0 = Math.min(y0, y);
                    x1 = Math.max(x1, x);
                    y1 = Math.max(y1, y);
                }
            }
        }
        if (x1 < 0) {
            throw new IllegalArgumentException("the lifted layer " + name + " covers nothing");
        }
        return new ArtPair.Lift((x0 + x1 + 1) / 2f / side, (y0 + y1 + 1) / 2f / side,
                Math.max(x1 + 1 - x0, y1 + 1 - y0) / 2f / side);
    }

    /** SVG → SDG → Art pair: an SVG's pair by way of its drawing, the route {@code ArtPair.bake} is replaced by. */
    public static Conversion<Svg, ArtPair> svgToArtPair(int texels, int field, float margin, int[] sizes,
                                                        String lift) {
        return svgToSdg(texels).then(sdgToArtPair(field, margin, sizes, lift));
    }

    /** SDG → Raster sizes → Icon, EXACT (or needing the small drawings, when there are some). */
    public static Conversion<Sdg, Icon> sdgToIcon(int[] sizes, List<Small> small) {
        return sdgToRasterSizes(sizes, small).then(icon());
    }

    /** SVG → Raster sizes → Icon, EXACT (or needing the small drawings): an icon of a drawing that is not an SDG. */
    public static Conversion<Svg, Icon> svgToIcon(int[] sizes, List<SmallSvg> small) {
        return svgToRasterSizes(sizes, small).then(icon());
    }

    /**
     * Raster → Mask → Shape → MSDF, SYNTHESISING (a channel, a threshold): a {@code width × height} texel field with
     * {@code spread} round it, from a raster {@code oversample} pixels to each of its texels.
     */
    public static Conversion<Raster, Field> rasterToMsdf(Channel channel, float threshold, boolean invert,
                                                         int oversample, double flatness, int width, int height,
                                                         int spread) {
        return rasterToMask(channel, threshold, invert, oversample)
                .then(maskToShape(flatness))
                .then(shapeToMsdf(margin(spread), width + 2 * spread, height + 2 * spread));
    }

    /** MSDF → Coverage → Mask → SDF, EXACT to the oversample. */
    public static Conversion<Field, Field> msdfToSdf(int width, int height, int oversample, int spread) {
        return msdfToCoverage(width * oversample, height * oversample)
                .then(coverageToMask(oversample))
                .then(maskToSdf(spread));
    }

    private static void kindIs(Field f, FieldKind kind) {
        if (f.kind() != kind) {
            throw new IllegalArgumentException("a " + f.kind().key + " field where " + kind.key + " was asked");
        }
    }

    private static String nameOf(FieldKind k) {
        return switch (k) {
            case SDF -> SDF;
            case MSDF -> MSDF;
            case MTSDF -> MTSDF;
        };
    }
}
