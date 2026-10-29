package dev.vexelray.fields.sdf;

import dev.vexelray.fields.Field;
import dev.vexelray.fields.Framing;
import dev.vexelray.fields.shape.PathData;
import dev.vexelray.fields.shape.Shape;
import dev.vexelray.fields.shape.Vec2;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SdfTest {

    /** A circle of radius 10 round (16, 16), as four cubics: within 0.03% of round. */
    static Shape circle() {
        double k = 0.5522847498 * 10;
        return PathData.parse("M26 16 C26 " + (16 + k) + " " + (16 + k) + " 26 16 26"
                + " C" + (16 - k) + " 26 6 " + (16 + k) + " 6 16"
                + " C6 " + (16 - k) + " " + (16 - k) + " 6 16 6"
                + " C" + (16 + k) + " 6 26 " + (16 - k) + " 26 16 Z");
    }

    @Test
    void aCirclesFieldIsItsDistance() {
        Framing framing = new Framing(1, 0, 0, false);
        Field f = ShapeSdf.generate(circle(), framing, 32, 32);
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 32; x++) {
                double expected = 10 - new Vec2(x + 0.5, y + 0.5).minus(new Vec2(16, 16)).length();
                assertEquals(expected, f.get(x, y, 0), 0.01, x + "," + y);
            }
        }
    }

    @Test
    void windingDoesNotChangeTheSign() {
        Framing framing = new Framing(2, 0, 0, false);
        Field cw = ShapeSdf.generate(PathData.parse("M2 2 H14 V14 H2 Z"), framing, 32, 32);
        Field ccw = ShapeSdf.generate(PathData.parse("M2 2 V14 H14 V2 Z"), framing, 32, 32);
        assertEquals(java.util.Arrays.toString(cw.data()), java.util.Arrays.toString(ccw.data()));
        assertTrue(cw.get(16, 16, 0) > 0);
        assertTrue(cw.get(1, 1, 0) < 0);
        // row 4's centre is at y 2.25, a quarter unit inside the edge: half a texel at two texels a unit
        assertEquals(0.5, cw.get(16, 4, 0), 1e-6);
    }

    @Test
    void aMasksFieldMatchesTheShapes() {
        int over = 8, inner = 24, spread = 4;
        int side = inner * over;
        boolean[] mask = new boolean[side * side];
        for (int y = 0; y < side; y++) {
            for (int x = 0; x < side; x++) {
                double dx = (x + 0.5) / over - 12, dy = (y + 0.5) / over - 12;
                mask[y * side + x] = dx * dx + dy * dy < 81;
            }
        }
        Field f = MaskSdf.of(mask, side, side, over, spread);
        assertEquals(inner + 2 * spread, f.width());
        double worst = 0;
        for (int y = 0; y < f.height(); y++) {
            for (int x = 0; x < f.width(); x++) {
                double expected = 9 - new Vec2(x + 0.5 - spread, y + 0.5 - spread).minus(new Vec2(12, 12)).length();
                if (Math.abs(expected) < spread) {
                    worst = Math.max(worst, Math.abs(expected - f.get(x, y, 0)));
                }
            }
        }
        assertTrue(worst < 0.15, "worst error " + worst + " texels");
    }
}
