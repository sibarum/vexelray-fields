package dev.vexelray.fields.fractal;

/**
 * One axis of a Gaussian footprint, truncated at {@link #REACH}σ and renormalized: how much of it lands in an interval.
 * A box's coverage is the product of its three axes' terms, and disjoint boxes' coverages add.
 *
 * <p>Why truncated: the cull drops every box past 3σ, and with the Gaussian truncated there, dropping them is exact
 * rather than an approximation. Coverage is then exactly {@code 0} with no solid inside the footprint and exactly
 * {@code 1} with the footprint wholly in solid, which a ray reads as "skip" and "hit". The price is a step of
 * φ(3)/Z ≈ 0.0044 in the density at ±3σ, so coverage is continuous everywhere but its slope has a small kink where a
 * box's face meets the footprint's edge.
 */
public final class Gauss {

    /** Where the footprint is cut, in units of σ. */
    public static final double REACH = 3;

    private static final double[] LO = new double[Cdf.values().length];
    private static final double[] NORM = new double[LO.length];
    private static final double[] A_END = new double[LO.length];

    static {
        for (Cdf c : Cdf.values()) {
            int o = c.ordinal();
            LO[o] = c.phi(-REACH);
            NORM[o] = c.phi(REACH) - LO[o];
            A_END[o] = (c.psi(REACH) - c.psi(-REACH) - LO[o] * 2 * REACH) / NORM[o];
        }
    }

    private Gauss() {
    }

    /** The truncated CDF, in units of σ: {@code 0} below −3, {@code 1} above 3. */
    public static double cdf(Cdf c, double z) {
        if (z <= -REACH) {
            return 0;
        }
        if (z >= REACH) {
            return 1;
        }
        return (c.phi(z) - LO[c.ordinal()]) / NORM[c.ordinal()];
    }

    /** The truncated density, in units of σ. */
    public static double density(Cdf c, double z) {
        return Math.abs(z) >= REACH ? 0 : c.pdf(z) / NORM[c.ordinal()];
    }

    /** The antiderivative of {@link #cdf}, zero at −3 and below. */
    static double integral(Cdf c, double z) {
        if (z <= -REACH) {
            return 0;
        }
        int o = c.ordinal();
        if (z >= REACH) {
            return A_END[o] + (z - REACH);
        }
        return (c.psi(z) - c.psi(-REACH) - LO[o] * (z + REACH)) / NORM[o];
    }

    /** How much of the footprint centred at {@code mu}, of width {@code sigma}, lands in {@code [a, b]}. */
    public static double mass(Cdf c, double a, double b, double mu, double sigma) {
        return cdf(c, (b - mu) / sigma) - cdf(c, (a - mu) / sigma);
    }

    /** The derivative of {@link #mass} with respect to {@code mu}: closed form, for Newton steps and normals. */
    public static double slope(Cdf c, double a, double b, double mu, double sigma) {
        return (density(c, (a - mu) / sigma) - density(c, (b - mu) / sigma)) / sigma;
    }

    /**
     * The mean of {@link #mass} over every centre in {@code [p, q]}: what a raster cell {@code [p, q]} holds of source
     * interval {@code [a, b]}, the area under the curve rather than a sample of it.
     */
    public static double meanMass(Cdf c, double a, double b, double p, double q, double sigma) {
        double w = q - p;
        if (w <= 1e-9 * sigma) {
            return mass(c, a, b, 0.5 * (p + q), sigma);
        }
        double s = 1 / sigma;
        double v = integral(c, (b - p) * s) - integral(c, (b - q) * s)
                - integral(c, (a - p) * s) + integral(c, (a - q) * s);
        return Math.clamp(v * sigma / w, 0, 1);
    }
}
