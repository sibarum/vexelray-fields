package dev.vexelray.fields.fractal;

import org.junit.jupiter.api.Test;

import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GaussTest {

    @Test
    void theExactCdfIsTheErrorFunction() {
        assertEquals(0.5204998778130465, Cdf.erf(0.5), 1e-15);
        assertEquals(0.8427007929497149, Cdf.erf(1), 1e-15);
        assertEquals(-0.9953222650189527, Cdf.erf(-2), 1e-15);
        assertEquals(0.9999779095030014, Cdf.erf(3), 1e-15);
        assertEquals(0.5, Cdf.EXACT.phi(0), 0);
    }

    @Test
    void theFastCdfIsWithinOnePointFourTenThousandthsAndSymmetric() {
        double worst = 0;
        for (double z = -8; z <= 8; z += 1e-4) {
            worst = Math.max(worst, Math.abs(Cdf.FAST.phi(z) - Cdf.EXACT.phi(z)));
            assertEquals(1, Cdf.FAST.phi(z) + Cdf.FAST.phi(-z), 1e-15);
        }
        assertTrue(worst < 1.42e-4 && worst > 1.40e-4, "the logistic-cubic's worst error: " + worst);
    }

    @Test
    void eachCdfsDensityIsItsOwnSlope() {
        for (Cdf c : Cdf.values()) {
            for (double z = -4; z <= 4; z += 0.37) {
                double h = 1e-5, slope = (c.phi(z + h) - c.phi(z - h)) / (2 * h);
                assertEquals(slope, c.pdf(z), 1e-9, c + " at " + z);
            }
        }
    }

    @Test
    void theTruncatedFootprintHoldsAllItsMassWithinThreeSigma() {
        for (Cdf c : Cdf.values()) {
            assertEquals(1, Gauss.mass(c, 0.4 - 0.3, 0.4 + 0.3, 0.4, 0.1), 0);
            assertEquals(0, Gauss.mass(c, 0.71, 2, 0.4, 0.1), 0, "past 3σ, nothing");
            assertEquals(0.5, Gauss.mass(c, -1, 0.4, 0.4, 0.1), 1e-15);
            // a partition of the line sums to one
            double sum = 0;
            for (double a = -1; a < 1; a += 0.05) {
                sum += Gauss.mass(c, a, a + 0.05, 0.123, 0.07);
            }
            assertEquals(1, sum, 1e-12);
        }
    }

    @Test
    void theSlopeIsTheDerivativeOfTheMass() {
        for (Cdf c : Cdf.values()) {
            for (double mu = -0.2; mu <= 0.8; mu += 0.0731) {
                double h = 1e-6;
                double fd = (Gauss.mass(c, 0.1, 0.35, mu + h, 0.09) - Gauss.mass(c, 0.1, 0.35, mu - h, 0.09)) / (2 * h);
                assertEquals(fd, Gauss.slope(c, 0.1, 0.35, mu, 0.09), 1e-6, c + " at " + mu);
            }
        }
    }

    @Test
    void aRasterCellHoldsTheMeanOfEverySampleInIt() {
        SplittableRandom r = new SplittableRandom(3);
        double worstExact = 0, worstFast = 0;
        for (int t = 0; t < 400; t++) {
            double a = r.nextDouble(-1, 1), b = a + r.nextDouble(0, 1);
            double p = r.nextDouble(-1, 1), q = p + r.nextDouble(0.01, 1), sigma = r.nextDouble(0.05, 0.6);
            worstExact = Math.max(worstExact, Math.abs(numericMean(Cdf.EXACT, a, b, p, q, sigma)
                    - Gauss.meanMass(Cdf.EXACT, a, b, p, q, sigma)));
            worstFast = Math.max(worstFast, Math.abs(numericMean(Cdf.FAST, a, b, p, q, sigma)
                    - Gauss.meanMass(Cdf.FAST, a, b, p, q, sigma)));
        }
        assertTrue(worstExact < 1e-7, "the exact Ψ: " + worstExact);
        // Ψ̃ keeps the true φ, so it is not quite Φ̃'s antiderivative: off by about 10⁻³, a third of an 8-bit step
        assertTrue(worstFast < 1.5e-3, "the fast Ψ̃: " + worstFast);
    }

    private static double numericMean(Cdf c, double a, double b, double p, double q, double sigma) {
        int steps = 4000;
        double sum = 0;
        for (int k = 0; k < steps; k++) {
            sum += Gauss.mass(c, a, b, p + (k + 0.5) * (q - p) / steps, sigma);
        }
        return sum / steps;
    }
}
