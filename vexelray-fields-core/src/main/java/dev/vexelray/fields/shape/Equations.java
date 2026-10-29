package dev.vexelray.fields.shape;

/** Real roots of low-order polynomials, as msdfgen solves them: robust to a leading coefficient near zero. */
public final class Equations {

    private Equations() {
    }

    /**
     * The real roots of {@code a x² + b x + c} into {@code x}: how many, or -1 if every x is one. A vanishing
     * {@code a} falls back to the linear equation.
     */
    public static int quadratic(double[] x, double a, double b, double c) {
        if (a == 0 || Math.abs(b) > 1e12 * Math.abs(a)) {
            if (b == 0) {
                return c == 0 ? -1 : 0;
            }
            x[0] = -c / b;
            return 1;
        }
        double d = b * b - 4 * a * c;
        if (d > 0) {
            d = Math.sqrt(d);
            x[0] = (-b + d) / (2 * a);
            x[1] = (-b - d) / (2 * a);
            return 2;
        }
        if (d == 0) {
            x[0] = -b / (2 * a);
            return 1;
        }
        return 0;
    }

    /** The real roots of {@code a x³ + b x² + c x + d} into {@code x}, three at most; as {@link #quadratic} else. */
    public static int cubic(double[] x, double a, double b, double c, double d) {
        if (a != 0) {
            double bn = b / a;
            if (Math.abs(bn) < 1e6) {   // past this, treating a as zero loses less than keeping it
                return normed(x, bn, c / a, d / a);
            }
        }
        return quadratic(x, b, c, d);
    }

    /** {@code x³ + a x² + b x + c}, by Cardano's or the trigonometric form. */
    private static int normed(double[] x, double a, double b, double c) {
        double a2 = a * a;
        double q = (a2 - 3 * b) / 9;
        double r = (a * (2 * a2 - 9 * b) + 27 * c) / 54;
        double r2 = r * r;
        double q3 = q * q * q;
        a /= 3;
        if (r2 < q3) {
            double t = Math.acos(Math.max(-1, Math.min(1, r / Math.sqrt(q3))));
            q = -2 * Math.sqrt(q);
            x[0] = q * Math.cos(t / 3) - a;
            x[1] = q * Math.cos((t + 2 * Math.PI) / 3) - a;
            x[2] = q * Math.cos((t - 2 * Math.PI) / 3) - a;
            return 3;
        }
        double u = (r < 0 ? 1 : -1) * Math.cbrt(Math.abs(r) + Math.sqrt(r2 - q3));
        double v = u == 0 ? 0 : q / u;
        x[0] = (u + v) - a;
        if (u == v || Math.abs(u - v) < 1e-12 * Math.abs(u + v)) {
            x[1] = -0.5 * (u + v) - a;
            return 2;
        }
        return 1;
    }
}
