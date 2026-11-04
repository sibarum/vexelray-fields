package dev.vexelray.fields.fractal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FractalTest {

    @Test
    void theMengerRuleKeepsTwentyOfTwentySeven() {
        assertEquals(20 / 27.0, Level.menger().fill(), 1e-15);
        assertEquals(8 / 27.0, Level.cantorDust().fill(), 1e-15);
        assertEquals(1, Level.menger().weight(0, 1, 0), 0);
        assertEquals(0, Level.menger().weight(1, 1, 0), 0, "two digits of 1: dropped");
        assertEquals(0, Level.menger().weight(1, 1, 1), 0);
    }

    @Test
    void theDigitTestFindsTheSpongeWithNoTreeWalk() {
        Fractal f = Fractal.repeat(Level.menger(), 3, 1);
        assertEquals(0, f.density(0.5, 0.5, 0.5), 0, "the centre");
        assertEquals(0, f.density(0.5, 0.5, 0.05), 0, "a face's tunnel");
        assertEquals(1, f.density(0.01, 0.01, 0.01), 0, "a corner");
        assertEquals(0, f.density(0.5, 0.05 + 1 / 9.0, 0.05), 0, "a hole one level down: digits (1,0,0) then (1,1,0)");
        assertEquals(0, f.density(1, 0.5, 0.5), 0, "outside");
        // every box at full depth: exactly 20³ of 27³ kept
        long top = f.scale(3);
        int kept = 0;
        for (long z = 0; z < top; z++) {
            for (long y = 0; y < top; y++) {
                for (long x = 0; x < top; x++) {
                    kept += (int) f.densityAt(x, y, z);
                }
            }
        }
        assertEquals(8000, kept);
        assertEquals(Math.pow(20 / 27.0, 3), f.tailFill(1), 1e-15);
    }

    @Test
    void aRandomizedLevelKeepsItsChanceAndHoldsStill() {
        Level rule = Level.menger();
        Fractal f = Fractal.repeat(rule, 4, 42).with(2, rule.randomized(0.5));
        assertEquals(Math.pow(20 / 27.0, 4) * 0.5, f.tailFill(1), 1e-15);
        long top = f.scale(2);
        int kept = 0;
        for (long z = 0; z < top; z++) {
            for (long y = 0; y < top; y++) {
                for (long x = 0; x < top; x++) {
                    kept += (int) Fractal.repeat(rule, 2, 42).with(2, rule.randomized(0.5)).densityAt(x, y, z);
                }
            }
        }
        // 400 boxes the rule keeps, each drawn with chance one half
        assertTrue(Math.abs(kept - 200) < 40, "kept " + kept + " of 400");
        Fractal same = Fractal.repeat(rule, 4, 42).with(2, rule.randomized(0.5));
        Fractal other = Fractal.repeat(rule, 4, 43).with(2, rule.randomized(0.5));
        int differ = 0;
        for (long i = 0; i < f.scale(4); i += 7) {
            assertEquals(f.densityAt(i, i / 3, 0), same.densityAt(i, i / 3, 0), 0);
            differ += f.densityAt(i, i / 3, 0) != other.densityAt(i, i / 3, 0) ? 1 : 0;
        }
        assertNotEquals(0, differ, "another seed, another draw");
    }

    @Test
    void levelsShareABase() {
        assertThrows(IllegalArgumentException.class,
                () -> Fractal.of(1, java.util.List.of(Level.menger(), Level.solid(2))));
        assertThrows(IllegalArgumentException.class, () -> Fractal.repeat(Level.menger(), 34, 1),
                "3³⁴ is past a double's integers");
    }
}
