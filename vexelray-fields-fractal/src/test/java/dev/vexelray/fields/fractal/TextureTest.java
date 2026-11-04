package dev.vexelray.fields.fractal;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextureTest {

    /** A 4³ texture solid in its lower half along x, empty in the upper. */
    private static Texture step() {
        double[] p = new double[64];
        for (int k = 0; k < 64; k++) {
            p[k] = k % 4 < 2 ? 1 : 0;
        }
        return new Texture(4, p);
    }

    @Test
    void aFullTextureCoversExactlyOneInside() {
        double[] p = new double[512];
        Arrays.fill(p, 1);
        Texture t = new Texture(8, p);
        assertEquals(1, t.at(0.5, 0.5, 0.5, 0.125, Cdf.FAST), 1e-12);
        assertEquals(0.5, t.at(0, 0.5, 0.5, 0.125, Cdf.FAST), 1e-12);
    }

    @Test
    void theInterpolationIsSmoothAcrossTexelFaces() {
        Texture t = step();
        double sigma = 0.25, h = 1e-4, e = 1e-8;
        // at the solid's face, x = 0.5, and at a face between two solid texels, x = 0.25: no crease either side, where
        // trilinear's slope would jump by the step over a texel, 4
        for (double face : new double[]{0.25, 0.5}) {
            double[] left = t.gradient(face - e, 0.5, 0.5, sigma, Cdf.EXACT);
            double[] right = t.gradient(face + e, 0.5, 0.5, sigma, Cdf.EXACT);
            assertEquals(left[0], right[0], 1e-6, "the slope across x = " + face);
        }
        // the gradient is the derivative of the coverage
        for (double x = 0.1; x < 0.9; x += 0.09) {
            double fd = (t.at(x + h, 0.4, 0.6, sigma, Cdf.EXACT) - t.at(x - h, 0.4, 0.6, sigma, Cdf.EXACT)) / (2 * h);
            assertEquals(fd, t.gradient(x, 0.4, 0.6, sigma, Cdf.EXACT)[0], 1e-6);
        }
        assertTrue(t.gradient(0.5, 0.5, 0.5, sigma, Cdf.EXACT)[0] < 0, "coverage falls toward the empty side");
    }

    @Test
    void aTextureIsALevelOfAFractal() {
        Texture t = step();
        Fractal f = Fractal.of(1, List.of(t.level()));
        Coverage c = new Coverage(f, Coverage.Options.DEFAULT.withCdf(Cdf.EXACT));
        for (double x = 0.05; x < 1; x += 0.1) {
            Footprint.Point p = new Footprint.Point(x, 0.5, 0.5, 0.1, Cdf.EXACT);
            assertEquals(t.cover(p), c.exact(p), 1e-12);
        }
        // a level beneath it: solid there, the texture's coverage is unchanged, blurred or not
        Fractal under = Fractal.of(1, List.of(t.level(), Level.solid(4)));
        Coverage blurred = new Coverage(under, Coverage.Options.DEFAULT.withCdf(Cdf.EXACT).withTable(false));
        assertEquals(t.at(0.3, 0.5, 0.5, 0.1, Cdf.EXACT), blurred.at(0.3, 0.5, 0.5, 0.1), 1e-12);
        assertEquals(t.at(0.3, 0.5, 0.5, 0.01, Cdf.EXACT), blurred.at(0.3, 0.5, 0.5, 0.01), 1e-12);
    }
}
