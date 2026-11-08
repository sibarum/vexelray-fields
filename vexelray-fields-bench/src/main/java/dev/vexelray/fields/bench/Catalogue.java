package dev.vexelray.fields.bench;

import dev.vexelray.fields.FieldKind;
import dev.vexelray.fields.art.Channel;
import dev.vexelray.fields.convert.Conversion;
import dev.vexelray.fields.convert.Convert;
import dev.vexelray.fields.convert.Icon;
import dev.vexelray.fields.sdg.Sdg;
import dev.vexelray.fields.sdg.Trace;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Every case the benchmark runs: each {@link Convert} factory at two sizes, and each linear route hop by hop as well,
 * every hop on the very input it gets inside the route. A case is keyed {@code "factory @ input"}, or
 * {@code "route @ input / 2 Mask → Shape"} for a route's second hop.
 *
 * <p>{@code CatalogueTest} fails if a public factory of {@link Convert} has no case: a conversion lands with its
 * benchmark or does not land.
 */
public final class Catalogue {

    /**
     * One thing to time.
     *
     * @param factory the {@link Convert} method that made it
     * @param input   what it runs on, in words
     * @param route   for a hop of a route, the route's key; else null
     * @param hop     for a hop, its place in the route from 1; else 0
     */
    public record Case(String key, String factory, Conversion<Object, Object> conversion, Supplier<Object> input,
                       String inputLabel, String route, int hop) {
    }

    private Catalogue() {
    }

    private static final Map<String, Case> CASES = build();

    public static Map<String, Case> cases() {
        return CASES;
    }

    public static Case get(String key) {
        Case c = CASES.get(key);
        if (c == null) {
            throw new IllegalArgumentException("no benchmark case " + key);
        }
        return c;
    }

    private static Map<String, Case> build() {
        Map<String, Case> m = new LinkedHashMap<>();
        for (int t : new int[]{32, 64}) {
            String tl = t + " texels";
            add(m, "shapeToSdf", Convert.shapeToSdf(Inputs.starFraming(t), t, t), Inputs::star, "star, " + tl);
            add(m, "shapeToMsdf", Convert.shapeToMsdf(Inputs.starFraming(t), t, t), Inputs::star, "star, " + tl);
            add(m, "shapeToMtsdf", Convert.shapeToMtsdf(Inputs.starFraming(t), t, t), Inputs::star, "star, " + tl);
            add(m, "svgToSdg", Convert.svgToSdg(t), Inputs::badgeSvg, "badge, " + tl + " across");
            add(m, "maskToSdf", Convert.maskToSdf(t / 8), () -> Inputs.starMask(t, 8), "star, " + tl + " × 8");
            add(m, "maskToShape", Convert.maskToShape(0.25), () -> Inputs.starMask(t, 8), "star, " + tl + " × 8");
            add(m, "maskToCells", Convert.maskToCells(), () -> Inputs.starMask(t, 8), "star, " + tl + " × 8");
            add(m, "widen", Convert.widen(FieldKind.MTSDF), () -> Inputs.starField(FieldKind.SDF, t),
                    "star SDF, " + tl);
            int px = t * 8;
            add(m, "sdfToCoverage", Convert.sdfToCoverage(px, px), () -> Inputs.starField(FieldKind.SDF, t),
                    "star, " + tl + " to " + px + " px");
            add(m, "msdfToCoverage", Convert.msdfToCoverage(px, px), () -> Inputs.starField(FieldKind.MSDF, t),
                    "star, " + tl + " to " + px + " px");
            add(m, "mtsdfToCoverage", Convert.mtsdfToCoverage(px, px), () -> Inputs.starField(FieldKind.MTSDF, t),
                    "star, " + tl + " to " + px + " px");
            add(m, "coverageToMask", Convert.coverageToMask(8), () -> Inputs.starCoverage(t, px),
                    "star, " + px + " px");
            add(m, "fieldToSdg", Convert.fieldToSdg(FieldKind.SDF, 8, new Sdg.Paint.Solid(0x2D6CDFFF)),
                    () -> Inputs.starField(FieldKind.SDF, t), "star SDF, " + tl + ", a solid paint");
            add(m, "fieldToSdg", Convert.fieldToSdg(FieldKind.SDF, 8, Inputs.ramp(t)),
                    () -> Inputs.starField(FieldKind.SDF, t), "star SDF, " + tl + ", a texture");
            add(m, "sdgToMask", Convert.sdgToMask(t, t, 8, Set.of()), Inputs::badge, "badge, " + tl + " × 8");
            add(m, "sdfToProbability", Convert.sdfToProbability(1.5), () -> Inputs.starField(FieldKind.SDF, t),
                    "star, " + tl + ", σ 1.5");
            add(m, "sdfToProbability", Convert.sdfToProbability((x, y) -> 1 + x / 32.0),
                    () -> Inputs.starField(FieldKind.SDF, t), "star, " + tl + ", σ per texel");
            add(m, "probabilityToSdf", Convert.probabilityToSdf(1e-4), () -> Inputs.starProbability(t),
                    "star, " + tl);
            add(m, "sdgLayerSdf", Convert.sdgLayerSdf(0),
                    () -> Convert.fieldToSdg(FieldKind.SDF, 8, new Sdg.Paint.Solid(-1))
                            .apply(Inputs.starField(FieldKind.SDF, t)), "star SDG, " + tl);
            add(m, "sdgLayerMsdf", Convert.sdgLayerMsdf(0),
                    () -> Convert.fieldToSdg(FieldKind.MSDF, 8, new Sdg.Paint.Solid(-1))
                            .apply(Inputs.starField(FieldKind.MSDF, t)), "star SDG, " + tl);
            add(m, "rasterToMask", Convert.rasterToMask(Channel.ALPHA, 0.5f, false, 8),
                    () -> Inputs.badgePicture(px), "badge picture, " + px + " px");

            // routes
            add(m, "svgToMtsdf", Convert.svgToMtsdf(Inputs.starFraming(t), t, t), Inputs::starSvg, "star, " + tl);
            add(m, "sdgToMtsdf", Convert.sdgToMtsdf(t, t / 8, 8, 0.25, Set.of()), Inputs::badge,
                    "badge, " + tl + " × 8");
            add(m, "sdgToMtsdfFast", Convert.sdgToMtsdfFast(t, t / 8, 8, Set.of()), Inputs::badge,
                    "badge, " + tl + " × 8");
            add(m, "rasterToMsdf", Convert.rasterToMsdf(Channel.ALPHA, 0.5f, false, 8, 0.25, t, t, t / 8),
                    () -> Inputs.badgePicture(px), "badge picture, " + px + " px to " + tl);
            add(m, "msdfToSdf", Convert.msdfToSdf(t, t, 8, 0), () -> Inputs.starField(FieldKind.MSDF, t),
                    "star, " + tl + " × 8");
        }
        for (int side : new int[]{256, 1024}) {
            add(m, "sdgToRaster", Convert.sdgToRaster(side, side), Inputs::badge, "badge, " + side + " px");
        }
        for (int side : new int[]{64, 128}) {
            add(m, "rasterToSdg", Convert.rasterToSdg(Trace.Options.DEFAULT), () -> Inputs.badgePicture(side),
                    "badge picture, " + side + " px");
        }
        add(m, "sdgToRasterSizes", Convert.sdgToRasterSizes(Icon.WINDOWS, List.of()), Inputs::badge,
                "badge, the 10 Windows icon sizes");
        add(m, "sdgToRasterSizes", Convert.sdgToRasterSizes(Icon.WINDOWS,
                        List.of(new Convert.Small(24, Inputs.badge()))), Inputs::badge,
                "badge, the 10 Windows icon sizes, a small drawing to 24");
        add(m, "icon", Convert.icon(), Inputs::badgeSizes, "badge, the 10 Windows icon sizes");
        for (int side : new int[]{64, 256}) {
            add(m, "svgToRaster", Convert.svgToRaster(side, side), Inputs::badgeSvg, "badge, " + side + " px");
        }
        add(m, "svgToRasterSizes", Convert.svgToRasterSizes(Icon.WINDOWS, List.of()), Inputs::badgeSvg,
                "badge, the 10 Windows icon sizes");
        add(m, "svgToIcon", Convert.svgToIcon(Icon.WINDOWS, List.of()), Inputs::badgeSvg,
                "badge, the 10 Windows icon sizes");
        add(m, "sdgToIcon", Convert.sdgToIcon(Icon.WINDOWS, List.of()), Inputs::badge,
                "badge, the 10 Windows icon sizes");
        for (int f : new int[]{48, 104}) {
            int[] sizes = {f, f * 2};
            String label = "badge, field " + f + ", colour at " + f + " and " + 2 * f;
            add(m, "sdgToArtPair", Convert.sdgToArtPair(f, 0.25f, sizes, "sun"), Inputs::badge, label);
            add(m, "sdgToArtPairFast", Convert.sdgToArtPairFast(f, 0.25f, sizes, "sun"), Inputs::badge, label);
            add(m, "svgToArtPair", Convert.svgToArtPair(64, f, 0.25f, sizes, "sun"), Inputs::badgeSvg, label);
        }
        add(m, "svgToShape", Convert.svgToShape(), Inputs::starSvg, "star");
        return hops(m);
    }

    @SuppressWarnings("unchecked")
    private static void add(Map<String, Case> m, String factory, Conversion<?, ?> c, Supplier<?> input,
                            String label) {
        String key = factory + " @ " + label;
        if (m.containsKey(key)) {
            throw new IllegalStateException("two cases " + key);
        }
        m.put(key, new Case(key, factory, (Conversion<Object, Object>) c, (Supplier<Object>) input, label, null, 0));
    }

    /**
     * Each linear route's hops as cases of their own, each on what the hop before it made from the route's input. A
     * branched route ({@code beside}) is not split: its branches are routes with cases of their own.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Case> hops(Map<String, Case> m) {
        Map<String, Case> out = new LinkedHashMap<>();
        for (Case c : m.values()) {
            out.put(c.key(), c);
            List<Conversion<?, ?>> hops = c.conversion().hops();
            if (hops.size() < 2 || c.conversion().name().contains(" beside ")) {
                continue;
            }
            List<Supplier<Object>> ins = new ArrayList<>();
            ins.add(c.input());
            for (int i = 1; i < hops.size(); i++) {
                Supplier<Object> before = ins.get(i - 1);
                Conversion<Object, Object> h = (Conversion<Object, Object>) hops.get(i - 1);
                ins.add(() -> h.apply(before.get()));
            }
            for (int i = 0; i < hops.size(); i++) {
                Conversion<Object, Object> h = (Conversion<Object, Object>) hops.get(i);
                String key = c.key() + " / " + (i + 1) + " " + h.name();
                out.put(key, new Case(key, c.factory(), h, ins.get(i), c.inputLabel(), c.key(), i + 1));
            }
        }
        return out;
    }
}
