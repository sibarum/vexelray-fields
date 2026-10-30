package dev.vexelray.fields.vexel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmokeAndGlitterTest {

    private static final Vec3 UP = new Vec3(0, 0, 1);
    private static final Vec3 DOWN = new Vec3(0, 0, -1);

    /** Even smoke of rate 0.4 filling an 8-cube. */
    private static VexelField fog() {
        return VexelField.bake(Solid.empty(), (x, y, z) -> 0.4, Vec3.ZERO, 1, 8, 8, 8, 2);
    }

    @Test
    void evenSmokeLetsThroughWhatBeersLawSays() {
        VexelField f = fog();
        assertEquals(0.4, f.vexel(3, 3, 3).smoke(), 1e-6);
        assertEquals(1, f.vexel(3, 3, 3).granularity(), 1e-9);   // no surface: nothing flat
        assertEquals(Math.exp(-0.4 * 8), f.transmittance(new Vec3(4, 4, -1), UP, 100), 1e-6);
        assertEquals(Math.exp(-0.4 * 3), f.transmittance(new Vec3(4, 4, 0.5), UP, 3), 1e-6);
        assertEquals(1 - Math.exp(-0.4 * 8), f.trace(new Vec3(4, 4, -1), UP).probability(), 1e-6);
    }

    @Test
    void hashedStopsInSmokeAreSmokeAndAverageToTheTrace() {
        VexelField f = fog();
        int n = 4000, stopped = 0;
        double t = 0;
        for (long seed = 0; seed < n; seed++) {
            Collision c = f.collide(new Vec3(4.3, 3.7, -1), new Vec3(0.1, 0, 1), 100, new VexelHash(seed, 16));
            if (c != null) {
                stopped++;
                t += c.t();
                assertTrue(c.granular() && c.smoke(), "a stop in smoke is smoke's");
                assertEquals(Vec3.ZERO, c.normal());
            }
        }
        double expected = f.trace(new Vec3(4.3, 3.7, -1), new Vec3(0.1, 0, 1)).probability();
        assertEquals(expected, (double) stopped / n, 0.03);
        assertEquals(f.trace(new Vec3(4.3, 3.7, -1), new Vec3(0.1, 0, 1)).expectedDistance(), t / stopped, 0.15);
    }

    @Test
    void expectedHitsSayWhatMadeThem() {
        // in fog every stop is smoke's, and the composite of them all is the fog's opacity
        RayHits fog = fog().trace(new Vec3(4, 4, -1), UP);
        assertTrue(fog.hits().stream().allMatch(h -> Math.abs(h.smoke() - 1) < 1e-9 && h.grains() == 0));
        assertEquals(1 - Math.exp(-0.4 * 8), fog.probability(), 1e-6);
        // in fog over a floor, the floor's own share is flat
        VexelField f = VexelField.bake(Solid.halfSpace(new Vec3(0, 0, 2), UP), (x, y, z) -> z > 2 ? 0.25 : 0,
                Vec3.ZERO, 1, 4, 4, 8, 4);
        RayHits down = f.trace(new Vec3(2.2, 1.7, 7.9), DOWN);
        double smoke = 0, flat = 0;
        for (RayHits.Hit h : down.hits()) {
            smoke += h.probability() * h.smoke();
            flat += h.probability() * h.flat();
        }
        assertEquals(1 - Math.exp(-0.25 * 5.9), smoke, 0.01);
        assertEquals(Math.exp(-0.25 * 5.9), flat, 0.01);
    }

    @Test
    void coarseningKeepsTheMeanRate() {
        // smoke only in the lower half: the one cell above it has half the rate
        VexelField f = VexelField.bake(Solid.empty(), (x, y, z) -> z < 4 ? 0.6 : 0, Vec3.ZERO, 1, 8, 8, 8, 2);
        VexelField top = f.coarser().coarser().coarser();
        assertEquals(0.3, top.vexel(0, 0, 0).smoke(), 1e-6);
        // and the expected count straight down is kept: 0.6 over 4 units
        assertEquals(Math.exp(-0.6 * 4), f.transmittance(new Vec3(4, 4, 9), DOWN, 100), 1e-6);
        assertEquals(Math.exp(-0.3 * 8), top.transmittance(new Vec3(4, 4, 9), DOWN, 100), 1e-6);
    }

    @Test
    void fogOverAFloorStopsSomeRaysBeforeTheFloor() {
        VexelField f = VexelField.bake(Solid.halfSpace(new Vec3(0, 0, 2), UP), (x, y, z) -> z > 2 ? 0.25 : 0,
                Vec3.ZERO, 1, 4, 4, 8, 4);
        int n = 3000, smoke = 0, floor = 0;
        for (long seed = 0; seed < n; seed++) {
            Collision c = f.collide(new Vec3(2.2, 1.7, 7.9), DOWN, 100, new VexelHash(seed, 16));
            assertNotNull(c, "nothing gets past the floor");
            if (c.smoke()) {
                smoke++;
            } else {
                floor++;
                assertEquals(2, c.point().z(), 1e-6);
                assertEquals(1, c.normal().z(), 1e-6);
            }
        }
        assertEquals(1 - Math.exp(-0.25 * 5.9), (double) smoke / n, 0.03);   // 5.9 units of fog above the floor
        assertEquals(n, smoke + floor);
    }

    @Test
    void aFlakeHoldsStillWithinItsBucketAndItsCube() {
        Glitter g = new Glitter(0.1, 1, 200, new VexelHash(3, 16));
        Vec3 p = new Vec3(1.23, 4.56, 7.89), q = new Vec3(1.21, 4.58, 7.81);   // the same 0.1 cube
        Vec3 view = new Vec3(0.3, -0.2, -1);
        assertEquals(g.flake(p, view, 3), g.flake(q, view, 3));
        assertEquals(g.flake(p, view, 3), g.flake(p, view.plus(new Vec3(0.001, 0, 0)), 3));
        assertEquals(1, g.flake(p, view, 3).length(), 1e-12);
        assertNull(new Glitter(0.1, 0, 200, new VexelHash(3, 16)).flake(p, view, 3), "no mirrors, no flakes");
    }

    @Test
    void sharperGlitterFlashesLess() {
        Vec3 view = new Vec3(0, 0.6, -1), light = new Vec3(0.2, 1, 1);
        double soft = 0, sharp = 0;
        for (int i = 0; i < 5000; i++) {
            Vec3 p = new Vec3(i * 0.37, (i * 7) % 13 * 0.41, i % 5 * 0.53);
            soft += new Glitter(0.1, 0.5, 5, new VexelHash(1, 16)).flash(p, view, light, 3) > 0.5 ? 1 : 0;
            sharp += new Glitter(0.1, 0.5, 300, new VexelHash(1, 16)).flash(p, view, light, 3) > 0.5 ? 1 : 0;
        }
        assertTrue(sharp < soft / 5 && sharp > 0, "sharp " + sharp + " soft " + soft);
        assertTrue(soft < 2500 * 1.1, "only the mirror half can flash: " + soft);
    }
}
