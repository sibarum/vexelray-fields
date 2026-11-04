package dev.vexelray.fields.fractal;

/**
 * A small 3D texture as geometry: {@code size³} texels over the unit cube, each a box holding a probability, and the
 * Gaussian's integral over those boxes the interpolation between them.
 *
 * <pre>P(x) = Σ p(i, j, l) · w_x(i) · w_y(j) · w_z(l)</pre>
 *
 * <p>The weights are each texel's interval's {@link Gauss#mass}, separable, so a query is a small tensor contraction:
 * with σ one texel and the footprint cut at 3σ, at most 7 taps a side. Unlike trilinear interpolation, which is only
 * C⁰ and creases along texel faces, the result is smooth, and so is its {@link #gradient}.
 *
 * <p>The texture carries the base form. Finer features come from a {@link Fractal} beneath it: {@link #level} makes it
 * a level whose texels scale their child boxes.
 */
public final class Texture {

    private final int size;
    private final double[] p;

    /** {@code size³} probabilities, x fastest, then y, then z. */
    public Texture(int size, double... p) {
        if (size < 1 || p.length != size * size * size) {
            throw new IllegalArgumentException(p.length + " texels for a texture of " + size + " a side");
        }
        for (double v : p) {
            if (!(v >= 0 && v <= 1)) {
                throw new IllegalArgumentException("a probability of " + v);
            }
        }
        this.size = size;
        this.p = p.clone();
    }

    public int size() {
        return size;
    }

    /** This texture as a fractal's level: base {@code size}, each texel's probability scaling its child box. */
    public Level level() {
        return Level.of(size, p);
    }

    /** The coverage of a Gaussian sample at {@code (x, y, z)} of width {@code sigma}. */
    public double at(double x, double y, double z, double sigma, Cdf cdf) {
        return cover(new Footprint.Point(x, y, z, sigma, cdf));
    }

    /** The coverage of {@code fp}: a sample, or a raster cell's mean. */
    public double cover(Footprint fp) {
        int[] lo = new int[3], hi = new int[3];
        double[][] w = new double[3][];
        for (int a = 0; a < 3; a++) {
            lo[a] = Math.max(0, (int) Math.floor(fp.lo(a) * size));
            hi[a] = Math.min(size - 1, (int) Math.ceil(fp.hi(a) * size) - 1);
            if (lo[a] > hi[a]) {
                return 0;
            }
            w[a] = new double[hi[a] - lo[a] + 1];
            for (int t = lo[a]; t <= hi[a]; t++) {
                w[a][t - lo[a]] = fp.weight(a, t / (double) size, (t + 1) / (double) size);
            }
        }
        return contract(lo, hi, w[0], w[1], w[2]);
    }

    /**
     * The gradient of {@link #at} with respect to the sample's centre, in closed form: each axis's weights swapped for
     * their {@link Gauss#slope} in turn. Continuous everywhere, so normals from it are too.
     */
    public double[] gradient(double x, double y, double z, double sigma, Cdf cdf) {
        double[] centre = {x, y, z};
        int[] lo = new int[3], hi = new int[3];
        double[][] w = new double[3][], d = new double[3][];
        for (int a = 0; a < 3; a++) {
            lo[a] = Math.max(0, (int) Math.floor((centre[a] - Gauss.REACH * sigma) * size));
            hi[a] = Math.min(size - 1, (int) Math.ceil((centre[a] + Gauss.REACH * sigma) * size) - 1);
            if (lo[a] > hi[a]) {
                return new double[3];
            }
            w[a] = new double[hi[a] - lo[a] + 1];
            d[a] = new double[w[a].length];
            for (int t = lo[a]; t <= hi[a]; t++) {
                double from = t / (double) size, to = (t + 1) / (double) size;
                w[a][t - lo[a]] = Gauss.mass(cdf, from, to, centre[a], sigma);
                d[a][t - lo[a]] = Gauss.slope(cdf, from, to, centre[a], sigma);
            }
        }
        return new double[]{
                contract(lo, hi, d[0], w[1], w[2]),
                contract(lo, hi, w[0], d[1], w[2]),
                contract(lo, hi, w[0], w[1], d[2])};
    }

    private double contract(int[] lo, int[] hi, double[] wx, double[] wy, double[] wz) {
        double sum = 0;
        for (int l = lo[2]; l <= hi[2]; l++) {
            double sy = 0;
            for (int j = lo[1]; j <= hi[1]; j++) {
                double sx = 0;
                int row = (l * size + j) * size;
                for (int i = lo[0]; i <= hi[0]; i++) {
                    sx += p[row + i] * wx[i - lo[0]];
                }
                sy += sx * wy[j - lo[1]];
            }
            sum += sy * wz[l - lo[2]];
        }
        return sum;
    }
}
