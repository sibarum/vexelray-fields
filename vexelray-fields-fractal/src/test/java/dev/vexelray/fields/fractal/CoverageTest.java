package dev.vexelray.fields.fractal;

import org.junit.jupiter.api.Test;

import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoverageTest {

    private static final Coverage.Options EXACT = Coverage.Options.DEFAULT.withCdf(Cdf.EXACT);

    @Test
    void aSolidBoxCoversExactlyOneInsideAndExactlyZeroPastThreeSigma() {
        for (Cdf cdf : Cdf.values()) {
            Coverage c = new Coverage(Fractal.repeat(Level.solid(2), 5, 1), Coverage.Options.DEFAULT.withCdf(cdf));
            assertEquals(1, c.at(0.5, 0.5, 0.5, 0.1), 1e-12, "wholly in solid: a hit");
            assertEquals(1, c.at(0.31, 0.5, 0.5, 0.1), 1e-12);
            assertEquals(0, c.at(-0.3, 0.5, 0.5, 0.1), 0, "3σ off the face: nothing, exactly");
            assertEquals(0.5, c.at(0, 0.5, 0.5, 0.1), 1e-12, "on a face: half");
            assertEquals(0.25, c.at(0, 0, 0.5, 0.1), 1e-12, "on an edge: a quarter");
        }
    }

    @Test
    void theBlurStaysWithinAnEightBitStepOfEveryBoxSummed() {
        // c = 0.36, m₀ = 1: the default. Every box to depth 4 is the reference.
        Fractal menger = Fractal.repeat(Level.menger(), 4, 1);
        double[] blur = worst(new Coverage(menger, EXACT.withTable(false)), 300);
        double[] table = worst(new Coverage(menger, EXACT), 300);
        assertTrue(blur[0] < 1.5e-3, "the blur, summed exactly: " + blur[0]);
        assertTrue(table[0] < 3.9e-3, "the blur from the table: " + table[0]);
        assertTrue(table[1] < 1.5e-3, "the table's mean error: " + table[1]);
    }

    @Test
    void keepingMoreLevelsInTheBlurShrinksItsError() {
        Fractal menger = Fractal.repeat(Level.menger(), 4, 1);
        double one = worst(new Coverage(menger, EXACT.withTable(false).withCutoff(0.2, 1)), 200)[0];
        double two = worst(new Coverage(menger, EXACT.withTable(false).withCutoff(0.2, 2)), 200)[0];
        assertTrue(two < one / 10, "m₀ = 1: " + one + ", m₀ = 2: " + two);
        // and a smaller c stops sooner, at a cost in accuracy
        double small = worst(new Coverage(menger, EXACT.withTable(false).withCutoff(0.12, 1)), 200)[0];
        assertTrue(small > one, "c = 0.12: " + small + ", c = 0.2: " + one);
    }

    @Test
    void mZeroFollowsFromCAndTheTolerancesThreshold() {
        assertEquals(1, Coverage.depthFor(3, 1.07 / 3, 1.07));
        assertEquals(1, Coverage.depthFor(3, 0.57, 1.69));
        assertEquals(2, Coverage.depthFor(3, 0.56, 1.69), "1.69 / 0.56 is just past 3");
        assertEquals(2, Coverage.depthFor(3, 0.2, 1.07));
        assertEquals(3, Coverage.depthFor(3, 0.122, 3.27));
    }

    @Test
    void boxesNearTheDepthAreRecursedNotBlurred() {
        // depth 1 with m₀ = 2: no box has two levels below it, so nothing is blurred and coverage is exact
        Fractal shallow = Fractal.repeat(Level.menger(), 1, 1);
        Coverage c = new Coverage(shallow, EXACT.withCutoff(0.05, 2));
        SplittableRandom r = new SplittableRandom(5);
        for (int k = 0; k < 50; k++) {
            Footprint.Point p = new Footprint.Point(r.nextDouble(), r.nextDouble(), r.nextDouble(), 0.2, Cdf.EXACT);
            assertEquals(c.exact(p), c.cover(p), 1e-15);
        }
    }

    @Test
    void aRasterCellIsTheMeanOfTheSamplesInIt() {
        Fractal menger = Fractal.repeat(Level.menger(), 3, 1);
        Coverage c = new Coverage(menger, EXACT.withTable(false));
        double sigma = 0.02;
        Footprint.Cell cell = new Footprint.Cell(0.30, 0.40, 0.10, 0.36, 0.46, 0.16, sigma, Cdf.EXACT);
        int k = 24;
        double sum = 0;
        for (int z = 0; z < k; z++) {
            for (int y = 0; y < k; y++) {
                for (int x = 0; x < k; x++) {
                    sum += c.exact(new Footprint.Point(0.30 + (x + 0.5) * 0.06 / k, 0.40 + (y + 0.5) * 0.06 / k,
                            0.10 + (z + 0.5) * 0.06 / k, sigma, Cdf.EXACT));
                }
            }
        }
        assertEquals(sum / (k * k * k), c.exact(cell), 2e-4);
        assertEquals(c.exact(cell), c.cover(cell), 3e-3);
        float[] raster = c.rasterize(9, sigma);
        assertEquals(9 * 9 * 9, raster.length);
        double mean = 0;
        for (float v : raster) {
            mean += v / raster.length;
        }
        // the cells' means add up to the whole cube's: the sponge's volume less what the blur spills past its faces
        double whole = c.exact(new Footprint.Cell(0, 0, 0, 1, 1, 1, sigma, Cdf.EXACT));
        assertTrue(whole < Math.pow(20 / 27.0, 3));
        assertEquals(whole, mean, 1e-3);
    }

    @Test
    void aRandomizedLevelAveragesToItsWeightedMean() {
        Level rule = Level.menger();
        Footprint.Point p = new Footprint.Point(0.41, 0.23, 0.67, 0.03, Cdf.EXACT);
        Coverage weighted = new Coverage(Fractal.of(0, java.util.List.of(rule, Level.of(3, half(rule)), rule)),
                EXACT.withTable(false));
        double expected = weighted.exact(p);
        double sum = 0;
        int seeds = 400;
        for (int s = 0; s < seeds; s++) {
            Fractal drawn = Fractal.repeat(rule, 3, s).with(2, rule.randomized(0.5));
            Coverage c = new Coverage(drawn, EXACT.withTable(false));
            double once = c.exact(p);
            assertEquals(once, c.exact(p), 0, "one seed, one answer");
            sum += once;
        }
        assertEquals(expected, sum / seeds, 0.02);
    }

    @Test
    void theFastCdfStaysWithinAnEightBitStepOfTheExact() {
        Fractal menger = Fractal.repeat(Level.menger(), 6, 1);
        Coverage fast = Coverage.of(menger), exact = new Coverage(menger, EXACT);
        SplittableRandom r = new SplittableRandom(11);
        double worst = 0;
        for (int k = 0; k < 2000; k++) {
            double sigma = Math.exp(r.nextDouble(Math.log(1e-3), Math.log(0.2)));
            double x = r.nextDouble(), y = r.nextDouble(), z = r.nextDouble();
            worst = Math.max(worst, Math.abs(fast.at(x, y, z, sigma) - exact.at(x, y, z, sigma)));
        }
        assertTrue(worst < 1.5e-3, "fast against exact: " + worst);
    }

    private static double[] half(Level rule) {
        double[] w = new double[27];
        for (int l = 0; l < 3; l++) {
            for (int j = 0; j < 3; j++) {
                for (int i = 0; i < 3; i++) {
                    w[(l * 3 + j) * 3 + i] = rule.weight(i, j, l) * 0.5;
                }
            }
        }
        return w;
    }

    /** The worst and mean error of {@code c} against every box summed, over random samples. */
    private static double[] worst(Coverage c, int count) {
        SplittableRandom r = new SplittableRandom(7);
        double worst = 0, mean = 0;
        for (int k = 0; k < count; k++) {
            double sigma = Math.exp(r.nextDouble(Math.log(0.004), Math.log(0.15)));
            Footprint.Point p = new Footprint.Point(r.nextDouble(), r.nextDouble(), r.nextDouble(), sigma,
                    c.options().cdf());
            double e = Math.abs(c.cover(p) - c.exact(p));
            worst = Math.max(worst, e);
            mean += e / count;
        }
        return new double[]{worst, mean};
    }
}
