package dev.vexelray.fields.sdg;

import dev.vexelray.fields.Field;
import dev.vexelray.fields.FieldKind;
import dev.vexelray.fields.Framing;
import dev.vexelray.fields.art.Svg;
import dev.vexelray.fields.art.SvgPart;
import dev.vexelray.fields.msdf.Msdf;
import dev.vexelray.fields.sdf.MaskSdf;
import dev.vexelray.fields.shape.Bounds;
import dev.vexelray.fields.shape.Shape;

import java.util.List;

/**
 * An SVG kept as layers, one for each fill and each stroke ({@link Svg#parts}), painted in its order. A fill by the
 * nonzero rule has its outline read exactly and its stencil is an MSDF, corners sharp; a stroke or an even-odd fill is
 * drawn as a mask at {@value #OVERSAMPLE} pixels a texel and its stencil is an SDF. A paint of one colour is
 * {@link Sdg.Paint.Solid}; a gradient is baked into a texture at the field's density, a gradient being smooth enough
 * that its texels read between give it back.
 */
public final class SvgToSdg {

    static final int OVERSAMPLE = 8;

    /**
     * @param texelsPerUnit texels of field (and of baked gradient) to a unit of the view box
     * @param spread        texels of margin round each layer, the farthest its field reaches outside, and half its
     *                      range
     */
    public record Options(double texelsPerUnit, int spread) {

        public Options {
            if (!(texelsPerUnit > 0) || spread < 1) {
                throw new IllegalArgumentException(texelsPerUnit + " texels a unit, " + spread + " of margin");
            }
        }

        /** {@code texels} across the view box's longer side for {@code svg}, a margin of 4. */
        public static Options across(Svg svg, int texels) {
            Bounds vb = svg.viewBox();
            return new Options(texels / Math.max(vb.width(), vb.height()), 4);
        }
    }

    private SvgToSdg() {
    }

    public static Sdg convert(Svg svg, Options o) {
        Bounds vb = svg.viewBox();
        List<SvgPart> parts = svg.parts();
        Assembly a = new Assembly(vb.width(), vb.height());
        double s = o.texelsPerUnit();
        int n = 0;
        for (SvgPart part : parts) {
            n++;
            Bounds b = part.bounds();
            if (b.isEmpty() || part.opacity() <= 0) {
                continue;
            }
            // the rectangle, in texels from the view box's corner, with the margin round it
            int x0 = (int) Math.floor((b.xMin() - vb.xMin()) * s) - o.spread();
            int y0 = (int) Math.floor((b.yMin() - vb.yMin()) * s) - o.spread();
            int x1 = (int) Math.ceil((b.xMax() - vb.xMin()) * s) + o.spread();
            int y1 = (int) Math.ceil((b.yMax() - vb.yMin()) * s) + o.spread();
            int w = x1 - x0, h = y1 - y0;
            double ox = vb.xMin() + x0 / s, oy = vb.yMin() + y0 / s;   // the rectangle's corner, view-box units

            Field field;
            var outline = part.outline();
            if (outline.isPresent()) {
                Shape shape = outline.get();
                field = Msdf.generate(FieldKind.MSDF, shape, new Framing(s, ox, oy, false), w, h);
            } else {
                boolean[] mask = part.mask(ox, oy, s * OVERSAMPLE, w * OVERSAMPLE, h * OVERSAMPLE);
                field = MaskSdf.of(mask, w * OVERSAMPLE, h * OVERSAMPLE, OVERSAMPLE, 0);
            }
            Assembly.Stencil stencil = new Assembly.Stencil(field, x0 / s, y0 / s, s, 2.0 * o.spread());
            String name = part.id().orElse((part.stroke() ? "stroke " : "fill ") + n);
            var solid = part.solid();
            if (solid.isPresent()) {
                float[] c = solid.get();
                a.add(name, stencil, Sdg.Paint.Solid.of(c[0], c[1], c[2], 1), part.opacity());
            } else {
                a.add(name, stencil, bake(part, w, h, ox, oy, s, x0 / s, y0 / s), part.opacity());
            }
        }
        return a.build();
    }

    /** The part's gradient over its rectangle, a texel each field texel, straight. */
    private static Assembly.Texture bake(SvgPart part, int w, int h, double ox, double oy, double s,
                                         double canvasX, double canvasY) {
        byte[] rgba = new byte[w * h * 4];
        float[] c = new float[4];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                part.colour(ox + (x + 0.5) / s, oy + (y + 0.5) / s, c);
                float inv = c[3] > 0 ? 1 / c[3] : 0;
                int i = (y * w + x) * 4;
                rgba[i] = byte8(c[0] * inv);
                rgba[i + 1] = byte8(c[1] * inv);
                rgba[i + 2] = byte8(c[2] * inv);
                rgba[i + 3] = byte8(c[3]);
            }
        }
        return new Assembly.Texture(w, h, rgba, canvasX, canvasY, s);
    }

    static byte byte8(float v) {
        return (byte) Math.round(Math.clamp(v, 0f, 1f) * 255f);
    }
}
