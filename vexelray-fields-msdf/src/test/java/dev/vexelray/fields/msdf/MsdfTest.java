package dev.vexelray.fields.msdf;

import dev.vexelray.fields.Coverage;
import dev.vexelray.fields.Field;
import dev.vexelray.fields.FieldKind;
import dev.vexelray.fields.Framing;
import dev.vexelray.fields.sdf.ShapeSdf;
import dev.vexelray.fields.shape.Bounds;
import dev.vexelray.fields.shape.PathData;
import dev.vexelray.fields.shape.Shape;
import dev.vexelray.fields.shape.Vec2;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MsdfTest {

    /** A star: ten sharp corners, five of them reflex. */
    private static final String STAR = "M50 0 L61 35 L98 35 L68 57 L79 91 L50 70 L21 91 L32 57 L2 35 L39 35 Z";

    @Test
    void cornersSeparateColoursAndEachEdgeHasTwoChannels() {
        Coloring c = Coloring.simple(PathData.parse(STAR).oriented());
        int[] colors = c.colors()[0];
        assertEquals(10, colors.length);
        for (int i = 0; i < colors.length; i++) {
            assertEquals(2, Integer.bitCount(colors[i]));
            assertNotEquals(colors[i], colors[(i + 1) % colors.length], "edges " + i + " and " + (i + 1));
        }
    }

    @Test
    void aSmoothContourIsOneColour() {
        Shape blob = PathData.parse("M0 5 C0 -2 10 -2 10 5 C10 12 0 12 0 5 Z");
        int[] colors = Coloring.simple(blob).colors()[0];
        assertEquals(colors[0], colors[1]);
    }

    @Test
    void aTeardropIsSplitAboutItsCorner() {
        Shape drop = PathData.parse("M0 0 C10 -10 10 10 0 0 Z");   // one edge, one corner
        Coloring c = Coloring.simple(drop);
        assertEquals(3, c.shape().contours().getFirst().edges().size());
        assertEquals(Coloring.WHITE, c.colors()[0][1]);
    }

    @Test
    void theMedianIsTheTrueDistanceAwayFromCorners() {
        Shape shape = PathData.parse("M4 4 H28 V28 H4 Z");
        Framing framing = new Framing(1, 0, 0, false);
        Field m = Msdf.generate(shape, framing, 32, 32);
        Field s = ShapeSdf.generate(shape, framing, 32, 32);
        for (int y = 8; y < 24; y++) {
            for (int x = 8; x < 24; x++) {
                assertEquals(s.get(x, y, 0), m.distance(x, y), 1e-4, x + "," + y);
            }
        }
    }

    @Test
    void anMtsdfKeepsTheTrueDistanceInItsFourthChannel() {
        Shape shape = PathData.parse(STAR);
        Framing framing = Framing.fit(shape.bounds(), 48, 48, 4, false);
        Field t = Msdf.generateMtsdf(shape, framing, 48, 48);
        Field s = ShapeSdf.generate(shape, framing, 48, 48);
        assertEquals(FieldKind.MTSDF, t.kind());
        for (int y = 0; y < 48; y++) {
            for (int x = 0; x < 48; x++) {
                assertEquals(s.get(x, y, 0), t.get(x, y, 3), 1e-3, x + "," + y);
            }
        }
    }

    /** The point of an MSDF: drawn eight times its size, the star's points stay sharp where an SDF's round off. */
    @Test
    void magnifiedCornersStaySharp() {
        Shape shape = PathData.parse(STAR);
        int size = 32, out = 256;
        Framing framing = Framing.fit(shape.bounds(), size, size, 3, false);
        Field msdf = Msdf.generate(shape, framing, size, size);
        Field sdf = ShapeSdf.generate(shape, framing, size, size);

        int msdfWrong = wrongPixels(Coverage.render(msdf, out, out), shape, framing, size, out);
        int sdfWrong = wrongPixels(Coverage.render(sdf, out, out), shape, framing, size, out);
        assertTrue(msdfWrong * 10 < sdfWrong, "msdf " + msdfWrong + " wrong pixels, sdf " + sdfWrong);
    }

    /**
     * Pixels whose coverage is clearly on the wrong side of the shape's own fill at their centre. Not merely on the
     * wrong side of a half: a pixel whose centre is on an edge is half covered, and which side the fill test puts it
     * is a coin toss.
     */
    private static int wrongPixels(float[] coverage, Shape shape, Framing framing, int size, int out) {
        int wrong = 0;
        double k = (double) size / out;
        for (int y = 0; y < out; y++) {
            for (int x = 0; x < out; x++) {
                Vec2 p = framing.toShape((x + 0.5) * k, (y + 0.5) * k);
                float c = coverage[y * out + x];
                if (shape.filled(p) ? c < 0.25 : c > 0.75) {
                    wrong++;
                }
            }
        }
        return wrong;
    }

    @Test
    void overlappingContoursComeOutAsFilled() {
        // two squares overlapping, wound the same way: one filled region
        Shape shape = PathData.parse("M2 2 H18 V18 H2 Z M10 10 H28 V28 H10 Z");
        Framing framing = new Framing(1, 0, 0, false);
        Field m = Msdf.generate(shape, framing, 32, 32);
        Bounds b = shape.bounds();
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 32; x++) {
                Vec2 p = framing.toShape(x + 0.5, y + 0.5);
                if (p.x() > b.xMin() && p.y() > b.yMin()) {
                    assertEquals(shape.filled(p), m.distance(x, y) > 0, x + "," + y);
                }
            }
        }
    }
}
