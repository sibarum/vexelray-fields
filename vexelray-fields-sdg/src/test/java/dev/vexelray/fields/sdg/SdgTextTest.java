package dev.vexelray.fields.sdg;

import dev.vexelray.fields.art.Raster;
import dev.vexelray.fields.art.Svg;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.TextAttribute;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SVG text as SDG 1.3 text layers. Set in the JDK's own SansSerif, the one font every JDK has: these tests hold the
 * encoder to itself and to the JDK's drawing of the same font, not to any one font's shapes.
 */
class SdgTextTest {

    private final List<String> warnings = new ArrayList<>();

    private Sdg convert(String svg, int texels) {
        Svg s = Svg.parse(svg);
        return SvgToSdg.convert(s, SvgToSdg.Options.across(s, texels).withWarnings(warnings::add));
    }

    private static String svg(String body) {
        return "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 200 60\">" + body + "</svg>";
    }

    @Test
    void textBecomesATextLayer() {
        Sdg sdg = convert(svg("<text id=\"title\" x=\"10\" y=\"40\" font-size=\"32\" fill=\"#204080\">Hello, SDG</text>"),
                256);
        assertEquals(1, sdg.layers().size());
        Sdg.Layer l = sdg.layers().getFirst();
        assertTrue(l.isText());
        assertEquals("title", l.name());
        assertEquals("Hello, SDG", l.text().string());
        assertEquals(new Sdg.Paint.Solid(0x204080FF), l.paint());
        assertEquals(32, l.text().size());
        assertEquals(10, l.text().glyphs().size());
        assertEquals(1, sdg.fonts().size());
        // one glyph each, however often it is set: "l" twice, nine distinct characters
        assertEquals(9, sdg.fonts().getFirst().glyphs().size());
        assertEquals(10, l.text().glyphs().getFirst().x(), 1e-9);
        assertEquals(40, l.text().glyphs().getFirst().y(), 1e-9);
        // the space has no field; the rest do
        long blank = sdg.fonts().getFirst().glyphs().stream().filter(g -> g.field() == null).count();
        assertEquals(1, blank);
        assertTrue(warnings.stream().anyMatch(w -> w.contains("SansSerif")), warnings.toString());
        // both forms carry it
        assertEquals(sdg.layers(), SdgFile.read(SdgFile.bytes(sdg)).layers());
        assertEquals(sdg.fonts(), SdgFile.read(SdgFile.xml(sdg).getBytes(StandardCharsets.UTF_8)).fonts());
    }

    /** Drawn, it is the JDK's own drawing of the same string, edge for edge, within antialiasing's give. */
    @Test
    void itDrawsAsTheFontDoes() {
        String text = "Rgb Hello";
        Sdg sdg = convert(svg("<text x=\"10\" y=\"42\" font-size=\"36\">" + text + "</text>"), 512);
        int w = 400, h = 120;   // two pixels a unit
        Raster r = SdgRender.render(sdg, w, h, 0, 0, 0.5);

        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setColor(Color.BLACK);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 1).deriveFont(72f)
                .deriveFont(Map.of(TextAttribute.KERNING, TextAttribute.KERNING_ON,
                        TextAttribute.LIGATURES, TextAttribute.LIGATURES_ON)));
        g.drawString(text, 20f, 84f);
        g.dispose();

        long diff = 0, ink = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int a = r.rgba()[(y * w + x) * 4 + 3] & 0xFF, b = img.getRGB(x, y) >>> 24;
                diff += Math.abs(a - b);
                ink += b;
            }
        }
        assertTrue(ink > 0);
        // the coverage that differs, against all the coverage there is: edges a fraction of a pixel apart at most
        double off = (double) diff / ink;
        assertTrue(off < 0.06, "differs by " + off + " of its ink");
    }

    @Test
    void anAnchorMovesItsChunk() {
        Sdg start = convert(svg("<text x=\"100\" y=\"40\" font-size=\"20\">Middle</text>"), 128);
        Sdg middle = convert(svg("<text x=\"100\" y=\"40\" font-size=\"20\" text-anchor=\"middle\">Middle</text>"), 128);
        Sdg end = convert(svg("<text x=\"100\" y=\"40\" font-size=\"20\" text-anchor=\"end\">Middle</text>"), 128);
        double x0 = start.layers().getFirst().text().glyphs().getFirst().x();
        double x1 = middle.layers().getFirst().text().glyphs().getFirst().x();
        double x2 = end.layers().getFirst().text().glyphs().getFirst().x();
        assertEquals(100, x0, 1e-9);
        assertTrue(x2 < x1 && x1 < x0);
        assertEquals(x0 - x1, x1 - x2, 1e-9);   // the middle is half the way
    }

    @Test
    void aTspanOfAnotherFillIsALayerOfItsOwn() {
        Sdg sdg = convert(svg("<text x=\"4\" y=\"40\" font-size=\"20\">One <tspan fill=\"red\">two</tspan> three"
                + "<tspan dy=\"-8\" font-size=\"10\">4</tspan></text>"), 128);
        List<String> strings = sdg.layers().stream().map(l -> l.text().string()).toList();
        assertEquals(List.of("One ", "two", " three", "4"), strings);
        assertEquals(new Sdg.Paint.Solid(0xFF0000FF), sdg.layers().get(1).paint());
        // each follows on from the one before it
        double endOfOne = sdg.layers().get(0).text().glyphs().getLast().x();
        double startOfTwo = sdg.layers().get(1).text().glyphs().getFirst().x();
        assertTrue(startOfTwo > endOfOne);
        assertEquals(32, sdg.layers().get(3).text().glyphs().getFirst().y(), 1e-9);
        assertEquals(10, sdg.layers().get(3).text().size());
    }

    @Test
    void whiteSpaceIsCollapsedAsSvgDoes() {
        Sdg sdg = convert(svg("<text x=\"4\" y=\"40\">\n    a \t\n   b    </text>"), 64);
        assertEquals("a b", sdg.layers().getFirst().text().string());
    }

    @Test
    void positionsListedPlaceEachCharacter() {
        Sdg sdg = convert(svg("<text x=\"10 50 90\" y=\"40\" font-size=\"20\">abcd</text>"), 64);
        List<Double> xs = new ArrayList<>();
        for (Sdg.Layer l : sdg.layers()) {
            for (Sdg.Placed p : l.text().glyphs()) {
                xs.add(p.x());
            }
        }
        assertEquals(4, xs.size());
        assertEquals(10, xs.get(0), 1e-9);
        assertEquals(50, xs.get(1), 1e-9);
        assertEquals(90, xs.get(2), 1e-9);
        assertTrue(xs.get(3) > 90);   // the last follows on
    }

    @Test
    void aGradientIsBakedAsAShapesIs() {
        Sdg sdg = convert(svg("<defs><linearGradient id=\"g\"><stop offset=\"0\" stop-color=\"#f00\"/>"
                + "<stop offset=\"1\" stop-color=\"#00f\"/></linearGradient></defs>"
                + "<text x=\"10\" y=\"40\" font-size=\"32\" fill=\"url(#g)\">Fade</text>"), 128);
        Sdg.Layer l = sdg.layers().getFirst();
        assertTrue(l.isText());
        Sdg.Paint.Sampled p = assertInstanceOf(Sdg.Paint.Sampled.class, l.paint());
        Raster r = SdgRender.render(sdg, 400, 120, 0, 0, 0.5);
        // red at the left of the word, blue at the right
        int[] left = firstInk(r, true), right = firstInk(r, false);
        assertTrue(left[0] > left[2], "left " + java.util.Arrays.toString(left));
        assertTrue(right[2] > right[0], "right " + java.util.Arrays.toString(right));
        assertTrue(p.width() > 0);
    }

    /** The colour of the first fully covered pixel from the left, or the right. */
    private static int[] firstInk(Raster r, boolean fromLeft) {
        for (int k = 0; k < r.width(); k++) {
            int x = fromLeft ? k : r.width() - 1 - k;
            for (int y = 0; y < r.height(); y++) {
                int o = (y * r.width() + x) * 4;
                if ((r.rgba()[o + 3] & 0xFF) == 255) {
                    return new int[]{r.rgba()[o] & 0xFF, r.rgba()[o + 1] & 0xFF, r.rgba()[o + 2] & 0xFF};
                }
            }
        }
        throw new AssertionError("no ink");
    }

    @Test
    void textOnlyMovedAndScaledIsTaken() {
        Sdg sdg = convert(svg("<g transform=\"translate(5 6) scale(2)\"><text x=\"1\" y=\"10\" font-size=\"8\">x</text></g>"),
                64);
        Sdg.Text t = sdg.layers().getFirst().text();
        assertEquals(16, t.size(), 1e-9);
        assertEquals(7, t.glyphs().getFirst().x(), 1e-9);
        assertEquals(26, t.glyphs().getFirst().y(), 1e-9);
        for (String turned : new String[]{"rotate(10)", "skewX(5)", "scale(1 2)", "scale(-1 1)"}) {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> convert(
                    svg("<text transform=\"" + turned + "\" y=\"20\">no</text>"), 64));
            assertTrue(e.getMessage().contains("<text>"), e.getMessage());
        }
        assertThrows(IllegalArgumentException.class, () -> convert(svg("<text y=\"20\" stroke=\"red\">no</text>"), 64));
        assertThrows(IllegalArgumentException.class, () -> convert(
                svg("<path id=\"p\" d=\"M0 0 L100 0\"/><text><textPath href=\"#p\">no</textPath></text>"), 64));
    }

    /** Noto Sans from the sibling vexelray checkout, where the stack keeps it; a test needing it is skipped without. */
    private static FontSet noto() throws java.io.IOException {
        java.nio.file.Path noto = java.nio.file.Path.of(System.getProperty("user.dir"))
                .resolve("../../vexelray/vexelray-text/fonts/NotoSans-Regular.ttf").normalize();
        org.junit.jupiter.api.Assumptions.assumeTrue(java.nio.file.Files.exists(noto), "no " + noto);
        return FontSet.of(noto);
    }

    @Test
    void aCharacterWithNoGlyphIsSaidSo() throws java.io.IOException {
        Svg s = Svg.parse(svg("<text y=\"20\">ab</text>"));
        SvgToSdg.convert(s, SvgToSdg.Options.across(s, 64).withFonts(noto()).withWarnings(warnings::add));
        assertTrue(warnings.stream().anyMatch(w -> w.contains("no glyph") && w.contains("U+E000")), warnings.toString());
    }

    /** A real face: its names, license and weight from its tables, and the text set in it rather than the JDK's. */
    @Test
    void aFontFileIsReadForItsNamesAndRights() throws java.io.IOException {
        FontSet fonts = noto();
        FontSet.Face face = fonts.faces().getFirst();
        assertEquals("Noto Sans", face.family());
        assertEquals("Regular", face.style());
        assertEquals(400, face.weight());
        assertFalse(face.italic());
        assertTrue(face.license().contains("Open Font License"), face.license());

        Svg s = Svg.parse(svg("<text y=\"40\" font-family=\"'Noto Sans', sans-serif\" font-weight=\"bold\">Hi</text>"
                + "<text y=\"50\" font-family=\"Comic Sans MS\">Hi</text>"));
        Sdg sdg = SvgToSdg.convert(s, SvgToSdg.Options.across(s, 64).withFonts(fonts).withWarnings(warnings::add));
        assertEquals(1, sdg.fonts().size());   // the one face, bold asked or not, the missing family too
        assertEquals("Noto Sans", sdg.fonts().getFirst().family());
        assertTrue(sdg.fonts().getFirst().license().contains("Open Font License"));
        assertEquals(List.of("no font of the family Comic Sans MS; set in Noto Sans instead"), warnings);
    }

    @Test
    void spacesAloneDrawNothing() {
        Sdg sdg = convert(svg("<rect width=\"10\" height=\"10\"/><text y=\"20\" xml:space=\"preserve\"> </text>"), 64);
        assertEquals(1, sdg.layers().size());
        assertFalse(sdg.layers().getFirst().isText());
    }
}
