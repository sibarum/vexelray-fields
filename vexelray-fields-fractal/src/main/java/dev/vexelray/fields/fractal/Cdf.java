package dev.vexelray.fields.fractal;

/**
 * The standard normal CDF Φ, two ways: {@link #EXACT} by the error function, and {@link #FAST}, a logistic of a cubic
 * that costs one {@code exp} and one reciprocal.
 *
 * <p>{@link #FAST} is {@code 1 / (1 + exp(−(1.5976 z + 0.07056 z³)))}: within 1.4 × 10⁻⁴ of Φ everywhere, analytic, so
 * its error is smooth too (no branch, no seam at a box's face), and exactly symmetric, {@code Φ̃(−z) = 1 − Φ̃(z)}. Its
 * {@link #pdf} is its own derivative, not the Gaussian's, so a Newton step's slope matches the field it solves.
 */
public enum Cdf {

    EXACT,
    FAST;

    private static final double A = 1.5976, B = 0.07056;
    private static final double INV_SQRT_2 = 1 / Math.sqrt(2), INV_SQRT_2PI = 1 / Math.sqrt(2 * Math.PI);

    /** Φ(z). */
    public double phi(double z) {
        if (this == FAST) {
            return 1 / (1 + Math.exp(-(A * z + B * z * z * z)));
        }
        return 0.5 * (1 + erf(z * INV_SQRT_2));
    }

    /** The derivative of {@link #phi}: the Gaussian's density for {@link #EXACT}, Φ̃'s own slope for {@link #FAST}. */
    public double pdf(double z) {
        if (this == FAST) {
            double p = phi(z);
            return p * (1 - p) * (A + 3 * B * z * z);
        }
        return normal(z);
    }

    /**
     * Ψ(z) = z·Φ(z) + φ(z), the antiderivative of Φ in units of σ: what averaging a Gaussian's coverage over an interval
     * of centres takes. For {@link #FAST} this keeps the exact Gaussian φ, since Φ̃ has no closed-form antiderivative;
     * its slope then differs from Φ̃ by about 10⁻⁴.
     */
    public double psi(double z) {
        return z * phi(z) + normal(z);
    }

    /** The standard normal density. */
    static double normal(double z) {
        return Math.exp(-0.5 * z * z) * INV_SQRT_2PI;
    }

    /**
     * erf(x) to about one ulp of 1, by the series {@code 2/√π · e^(−x²) · Σ (2x²)ⁿ x / (1·3·…·(2n+1))}, whose terms
     * are all positive, so nothing cancels. Past |x| = 6, erf is ±1 in a double.
     */
    static double erf(double x) {
        double a = Math.abs(x);
        if (a >= 6) {
            return Math.signum(x);
        }
        double t = a, sum = a, x2 = 2 * a * a;
        for (int n = 1; t > 1e-17 * sum; n++) {
            t *= x2 / (2 * n + 1);
            sum += t;
        }
        double r = 2 / Math.sqrt(Math.PI) * Math.exp(-a * a) * sum;
        return x < 0 ? -r : r;
    }
}
