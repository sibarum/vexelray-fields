package dev.vexelray.fields.sdg;

import dev.vexelray.fields.Field;
import dev.vexelray.fields.FieldKind;
import dev.vexelray.fields.Framing;
import dev.vexelray.fields.art.SvgText;
import dev.vexelray.fields.msdf.Msdf;
import dev.vexelray.fields.shape.Bounds;
import dev.vexelray.fields.shape.Path;
import dev.vexelray.fields.shape.Shape;
import dev.vexelray.fields.shape.Vec2;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.font.LineMetrics;
import java.awt.font.TextAttribute;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Sets SVG text in fonts, as text layers (SDG 1.3): each {@code <text>}'s runs laid out by the JDK's text layout,
 * kerning and ligatures and all, chunk by chunk as {@code text-anchor} says, then a text layer for each stretch of one
 * font, size and fill. The glyphs a drawing uses are added to its fonts as they are first set; their fields, MSDFs of
 * their outlines, are made once all the text is set, at the density the largest size a face is set at needs, and no less
 * than {@value #MIN_TEXELS_PER_EM} texels an em.
 */
final class TextSetter {

    /** Font units an em that faces are laid out at: positions are scaled from these. */
    private static final float EM = 1000f;

    /** The fewest texels an em a glyph's field has, however small its text: below it, MSDF glyphs lose their shape. */
    static final int MIN_TEXELS_PER_EM = 32;

    private static final FontRenderContext FRC = new FontRenderContext(null, true, true);

    private final Assembly assembly;
    private final SvgToSdg.Options options;
    private final Bounds viewBox;
    private final Consumer<String> warn;
    private final Map<Object, Face> faces = new LinkedHashMap<>();
    private int count;

    TextSetter(Assembly assembly, SvgToSdg.Options options, Bounds viewBox) {
        this.assembly = assembly;
        this.options = options;
        this.viewBox = viewBox;
        this.warn = options.warnings();
    }

    /** A face being used: its font at {@link #EM}, its pending font, and the glyphs set so far. */
    private final class Face {
        final Font font;
        final Assembly.Font pending;
        final int index;
        final Map<Integer, Integer> glyphOf = new HashMap<>();   // glyph code to the font's glyph number
        final List<Integer> codes = new ArrayList<>();
        final List<String> codepoints = new ArrayList<>();
        final List<Shape> outlines = new ArrayList<>();   // in ems, y down; null for none
        double largest;   // the largest size it is set at, in canvas units an em

        Face(FontSet.Face face) {
            Font base = face != null ? face.font() : new Font(Font.SANS_SERIF, Font.PLAIN, 1);
            Map<TextAttribute, Object> features = new HashMap<>();
            features.put(TextAttribute.KERNING, TextAttribute.KERNING_ON);
            features.put(TextAttribute.LIGATURES, TextAttribute.LIGATURES_ON);
            font = base.deriveFont(EM).deriveFont(features);
            LineMetrics lm = font.getLineMetrics("Hg", FRC);
            String family = face != null ? face.family() : font.getFamily(java.util.Locale.ROOT);
            String style = face != null ? face.style() : "Regular";
            pending = new Assembly.Font(family, style, lm.getAscent() / EM, -lm.getDescent() / EM,
                    (lm.getAscent() + lm.getDescent() + lm.getLeading()) / EM, face != null ? face.license() : "");
            index = assembly.font(pending);
        }

        /** The font's glyph number for glyph {@code code}, added the first time it is set. */
        int glyph(int code, String chars) {
            return glyphOf.computeIfAbsent(code, c -> {
                codes.add(c);
                codepoints.add(chars);
                outlines.add(outline(c));
                return codes.size() - 1;
            });
        }

        Shape outline(int code) {
            java.awt.Shape s = font.createGlyphVector(FRC, new int[]{code}).getOutline();
            Path.Builder b = Path.builder();
            double[] c = new double[6];
            boolean any = false;
            for (PathIterator it = s.getPathIterator(null); !it.isDone(); it.next()) {
                switch (it.currentSegment(c)) {
                    case PathIterator.SEG_MOVETO -> b.moveTo(c[0] / EM, c[1] / EM);
                    case PathIterator.SEG_LINETO -> b.lineTo(c[0] / EM, c[1] / EM);
                    case PathIterator.SEG_QUADTO -> b.quadTo(c[0] / EM, c[1] / EM, c[2] / EM, c[3] / EM);
                    case PathIterator.SEG_CUBICTO -> b.cubicTo(c[0] / EM, c[1] / EM, c[2] / EM, c[3] / EM, c[4] / EM,
                            c[5] / EM);
                    case PathIterator.SEG_CLOSE -> b.close();
                    default -> throw new IllegalStateException();
                }
                any = true;
            }
            if (!any) {
                return null;
            }
            Shape shape = b.build().shape().oriented();
            return shape.contours().isEmpty() || shape.bounds().isEmpty() ? null : shape;
        }

        /** Every glyph set, its field made and pended: done once, when all the text is set. */
        void finish() {
            double s = Math.max(MIN_TEXELS_PER_EM, largest * options.texelsPerUnit());
            int spread = options.spread();
            for (int g = 0; g < codes.size(); g++) {
                double advance = font.createGlyphVector(FRC, new int[]{codes.get(g)}).getGlyphMetrics(0)
                        .getAdvanceX() / EM;
                Shape shape = outlines.get(g);
                int field = -1;
                if (shape != null) {
                    Bounds b = shape.bounds();
                    int x0 = (int) Math.floor(b.xMin() * s) - spread, y0 = (int) Math.floor(b.yMin() * s) - spread;
                    int x1 = (int) Math.ceil(b.xMax() * s) + spread, y1 = (int) Math.ceil(b.yMax() * s) + spread;
                    double ox = x0 / s, oy = y0 / s;
                    Field msdf = Msdf.generate(FieldKind.MSDF, shape, new Framing(s, ox, oy, false), x1 - x0, y1 - y0);
                    field = assembly.pend("glyph", new Assembly.Stencil(msdf, ox, oy, s, 2.0 * spread), null, null,
                            1f);
                }
                pending.glyphs.add(new Assembly.Glyph(field, advance, codepoints.get(g)));
            }
        }
    }

    /** A glyph as laid out: where its pen is in the text's user units, and the run it is from. */
    private record Laid(Face face, int glyph, double x, double y, SvgText.Run run) {
    }

    /**
     * {@code text} set: its text layers, back to front, as nodes. A run with no fill, or none to see, still takes its
     * room, and draws nothing.
     */
    List<Assembly.Node> set(SvgText text) {
        List<Laid> laid = new ArrayList<>();
        List<int[]> chunks = new ArrayList<>();   // from, to: glyphs of laid, one anchor each
        List<double[]> extents = new ArrayList<>();   // start and end of each chunk, across
        double penX = 0, penY = 0;
        for (int r = 0; r < text.runs().size(); r++) {
            SvgText.Run run = text.runs().get(r);
            if (!Double.isNaN(run.x())) {
                penX = run.x();
            }
            if (!Double.isNaN(run.y())) {
                penY = run.y();
            }
            penX += run.dx();
            penY += run.dy();
            if (r == 0 || run.startsChunk()) {
                chunks.add(new int[]{laid.size(), laid.size(), anchor(run.anchor())});
                extents.add(new double[]{penX, penX});
            }
            Face face = face(run);
            String chars = run.text();
            for (int at = face.font.canDisplayUpTo(chars); at >= 0 && at < chars.length();
                 at = at + 1 >= chars.length() ? -1 : face.font.canDisplayUpTo(chars.toCharArray(), at + 1,
                         chars.length())) {
                warn.accept("the font " + face.font.getFontName() + " has no glyph for \"" + chars.charAt(at)
                        + "\" (U+" + String.format("%04X", (int) chars.charAt(at)) + "); it is drawn as the font"
                        + " draws what it has not");
            }
            GlyphVector gv = face.font.layoutGlyphVector(FRC, chars.toCharArray(), 0, chars.length(),
                    Font.LAYOUT_LEFT_TO_RIGHT);
            double k = run.size() / EM;
            int n = gv.getNumGlyphs();
            for (int g = 0; g < n; g++) {
                int code = gv.getGlyphCode(g);
                if (code >= 0xFFFE) {
                    continue;   // an invisible glyph the layout made: a joiner, say
                }
                int from = gv.getGlyphCharIndex(g);
                int to = g + 1 < n ? Math.max(from + 1, gv.getGlyphCharIndex(g + 1)) : chars.length();
                String cs = chars.substring(from, Math.min(Math.max(from + 1, to), chars.length()));
                Point2D p = gv.getGlyphPosition(g);
                laid.add(new Laid(face, face.glyph(code, cs), penX + p.getX() * k + g * run.letterSpacing(),
                        penY + p.getY() * k, run));
            }
            Point2D end = gv.getGlyphPosition(n);
            penX += end.getX() * k + n * run.letterSpacing();
            penY += end.getY() * k;
            chunks.getLast()[1] = laid.size();
            extents.getLast()[1] = penX;
            face.largest = Math.max(face.largest, run.size() * text.scale());
        }
        // each chunk moved as its anchor says
        double[] shift = new double[laid.size()];
        for (int c = 0; c < chunks.size(); c++) {
            int[] ch = chunks.get(c);
            double width = extents.get(c)[1] - extents.get(c)[0];
            double by = ch[2] == 0 ? 0 : ch[2] == 1 ? -width / 2 : -width;
            for (int i = ch[0]; i < ch[1]; i++) {
                shift[i] = by;
            }
        }
        // the text's box in its user units, for a gradient in objectBoundingBox units
        Bounds box = Bounds.EMPTY;
        for (int i = 0; i < laid.size(); i++) {
            Laid l = laid.get(i);
            Shape o = l.face.outlines.get(l.glyph);
            if (o != null) {
                Bounds b = o.bounds();
                double s = l.run.size();
                box = box.include(new Bounds(l.x + shift[i] + b.xMin() * s, l.y + b.yMin() * s,
                        l.x + shift[i] + b.xMax() * s, l.y + b.yMax() * s));
            }
        }
        // a layer for each stretch of one face, size and fill
        String name = text.name().orElse("text " + ++count);
        List<Assembly.Node> out = new ArrayList<>();
        int start = 0;
        for (int i = 1; i <= laid.size(); i++) {
            if (i < laid.size() && alike(laid.get(start), laid.get(i))) {
                continue;
            }
            Assembly.Node node = layer(text, name, laid.subList(start, i), shift, start, box);
            if (node != null) {
                out.add(node);
            }
            start = i;
        }
        return out;
    }

    private static boolean alike(Laid a, Laid b) {
        SvgText.Run p = a.run, q = b.run;
        return a.face == b.face && p.size() == q.size() && p.opacity() == q.opacity()
                && (p.fill() == null ? q.fill() == null : q.fill() != null && p.fill().sameAs(q.fill()));
    }

    /** One text layer of {@code glyphs}, the first of them glyph {@code first} of the text; null if it draws nothing. */
    private Assembly.Node layer(SvgText text, String name, List<Laid> glyphs, double[] shift, int first, Bounds box) {
        SvgText.Run run = glyphs.getFirst().run;
        Face face = glyphs.getFirst().face;
        if (run.fill() == null || run.opacity() <= 0) {
            return null;
        }
        double size = run.size() * text.scale();
        List<Sdg.Placed> placed = new ArrayList<>();
        StringBuilder string = new StringBuilder();
        SvgText.Run last = null;
        Bounds ink = Bounds.EMPTY;   // in canvas units
        for (int i = 0; i < glyphs.size(); i++) {
            Laid l = glyphs.get(i);
            Vec2 pen = text.place(l.x + shift[first + i], l.y);
            double cx = pen.x() - viewBox.xMin(), cy = pen.y() - viewBox.yMin();
            placed.add(new Sdg.Placed(l.glyph, cx, cy));
            if (l.run != last) {
                string.append(l.run.text());
                last = l.run;
            }
            Shape o = face.outlines.get(l.glyph);
            if (o != null) {
                Bounds b = o.bounds();
                ink = ink.include(new Bounds(cx + b.xMin() * size, cy + b.yMin() * size, cx + b.xMax() * size,
                        cy + b.yMax() * size));
            }
        }
        if (ink.isEmpty()) {
            return null;   // spaces alone
        }
        var solid = run.fill().solid();
        if (solid.isPresent()) {
            float[] c = solid.get();
            return new Assembly.Text(name, run.opacity(), face.index, size, placed, string.toString(),
                    Sdg.Paint.Solid.of(c[0], c[1], c[2], 1), -1);
        }
        var colour = run.fill().gradient(box);
        if (colour.isEmpty()) {
            return null;
        }
        // the gradient baked over the layer's ink and a margin, at the field's density, as a shape's is
        double s = options.texelsPerUnit();
        int x0 = (int) Math.floor(ink.xMin() * s) - options.spread(), y0 = (int) Math.floor(ink.yMin() * s)
                - options.spread();
        int x1 = (int) Math.ceil(ink.xMax() * s) + options.spread(), y1 = (int) Math.ceil(ink.yMax() * s)
                + options.spread();
        int w = x1 - x0, h = y1 - y0;
        byte[] rgba = new byte[w * h * 4];
        float[] c = new float[4];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                colour.get().at(viewBox.xMin() + (x0 + x + 0.5) / s, viewBox.yMin() + (y0 + y + 0.5) / s, c);
                float inv = c[3] > 0 ? 1 / c[3] : 0;
                int o = (y * w + x) * 4;
                rgba[o] = SvgToSdg.byte8(c[0] * inv);
                rgba[o + 1] = SvgToSdg.byte8(c[1] * inv);
                rgba[o + 2] = SvgToSdg.byte8(c[2] * inv);
                rgba[o + 3] = SvgToSdg.byte8(c[3]);
            }
        }
        int texture = assembly.pendTexture(new Assembly.Texture(w, h, rgba, x0 / s, y0 / s, s));
        return new Assembly.Text(name, run.opacity(), face.index, size, placed, string.toString(), null, texture);
    }

    private Face face(SvgText.Run run) {
        FontSet.Face f = options.fonts().pick(run.family(), run.weight(), run.italic(),
                families -> warn.accept("no font of the family " + families + "; set in "
                        + options.fonts().faces().getFirst().family() + " instead"));
        if (f == null && options.fonts().faces().isEmpty() && faces.isEmpty()) {
            warn.accept("no fonts were given, so text is set in the JDK's SansSerif, which differs between JDKs");
        }
        Object key = f != null ? f : "jdk";
        Face face = faces.get(key);
        if (face == null) {
            face = new Face(f);
            faces.put(key, face);
        }
        return face;
    }

    private static int anchor(String anchor) {
        return switch (anchor) {
            case "middle" -> 1;
            case "end" -> 2;
            default -> 0;
        };
    }

    /** Every glyph's field made: called once, when all the drawing's text is set. */
    void finish() {
        for (Face f : faces.values()) {
            f.finish();
        }
    }
}
