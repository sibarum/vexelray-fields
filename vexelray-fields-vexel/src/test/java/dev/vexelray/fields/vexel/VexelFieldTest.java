package dev.vexelray.fields.vexel;

import dev.vexelray.fields.shape.PathData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VexelFieldTest {

    private static final Vec3 UP = new Vec3(0, 0, 1);

    @Test
    void aPlaneIsOneFlatPatchPerCell() {
        // the floor z < 1.3 in a 4-cube of unit cells
        VexelField f = VexelField.bake(Solid.halfSpace(new Vec3(0, 0, 1.3), UP), Vec3.ZERO, 1, 4, 4, 4, 8);
        Vexel v = f.vexel(1, 2, 1);
        assertEquals(0.3, v.occupancy(), 1e-6);
        assertEquals(1, v.surfaceDensity(), 1e-3);
        assertEquals(0, v.roughness(), 1e-6);
        assertEquals(1, v.meanNormal().z(), 1e-6);
        assertEquals(-0.2, v.offsetMean(), 1e-3);
        assertEquals(16, f.area(), 1e-2);
        assertEquals(4 * 4 * 1.3, f.volume(), 1e-4);
    }

    @Test
    void aRayIsStoppedByAPlaneFromAnyAngle() {
        VexelField f = VexelField.bake(Solid.halfSpace(new Vec3(0, 0, 1.3), UP), Vec3.ZERO, 1, 4, 4, 4, 8);
        RayHits down = f.trace(new Vec3(2.2, 2.7, 3.9), new Vec3(0, 0, -1));
        assertEquals(1, down.probability(), 1e-3);
        assertEquals(2.6, down.expectedDistance(), 1e-3);
        RayHits slant = f.trace(new Vec3(0.5, 0.5, 3.5), new Vec3(1, 0.5, -1));
        assertEquals(1, slant.probability(), 0.05);
        assertEquals(1.3, slant.sample(0.5).point().z(), 0.05);
        // parallel to the floor, above it: never stopped
        assertEquals(0, f.trace(new Vec3(0, 2, 2.5), new Vec3(1, 0, 0)).probability(), 1e-9);
    }

    @Test
    void coarseningKeepsVolumeAndAreaAndShowsRoughness() {
        double r = 3;
        VexelField f = VexelField.bake(Solid.sphere(new Vec3(4, 4, 4), r), Vec3.ZERO, 0.25, 32, 32, 32, 6);
        double volume = 4 / 3.0 * Math.PI * r * r * r, area = 4 * Math.PI * r * r;
        assertEquals(volume, f.volume(), volume * 0.01);
        assertEquals(area, f.area(), area * 0.02);
        double before = meanRoughness(f);
        for (int level = 0; level < 3; level++) {
            VexelField c = f.coarser();
            assertEquals(f.volume(), c.volume(), 1e-6 * volume);
            assertEquals(f.area(), c.area(), 1e-6 * area);
            double after = meanRoughness(c);
            assertTrue(after > before, "level " + (level + 1) + ": " + after + " after " + before);
            f = c;
            before = after;
        }
        // one cell of side 8 holding the whole sphere: every way alike, nothing left of its mean normal
        VexelField top = f.coarser().coarser();
        assertEquals(1, top.sizeX());
        assertEquals(1, top.vexel(0, 0, 0).roughness(), 0.02);
    }

    /** The area-weighted mean roughness of the cells with surface. */
    private static double meanRoughness(VexelField f) {
        double sum = 0, weight = 0;
        for (int z = 0; z < f.sizeZ(); z++) {
            for (int y = 0; y < f.sizeY(); y++) {
                for (int x = 0; x < f.sizeX(); x++) {
                    Vexel v = f.vexel(x, y, z);
                    sum += v.roughness() * v.surfaceDensity();
                    weight += v.surfaceDensity();
                }
            }
        }
        return sum / weight;
    }

    @Test
    void aCoarseSphereStillStopsARayThroughItsMiddle() {
        VexelField f = VexelField.bake(Solid.sphere(new Vec3(4, 4, 4), 3), Vec3.ZERO, 0.25, 32, 32, 32, 4);
        VexelField coarse = f.coarser().coarser().coarser();   // cells of 2: the sphere is a few rough cells
        for (VexelField v : new VexelField[]{f, coarse}) {
            RayHits through = v.trace(new Vec3(4, 4, -1), UP);
            assertTrue(through.probability() > 0.95, "p = " + through.probability());
        }
        assertEquals(2, f.trace(new Vec3(4.1, 3.9, -1), UP).expectedDistance(), 0.1);
    }

    @Test
    void attributesAreAveragedOverTheSurface() {
        Solid painted = new Solid() {
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
                return new float[]{normal.z() > 0 ? 1 : 0};   // white above the equator, black below
            }
        };
        VexelField f = VexelField.bake(painted, Vec3.ZERO, 0.5, 8, 8, 8, 4);
        RayHits top = f.trace(new Vec3(2, 2, 3.9), new Vec3(0, 0, -1));
        assertEquals(1, top.sample(0.1).attributes()[0], 1e-6);
        VexelField whole = f.coarser().coarser().coarser();
        Vexel v = whole.vexel(0, 0, 0);
        assertEquals(0.5, v.attributeMean()[0], 0.03);
        assertEquals(0.25, v.attributeVariance()[0], 0.03);
    }

    @Test
    void aShapeIn2dKeepsItsAreaAndOutline() {
        VexelField f = VexelField.bake2d(Solid.prism(PathData.parse("M2 2 H10 V8 H2 Z")), 0, 0, 0.5, 24, 24, 8);
        assertEquals(2, f.dimensions());
        assertEquals(48, f.volume(), 0.1);
        assertEquals(28, f.area(), 0.3);
        RayHits across = f.trace2d(0.1, 5.1, 1, 0, HitModel.SHEET);
        assertEquals(1, across.probability(), 1e-3);
        assertEquals(1.9, across.expectedDistance(), 0.05);
        assertNotNull(across.sample(0.5));
        assertNull(f.trace2d(0.1, 9.1, 1, 0, HitModel.SHEET).sample(0.5));
    }

    @Test
    void scatteredSurfaceNeverStopsARayForCertain() {
        VexelField f = VexelField.bake(Solid.halfSpace(new Vec3(0, 0, 1.5), UP), Vec3.ZERO, 1, 2, 2, 3, 8);
        double p = f.trace(new Vec3(1, 1, 2.9), new Vec3(0, 0, -1), Double.POSITIVE_INFINITY, HitModel.SCATTERED)
                .probability();
        assertEquals(1 - Math.exp(-1), p, 1e-3);
    }
}
