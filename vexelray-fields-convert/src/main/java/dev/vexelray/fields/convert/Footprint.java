package dev.vexelray.fields.convert;

/**
 * The standard normal's distribution {@code Φ} and its inverse, for {@link ProbabilityField}: a footprint's chance of
 * being inside at a distance, and the distance a chance says.
 */
final class Footprint {

    private Footprint() {
    }

    /** {@code Φ(x)}, to within 1.2e-7: half of the complementary error function at {@code -x / √2}. */
    static double phi(double x) {
        return 0.5 * erfc(-x / Math.sqrt(2));
    }

    /**
     * {@code erfc(z)} by the Chebyshev fit in <i>Numerical Recipes</i> (§6.2), fractional error under 1.2e-7 for every
     * {@code z}.
     */
    static double erfc(double z) {
        double t = 1 / (1 + 0.5 * Math.abs(z));
        double r = t * Math.exp(-z * z - 1.26551223 + t * (1.00002368 + t * (0.37409196 + t * (0.09678418
                + t * (-0.18628806 + t * (0.27886807 + t * (-1.13520398 + t * (1.48851587
                + t * (-0.82215223 + t * 0.17087277)))))))));
        return z >= 0 ? r : 2 - r;
    }

    /**
     * {@code Φ⁻¹(p)} for {@code 0 < p < 1}, by Acklam's rational approximation (relative error under 1.15e-9), refined
     * by one Halley step against {@link #phi}.
     */
    static double inverse(double p) {
        if (!(p > 0 && p < 1)) {
            throw new IllegalArgumentException("Φ⁻¹ of " + p);
        }
        double q, x;
        if (p < 0.02425) {
            q = Math.sqrt(-2 * Math.log(p));
            x = (((((C1 * q + C2) * q + C3) * q + C4) * q + C5) * q + C6) / ((((D1 * q + D2) * q + D3) * q + D4) * q + 1);
        } else if (p > 1 - 0.02425) {
            q = Math.sqrt(-2 * Math.log(1 - p));
            x = -(((((C1 * q + C2) * q + C3) * q + C4) * q + C5) * q + C6) / ((((D1 * q + D2) * q + D3) * q + D4) * q + 1);
        } else {
            q = p - 0.5;
            double r = q * q;
            x = (((((A1 * r + A2) * r + A3) * r + A4) * r + A5) * r + A6) * q
                    / (((((B1 * r + B2) * r + B3) * r + B4) * r + B5) * r + 1);
        }
        double e = phi(x) - p;
        double u = e * Math.sqrt(2 * Math.PI) * Math.exp(x * x / 2);
        return x - u / (1 + x * u / 2);
    }

    private static final double A1 = -3.969683028665376e+01, A2 = 2.209460984245205e+02, A3 = -2.759285104469687e+02,
            A4 = 1.383577518672690e+02, A5 = -3.066479806614716e+01, A6 = 2.506628277459239e+00;
    private static final double B1 = -5.447609879822406e+01, B2 = 1.615858368580409e+02, B3 = -1.556989798598866e+02,
            B4 = 6.680131188771972e+01, B5 = -1.328068155288572e+01;
    private static final double C1 = -7.784894002430293e-03, C2 = -3.223964580411365e-01, C3 = -2.400758277161838e+00,
            C4 = -2.549732539343734e+00, C5 = 4.374664141464968e+00, C6 = 2.938163982698783e+00;
    private static final double D1 = 7.784695709041462e-03, D2 = 3.224671290700398e-01, D3 = 2.445134137142996e+00,
            D4 = 3.754408661907416e+00;
}
