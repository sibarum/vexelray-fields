package dev.vexelray.fields.bench;

import dev.vexelray.fields.Field;
import dev.vexelray.fields.FieldKind;
import dev.vexelray.fields.Framing;
import dev.vexelray.fields.art.Raster;
import dev.vexelray.fields.art.Svg;
import dev.vexelray.fields.convert.Convert;
import dev.vexelray.fields.convert.CoverageMap;
import dev.vexelray.fields.convert.Mask;
import dev.vexelray.fields.convert.ProbabilityField;
import dev.vexelray.fields.msdf.Msdf;
import dev.vexelray.fields.sdf.ShapeSdf;
import dev.vexelray.fields.sdg.Sdg;
import dev.vexelray.fields.sdg.SdgRender;
import dev.vexelray.fields.shape.PathData;
import dev.vexelray.fields.shape.Shape;

import java.util.ArrayList;
import java.util.List;

/**
 * The fixed inputs every benchmark runs on, made the same way every run: the README's star, a badge drawing (a rounded
 * square, a star and a sun, the shape of an application mark), a picture of the badge for the raster conversions, and
 * what each of those becomes. Nothing is read from disk.
 */
final class Inputs {

    private Inputs() {
    }

    static final String STAR_PATH = "M50 0 L61 35 L98 35 L68 57 L79 91 L50 70 L21 91 L32 57 L2 35 L39 35 Z";

    static final String STAR_SVG = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 100 100\"><path d=\""
            + STAR_PATH + "\" fill=\"#000000\"/></svg>";

    static final String BADGE_SVG = """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100">
              <rect x="4" y="4" width="92" height="92" rx="18" fill="#2d6cdf"/>
              <path d="M50 18 L58 42 L84 42 L63 57 L71 82 L50 67 L29 82 L37 57 L16 42 L42 42 Z" fill="#ffffff"/>
              <circle id="sun" cx="78" cy="22" r="10" fill="#ffcc33"/>
              <path d="M8 74 C30 64 60 84 92 70" fill="none" stroke="#173d22" stroke-width="5"/>
            </svg>""";

    static Shape star() {
        return PathData.parse(STAR_PATH);
    }

    static Framing starFraming(int texels) {
        return Framing.fit(star().bounds(), texels, texels, Math.max(2, texels / 8), false);
    }

    static Svg starSvg() {
        return Svg.parse(STAR_SVG);
    }

    static Svg badgeSvg() {
        return Svg.parse(BADGE_SVG);
    }

    static Sdg badge() {
        return Convert.svgToSdg(64).apply(badgeSvg());
    }

    /** The badge drawn as a picture, {@code side} pixels square: what the raster conversions take. */
    static Raster badgePicture(int side) {
        return SdgRender.render(badge(), side, side);
    }

    static Field starField(FieldKind kind, int texels) {
        Framing f = starFraming(texels);
        return switch (kind) {
            case SDF -> ShapeSdf.generate(star(), f, texels, texels);
            case MSDF -> Msdf.generate(star(), f, texels, texels);
            case MTSDF -> Msdf.generateMtsdf(star(), f, texels, texels);
        };
    }

    /** The star's mask, {@code oversample} samples to each of {@code texels} texels each way. */
    static Mask starMask(int texels, int oversample) {
        Framing f = starFraming(texels);
        Shape star = star();
        int side = texels * oversample;
        boolean[] in = new boolean[side * side];
        for (int y = 0; y < side; y++) {
            for (int x = 0; x < side; x++) {
                in[y * side + x] = star.filled(f.toShape((x + 0.5) / oversample, (y + 0.5) / oversample));
            }
        }
        return new Mask(side, side, in, oversample, 0, 0, 1);
    }

    static CoverageMap starCoverage(int texels, int pixels) {
        return Convert.msdfToCoverage(pixels, pixels).apply(starField(FieldKind.MSDF, texels));
    }

    static ProbabilityField starProbability(int texels) {
        return Convert.sdfToProbability(1.5).apply(starField(FieldKind.SDF, texels));
    }

    /** The badge at every size Windows asks an icon for. */
    static List<Raster> badgeSizes() {
        List<Raster> out = new ArrayList<>();
        for (int s : dev.vexelray.fields.convert.Icon.WINDOWS) {
            out.add(SdgRender.render(badge(), s, s));
        }
        return out;
    }

    /** A texture to paint a field with: a two-colour ramp, {@code side} square. */
    static Raster ramp(int side) {
        byte[] rgba = new byte[side * side * 4];
        for (int y = 0; y < side; y++) {
            for (int x = 0; x < side; x++) {
                int o = (y * side + x) * 4;
                rgba[o] = (byte) (255 * x / side);
                rgba[o + 1] = (byte) (255 * y / side);
                rgba[o + 2] = (byte) 160;
                rgba[o + 3] = (byte) 255;
            }
        }
        return new Raster(side, side, rgba);
    }
}
