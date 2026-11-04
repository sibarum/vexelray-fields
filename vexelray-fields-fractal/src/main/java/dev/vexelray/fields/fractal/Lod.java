package dev.vexelray.fields.fractal;

import java.util.List;

/**
 * What a box's next {@code m₀} levels look like to a footprint too wide to resolve them: their structure blurred, from
 * the unit box's point of view. Self-similarity means every kept box is a scaled copy of the root, and a Gaussian's
 * coverage is scale-invariant, so one {@code Lod} serves every box whose next {@code m₀} levels are these.
 *
 * <p>Two ways to ask it. {@link #exact} sums the {@code n^(3m₀)} sub-boxes by separable contraction, for any footprint:
 * {@code n^m₀} terms per axis and one multiply-add per sub-box. {@link #table} reads {@code T(u, s)} baked from that
 * sum on a grid, for a Gaussian sample: one interpolated lookup.
 *
 * <p>The table is narrow. The recursion only stops at a box once {@code s = σ / size} first passes {@code c}, and its
 * parent had {@code s / n ≤ c}, so {@code s} spans {@code [c, n·c)}. With the footprint truncated at 3σ, the offset
 * {@code u = (μ − box) / size} that covers anything spans {@code [−3s, 1 + 3s]} a side; the table is laid on that span,
 * stretched per {@code s}, and is exactly zero outside it.
 *
 * <p>A hashed level within the {@code m₀} is baked as its expectation, the mean of its draws.
 */
final class Lod {

    private final int m;            // n^m₀ sub-boxes a side
    private final double[] weights; // m³ composite weights, x fastest
    private final double uniform;   // the one weight, if every sub-box has it; −1 if not
    private final Cdf cdf;
    private final double c, sMax;
    private final int nu, ns;
    private float[] table;          // [ns][nu][nu][nu], x fastest

    Lod(List<Level> window, Cdf cdf, double c, int nu, int ns) {
        int n = window.getFirst().base();
        int m = 1;
        for (int r = 0; r < window.size(); r++) {
            m *= n;
        }
        this.m = m;
        this.weights = new double[m * m * m];
        for (int z = 0; z < m; z++) {
            for (int y = 0; y < m; y++) {
                for (int x = 0; x < m; x++) {
                    double w = 1;
                    int dx = x, dy = y, dz = z, below = m;
                    for (Level level : window) {
                        below /= n;
                        w *= level.weight(dx / below, dy / below, dz / below);
                        dx %= below;
                        dy %= below;
                        dz %= below;
                    }
                    weights[(z * m + y) * m + x] = w;
                }
            }
        }
        double first = weights[0];
        boolean same = true;
        for (double w : weights) {
            same &= w == first;
        }
        this.uniform = same ? first : -1;
        this.cdf = cdf;
        this.c = c;
        this.sMax = c * n;
        this.nu = nu;
        this.ns = ns;
    }

    /** The covered share of the box from {@code lo} of side {@code size}, its sub-boxes summed exactly. */
    double exact(Footprint fp, double x0, double y0, double z0, double size) {
        if (uniform >= 0) {
            return uniformly(fp, x0, y0, z0, size);
        }
        double sub = size / m;
        double[] gx = axis(fp, 0, x0, sub), gy = axis(fp, 1, y0, sub), gz = axis(fp, 2, z0, sub);
        return contract(gx, gy, gz);
    }

    /** {@code T(u, s)}: the covered share of the unit box of a Gaussian at {@code u} of width {@code s}. */
    double table(double ux, double uy, double uz, double s) {
        if (s < c || s > sMax) {
            throw new IllegalArgumentException("s = " + s + " outside the table's [" + c + ", " + sMax + "]");
        }
        if (uniform >= 0) {
            // structureless: the box's own coverage, exactly, so solid stays exactly 1
            return uniformly(new Footprint.Point(ux, uy, uz, s, cdf), 0, 0, 0, 1);
        }
        float[] t = baked();
        double span = 1 + 2 * Gauss.REACH * s, off = Gauss.REACH * s;
        double tx = (ux + off) / span, ty = (uy + off) / span, tz = (uz + off) / span;
        if (!(tx > 0 && tx < 1 && ty > 0 && ty < 1 && tz > 0 && tz < 1)) {
            return 0;
        }
        // cubic (Catmull-Rom) along s, where coverage bends most; linear along the offsets
        double fs = Math.log(s / c) / Math.log(sMax / c) * (ns - 1);
        int s0 = Math.min(ns - 2, (int) fs);
        double ws = fs - s0, ws2 = ws * ws, ws3 = ws2 * ws;
        double[] cs = {
                0.5 * (-ws3 + 2 * ws2 - ws),
                0.5 * (3 * ws3 - 5 * ws2 + 2),
                0.5 * (-3 * ws3 + 4 * ws2 + ws),
                0.5 * (ws3 - ws2)};
        double fx = tx * (nu - 1), fy = ty * (nu - 1), fz = tz * (nu - 1);
        int x0 = Math.min(nu - 2, (int) fx), y0 = Math.min(nu - 2, (int) fy), z0 = Math.min(nu - 2, (int) fz);
        double wx = fx - x0, wy = fy - y0, wz = fz - z0;
        double[] f = new double[4];
        for (int ds = 0; ds < 4; ds++) {
            int si = s0 - 1 + ds;
            if (si < 0 || si >= ns) {
                continue;
            }
            double v = 0;
            for (int dz = 0; dz < 2; dz++) {
                double az = dz == 0 ? 1 - wz : wz;
                for (int dy = 0; dy < 2; dy++) {
                    double ay = az * (dy == 0 ? 1 - wy : wy);
                    int row = ((si * nu + z0 + dz) * nu + y0 + dy) * nu + x0;
                    v += ay * ((1 - wx) * t[row] + wx * t[row + 1]);
                }
            }
            f[ds] = v;
        }
        // past either end, a ghost sample on the parabola through the last three (the line through two, for ns = 2)
        if (s0 == 0) {
            f[0] = ns > 2 ? 3 * f[1] - 3 * f[2] + f[3] : 2 * f[1] - f[2];
        }
        if (s0 + 2 >= ns) {
            f[3] = ns > 2 ? 3 * f[2] - 3 * f[1] + f[0] : 2 * f[2] - f[1];
        }
        return cs[0] * f[0] + cs[1] * f[1] + cs[2] * f[2] + cs[3] * f[3];
    }

    /** The table's size in bytes. */
    int bytes() {
        return ns * nu * nu * nu * Float.BYTES;
    }

    private synchronized float[] baked() {
        if (table == null) {
            table = bake();
        }
        return table;
    }

    private float[] bake() {
        float[] t = new float[ns * nu * nu * nu];
        double sub = 1.0 / m;
        double[][] g = new double[nu][];
        double[] v = new double[m * m], w = new double[m];
        for (int si = 0; si < ns; si++) {
            double s = c * Math.pow(sMax / c, si / (double) (ns - 1));
            double span = 1 + 2 * Gauss.REACH * s, off = Gauss.REACH * s;
            for (int ti = 0; ti < nu; ti++) {
                double u = -off + span * ti / (nu - 1);
                g[ti] = new double[m];
                for (int k = 0; k < m; k++) {
                    g[ti][k] = Gauss.mass(cdf, k * sub, (k + 1) * sub, u, s);
                }
            }
            // the same axis terms serve x, y and z: contract z, then y, then x
            for (int tz = 0; tz < nu; tz++) {
                for (int xy = 0; xy < m * m; xy++) {
                    double sum = 0;
                    for (int z = 0; z < m; z++) {
                        sum += weights[z * m * m + xy] * g[tz][z];
                    }
                    v[xy] = sum;
                }
                for (int ty = 0; ty < nu; ty++) {
                    for (int x = 0; x < m; x++) {
                        double sum = 0;
                        for (int y = 0; y < m; y++) {
                            sum += v[y * m + x] * g[ty][y];
                        }
                        w[x] = sum;
                    }
                    int row = ((si * nu + tz) * nu + ty) * nu;
                    for (int tx = 0; tx < nu; tx++) {
                        double sum = 0;
                        for (int x = 0; x < m; x++) {
                            sum += w[x] * g[tx][x];
                        }
                        t[row + tx] = (float) sum;
                    }
                }
            }
        }
        return t;
    }

    private double uniformly(Footprint fp, double x0, double y0, double z0, double size) {
        return uniform == 0 ? 0 : uniform * fp.weight(0, x0, x0 + size) * fp.weight(1, y0, y0 + size)
                * fp.weight(2, z0, z0 + size);
    }

    private double[] axis(Footprint fp, int axis, double lo, double sub) {
        double[] g = new double[m];
        for (int k = 0; k < m; k++) {
            double a = lo + k * sub, b = lo + (k + 1) * sub;
            g[k] = b <= fp.lo(axis) || a >= fp.hi(axis) ? 0 : fp.weight(axis, a, b);
        }
        return g;
    }

    private double contract(double[] gx, double[] gy, double[] gz) {
        double sum = 0;
        for (int z = 0; z < m; z++) {
            if (gz[z] == 0) {
                continue;
            }
            double sz = 0;
            for (int y = 0; y < m; y++) {
                if (gy[y] == 0) {
                    continue;
                }
                double sy = 0;
                int row = (z * m + y) * m;
                for (int x = 0; x < m; x++) {
                    sy += weights[row + x] * gx[x];
                }
                sz += sy * gy[y];
            }
            sum += sz * gz[z];
        }
        return sum;
    }
}
