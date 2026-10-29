package dev.vexelray.fields.vexel;

import dev.vexelray.fields.shape.PathData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VexelFieldTest {

    private static final Vec3 UP = new Vec3(0, 0, 1);
    private static final Vec3 DOWN = new Vec3(0, 0, -1);
    private static final VexelHash HASH = new VexelHash(1, 16);

    private static VexelField floor() {
        // the floor z < 1.3 in a 4-cube of unit cells
        return VexelField.bake(Solid.halfSpace(new Vec3(0, 0, 1.3), UP), Vec3.ZERO, 1, 4, 4, 4, 8);
    }

    /** A sphere of radius 3 in an 8-cube of cells of 0.5. */
    private static VexelField sphere() {
        return VexelField.bake(Solid.sphere(new Vec3(4, 4, 4), 3), Vec3.ZERO, 0.5, 16, 16, 16, 4);
    }

    @Test
    void aPlaneIsAFlatCellOfFullCoverage() {
        VexelField f = floor();
        Vexel v = f.vexel(1, 2, 1);
        assertEquals(0, v.granularity(), 1e-6);
        assertEquals(0, v.rate(), 1e-6);
        assertEquals(1, v.coverage(), 1e-3);
        assertEquals(1, v.normal().z(), 1e-6);
        assertEquals(-0.2, v.offset(), 1e-3);
        assertEquals(1, v.materials()[0], 1e-6);
        assertEquals(0.3, v.occupancy(), 1e-6);
        assertEquals(1, v.surfaceDensity(), 1e-3);
        assertEquals(16, f.area(), 1e-2);
        assertEquals(4 * 4 * 1.3, f.volume(), 1e-4);
    }

    @Test
    void aPlaneStopsEveryRayThatCrossesItAtAnyAngle() {
        VexelField f = floor();
        RayHits down = f.trace(new Vec3(2.2, 2.7, 3.9), DOWN);
        assertEquals(1, down.probability(), 1e-3);
        assertEquals(2.6, down.expectedDistance(), 1e-3);
        Vec3 slant = new Vec3(1, 0.5, -1);
        Vec3 from = new Vec3(0.5, 0.5, 3.5);
        assertEquals(1, f.trace(from, slant).probability(), 1e-3);
        Collision c = f.collide(from, slant, Double.POSITIVE_INFINITY, HASH);
        assertFalse(c.granular());
        assertEquals(1.3, c.point().z(), 1e-3);
        assertEquals(1, c.normal().z(), 1e-6);
        // parallel to the floor, above it: never stopped
        assertEquals(0, f.trace(new Vec3(0, 2, 2.5), new Vec3(1, 0, 0)).probability(), 1e-9);
        assertNull(f.collide(new Vec3(0, 2, 2.5), new Vec3(1, 0, 0), Double.POSITIVE_INFINITY, HASH));
    }

    @Test
    void aPlaneOnAFaceBetweenCellsBelongsToOne() {
        // z < 2 exactly: the surface lies on the face between layers 1 and 2
        VexelField f = VexelField.bake(Solid.halfSpace(new Vec3(0, 0, 2), UP), Vec3.ZERO, 1, 2, 2, 4, 8);
        assertEquals(0, f.vexel(0, 0, 1).surfaceDensity(), 1e-9);
        assertEquals(1, f.vexel(0, 0, 2).coverage(), 1e-3);
        assertEquals(1, f.trace(new Vec3(0.5, 0.5, 3.9), DOWN).probability(), 1e-3);
    }

    @Test
    void coarseningKeepsVolumeAndAreaAndTurnsDetailGranular() {
        double r = 3;
        VexelField f = VexelField.bake(Solid.sphere(new Vec3(4, 4, 4), r), Vec3.ZERO, 0.25, 32, 32, 32, 6);
        double volume = 4 / 3.0 * Math.PI * r * r * r, area = 4 * Math.PI * r * r;
        assertEquals(volume, f.volume(), volume * 0.01);
        assertEquals(area, f.area(), area * 0.02);
        double before = meanGranularity(f);
        assertTrue(before < 0.1, "a finely baked sphere is nearly all flat: " + before);
        for (int level = 0; level < 4; level++) {
            VexelField c = f.coarser();
            assertEquals(f.volume(), c.volume(), 1e-5 * volume);
            assertEquals(f.area(), c.area(), 1e-5 * area);
            double after = meanGranularity(c);
            assertTrue(after > before, "level " + (level + 1) + ": " + after + " after " + before);
            f = c;
            before = after;
        }
        // one cell of side 8 holding the whole sphere: a small cloud, its rate the Crofton count of its area
        VexelField top = f.coarser();
        assertEquals(1, top.sizeX());
        Vexel v = top.vexel(0, 0, 0);
        assertEquals(1, v.granularity(), 0.02);
        // grains entered from the free space between them: a quarter of the Crofton count, over the free share
        assertEquals(area / 512 / 4 / (1 - volume / 512), v.rate(), 0.03 * v.rate());
        // and where the solid is: the ball's centroid, and its mean squared radius, 3/5 r²
        assertEquals(0, v.solidCentroid().length(), 0.02);
        assertEquals(0.6 * r * r, v.solidSpread(), 0.03 * 0.6 * r * r);
    }

    @Test
    void aCellKnowsWhereItsSolidIs() {
        Vexel v = floor().vexel(1, 2, 1);   // solid from z 1 to 1.3 of a cell from 1 to 2
        assertEquals(-0.35, v.solidCentroid().z(), 1e-3);
        assertEquals(0, v.solidCentroid().x(), 1e-6);
        assertEquals(2 / 12.0 + 0.09 / 12, v.solidSpread(), 1e-3);
        assertEquals(0, floor().vexel(1, 2, 3).occupancy(), 1e-9);
    }

    /** The area-weighted mean granularity of the cells with surface. */
    private static double meanGranularity(VexelField f) {
        double sum = 0, weight = 0;
        for (int z = 0; z < f.sizeZ(); z++) {
            for (int y = 0; y < f.sizeY(); y++) {
                for (int x = 0; x < f.sizeX(); x++) {
                    Vexel v = f.vexel(x, y, z);
                    sum += v.granularity() * v.surfaceDensity();
                    weight += v.surfaceDensity();
                }
            }
        }
        return sum / weight;
    }

    /** The sphere as one granular cell is still solid where the sphere is, and empty past it. */
    @Test
    void aCoarseSolidStopsRaysThroughItAndNotPastIt() {
        VexelField cloud = sphere().coarser().coarser().coarser().coarser();
        assertEquals(1, cloud.sizeX());
        assertEquals(1, cloud.vexel(0, 0, 0).granularity(), 0.02);
        double middle = cloud.trace(new Vec3(4, 4, -1), UP).probability();
        double past = cloud.trace(new Vec3(0.5, 0.5, -1), UP).probability();   // 4.9 from the axis: misses
        assertTrue(middle > 0.9, "through the middle: " + middle);
        assertTrue(past < 0.02, "past the edge: " + past);
        assertEquals(4, cloud.trace(new Vec3(4, 4, -1), UP).expectedDistance(), 1.0);
    }

    @Test
    void aGranularCellStopsARayWithNoNormal() {
        VexelField cloud = sphere().coarser().coarser().coarser().coarser();
        int granular = 0, hits = 0;
        for (long seed = 0; seed < 400; seed++) {
            Collision c = cloud.collide(new Vec3(4, 4, -1), UP, Double.POSITIVE_INFINITY, new VexelHash(seed, 16));
            if (c != null) {
                hits++;
                if (c.granular()) {
                    granular++;
                    assertEquals(Vec3.ZERO, c.normal());
                }
            }
        }
        assertTrue(granular > hits * 0.9, granular + " of " + hits + " collisions granular");
    }

    @Test
    void hashedCollisionsAverageToTheExpectedOutcome() {
        VexelField f = sphere().coarser().coarser();   // cells of 2: partly flat, partly granular
        Vec3[][] rays = {
                {new Vec3(4.3, 3.6, -1), UP},
                {new Vec3(-1, 4.2, 5.1), new Vec3(1, 0.1, -0.2)},
                {new Vec3(0.2, 0.3, 0.1), new Vec3(1, 1, 1)},
        };
        for (Vec3[] ray : rays) {
            double expected = f.trace(ray[0], ray[1]).probability();
            int n = 4000, hit = 0;
            for (long seed = 0; seed < n; seed++) {
                if (f.collide(ray[0], ray[1], Double.POSITIVE_INFINITY, new VexelHash(seed, 16)) != null) {
                    hit++;
                }
            }
            assertEquals(expected, (double) hit / n, 0.03, "ray from " + ray[0]);
        }
    }

    @Test
    void anOutcomeHoldsStillWithinAnAngleBucket() {
        VexelField f = sphere().coarser().coarser();
        VexelHash hash = new VexelHash(42, 8);
        Vec3 from = new Vec3(4.1, 4.2, -1);
        Vec3 a = new Vec3(0.001, 0.002, 1), b = new Vec3(0.003, 0.001, 1);
        assertEquals(hash.bucket(a.normalize(), 3), hash.bucket(b.normalize(), 3));
        Collision ca = f.collide(from, a, 100, hash), cb = f.collide(from, b, 100, hash);
        assertEquals(ca, f.collide(from, a, 100, hash));
        assertEquals(ca == null, cb == null);
        if (ca != null) {
            assertEquals(ca.material(), cb.material());
            assertEquals(ca.granular(), cb.granular());
            assertEquals(ca.z(), cb.z());
        }
        assertNotEquals(hash.bucket(UP, 3), hash.bucket(DOWN, 3));
        assertNotEquals(hash.u(1, 2, 3, 4, 0), new VexelHash(43, 8).u(1, 2, 3, 4, 0));
    }

    @Test
    void materialsAreTheirShareOfTheSurface() {
        Solid painted = new Solid() {
            private final Solid ball = Solid.sphere(new Vec3(2, 2, 2), 1.5);

            @Override
            public double distance(double x, double y, double z) {
                return ball.distance(x, y, z);
            }

            @Override
            public int materials() {
                return 2;
            }

            @Override
            public int material(Vec3 p, Vec3 normal) {
                return normal.z() > 0 ? 1 : 0;   // material 1 above the equator, 0 below
            }
        };
        VexelField f = VexelField.bake(painted, Vec3.ZERO, 0.5, 8, 8, 8, 4);
        int stopped = 0;
        for (long seed = 0; seed < 50; seed++) {
            VexelHash hash = new VexelHash(seed, 16);
            Collision top = f.collide(new Vec3(2.2, 2.1, 3.9), DOWN, Double.POSITIVE_INFINITY, hash);
            Collision bottom = f.collide(new Vec3(2.2, 2.1, 0.1), UP, Double.POSITIVE_INFINITY, hash);
            if (top != null && bottom != null) {
                stopped++;
                assertEquals(1, top.material());
                assertEquals(0, bottom.material());
            }
        }
        assertTrue(stopped >= 45, stopped + " of 50 seeds stopped both rays");
        Vexel whole = f.coarser().coarser().coarser().vexel(0, 0, 0);
        assertEquals(0.5, whole.materials()[0], 0.03);
        assertEquals(0.5, whole.materials()[1], 0.03);
    }

    @Test
    void attributesAreAveragedOverTheSurface() {
        Solid tinted = new Solid() {
            private final Solid ball = Solid.sphere(new Vec3(2, 2, 2), 1.5);

            @Override
            public double distance(double x, double y, double z) {
                return ball.distance(x, y, z);
            }

            @Override
            public int attributes() {
                return 1;
            }

            @Override
            public float[] attributes(Vec3 p, Vec3 normal) {
                return new float[]{normal.z() > 0 ? 1 : 0};
            }
        };
        Vexel v = VexelField.bake(tinted, Vec3.ZERO, 0.5, 8, 8, 8, 4).coarser().coarser().coarser().vexel(0, 0, 0);
        assertEquals(0.5, v.attributeMean()[0], 0.03);
        assertEquals(0.25, v.attributeVariance()[0], 0.03);
    }

    @Test
    void aShapeIn2dKeepsItsAreaAndOutline() {
        VexelField f = VexelField.bake2d(Solid.prism(PathData.parse("M2 2 H10 V8 H2 Z")), 0, 0, 0.5, 24, 24, 8);
        assertEquals(2, f.dimensions());
        assertEquals(48, f.volume(), 0.1);
        assertEquals(28, f.area(), 0.3);
        RayHits across = f.trace2d(0.1, 5.1, 1, 0);
        assertEquals(1, across.probability(), 1e-3);
        assertEquals(1.9, across.expectedDistance(), 1e-3);
        Collision c = f.collide2d(0.1, 5.1, 1, 0, HASH);
        assertNotNull(c);
        assertEquals(2, c.point().x(), 1e-3);
        assertEquals(-1, c.normal().x(), 1e-6);
        assertNull(f.collide2d(0.1, 9.1, 1, 0, HASH));
    }
}
