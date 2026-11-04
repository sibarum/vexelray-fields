package dev.vexelray.fields.fractal;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * How much of a footprint lands in a {@link Fractal}'s solid: by recursion from the root, culling boxes the footprint
 * does not reach and blurring the ones it cannot resolve.
 *
 * <pre>
 * cover(box at level k):
 *   past the footprint's reach on any axis:        0
 *   k = depth:                                     the box's own coverage, exactly
 *   s = width / size > c, and depth − k ≥ m₀:      T(u, s) · ρ(k+m₀+1) ⋯ ρ(depth)
 *   otherwise:                                     Σ weight(child) · cover(child)
 * </pre>
 *
 * <p>{@code T} is a {@link Lod}: the next {@code m₀} levels blurred. The levels past those are replaced by their fill
 * fractions. A box fewer than {@code m₀} levels from the depth is recursed instead, since {@code T} would hold
 * structure the fractal does not have there.
 *
 * <p>{@code c} trades accuracy against cost: a larger {@code c} recurses deeper before blurring. {@code m₀} is how many
 * levels of real structure {@code T} keeps; {@link #depthFor} gives it from {@code c} and a tolerance's threshold.
 */
public final class Coverage {

    /**
     * How coverage is computed.
     *
     * @param cdf         which Φ: {@link Cdf#FAST} renders, {@link Cdf#EXACT} checks
     * @param c           the width, relative to a box, past which the box is blurred rather than recursed
     * @param m0          levels of structure the blur keeps before the fill fractions take over
     * @param table       whether a Gaussian sample reads the baked table {@code T(u, s)}; if not, or for a raster
     *                    cell, the {@code m₀} levels are summed exactly by separable contraction
     * @param tableSize   table samples a side along each offset axis
     * @param tableWidths table samples along {@code s}, log-spaced over {@code [c, n·c]}
     */
    public record Options(Cdf cdf, double c, int m0, boolean table, int tableSize, int tableWidths) {

        /** {@code c = 0.36} and {@code m₀ = 1}: within one 8-bit step for base 3, by the 1D test. */
        public static final Options DEFAULT = new Options(Cdf.FAST, 0.36, 1, true, 49, 9);

        public Options {
            if (!(c > 0) || m0 < 1 || tableSize < 2 || tableWidths < 2) {
                throw new IllegalArgumentException("c = " + c + ", m0 = " + m0 + ", a table of " + tableSize + " by "
                        + tableWidths);
            }
        }

        public Options withCdf(Cdf cdf) {
            return new Options(cdf, c, m0, table, tableSize, tableWidths);
        }

        public Options withCutoff(double c, int m0) {
            return new Options(cdf, c, m0, table, tableSize, tableWidths);
        }

        public Options withTable(boolean table) {
            return new Options(cdf, c, m0, table, tableSize, tableWidths);
        }

        public Options withTableSize(int tableSize, int tableWidths) {
            return new Options(cdf, c, m0, table, tableSize, tableWidths);
        }
    }

    private final Fractal fractal;
    private final Options options;
    private final Map<List<Level>, Lod> lods = new ConcurrentHashMap<>();

    public Coverage(Fractal fractal, Options options) {
        this.fractal = fractal;
        this.options = options;
    }

    public static Coverage of(Fractal fractal) {
        return new Coverage(fractal, Options.DEFAULT);
    }

    public Fractal fractal() {
        return fractal;
    }

    public Options options() {
        return options;
    }

    /**
     * {@code m₀ = ⌈log_n(s* / c)⌉}: the levels a blur must keep so that the ones past it are seen at a width of at least
     * {@code s*}, where blurred structure and its mean density differ by less than the tolerance {@code s*} was found
     * for. At least 1.
     */
    public static int depthFor(int n, double c, double sStar) {
        return Math.max(1, (int) Math.ceil(Math.log(sStar / c) / Math.log(n) - 1e-12));
    }

    /** The coverage of a Gaussian sample at {@code (x, y, z)} of width {@code sigma}. */
    public double at(double x, double y, double z, double sigma) {
        return cover(new Footprint.Point(x, y, z, sigma, options.cdf()));
    }

    /** The coverage of {@code fp}, blurring what it cannot resolve. */
    public double cover(Footprint fp) {
        return cover(fp, 0, 0, 0, 0, fractal.rootHash(), true);
    }

    /** The coverage of {@code fp} with nothing blurred: every box to the full depth. The reference; slow. */
    public double exact(Footprint fp) {
        return cover(fp, 0, 0, 0, 0, fractal.rootHash(), false);
    }

    /**
     * The fractal rasterized on a grid of {@code res³} cells over the unit cube, each cell the exact mean coverage of
     * every Gaussian sample of width {@code sigma} centred in it. x fastest, then y, then z.
     */
    public float[] rasterize(int res, double sigma) {
        float[] out = new float[res * res * res];
        for (int z = 0; z < res; z++) {
            for (int y = 0; y < res; y++) {
                for (int x = 0; x < res; x++) {
                    Footprint.Cell cell = new Footprint.Cell(x / (double) res, y / (double) res, z / (double) res,
                            (x + 1) / (double) res, (y + 1) / (double) res, (z + 1) / (double) res, sigma,
                            options.cdf());
                    out[(z * res + y) * res + x] = (float) cover(cell);
                }
            }
        }
        return out;
    }

    /** The blur of the {@code m₀} levels below level {@code k}. */
    Lod lod(int k) {
        List<Level> window = List.of(levelsBelow(k));
        return lods.computeIfAbsent(window, w -> new Lod(w, options.cdf(), options.c(), options.tableSize(),
                options.tableWidths()));
    }

    private Level[] levelsBelow(int k) {
        Level[] window = new Level[options.m0()];
        for (int r = 0; r < window.length; r++) {
            window[r] = fractal.level(k + 1 + r);
        }
        return window;
    }

    private double cover(Footprint fp, int k, long ix, long iy, long iz, long hash, boolean blur) {
        double sc = fractal.scale(k), size = 1 / sc;
        double x0 = ix / sc, y0 = iy / sc, z0 = iz / sc;
        double x1 = (ix + 1) / sc, y1 = (iy + 1) / sc, z1 = (iz + 1) / sc;
        if (x1 <= fp.lo(0) || x0 >= fp.hi(0) || y1 <= fp.lo(1) || y0 >= fp.hi(1) || z1 <= fp.lo(2)
                || z0 >= fp.hi(2)) {
            return 0;
        }
        int depth = fractal.depth();
        if (k == depth) {
            return fp.weight(0, x0, x1) * fp.weight(1, y0, y1) * fp.weight(2, z0, z1);
        }
        if (blur) {
            double s = fp.width() * sc;
            if (s > options.c() && depth - k >= options.m0()) {
                return blurred(fp, k, x0, y0, z0, size, s) * fractal.tailFill(k + options.m0() + 1);
            }
        }
        int n = fractal.base();
        double sub = size / n;
        int i0 = child(fp.lo(0), x0, sub, n), i1 = child(fp.hi(0), x0, sub, n);
        int j0 = child(fp.lo(1), y0, sub, n), j1 = child(fp.hi(1), y0, sub, n);
        int l0 = child(fp.lo(2), z0, sub, n), l1 = child(fp.hi(2), z0, sub, n);
        double sum = 0;
        for (int l = l0; l <= l1; l++) {
            for (int j = j0; j <= j1; j++) {
                for (int i = i0; i <= i1; i++) {
                    long h = Fractal.childHash(hash, k + 1, i, j, l);
                    double w = fractal.weight(k + 1, h, i, j, l);
                    if (w != 0) {
                        sum += w * cover(fp, k + 1, ix * n + i, iy * n + j, iz * n + l, h, blur);
                    }
                }
            }
        }
        return sum;
    }

    private double blurred(Footprint fp, int k, double x0, double y0, double z0, double size, double s) {
        Lod lod = lod(k);
        if (options.table() && fp instanceof Footprint.Point p && s <= options.c() * fractal.base()) {
            return lod.table((p.x() - x0) / size, (p.y() - y0) / size, (p.z() - z0) / size, s);
        }
        return lod.exact(fp, x0, y0, z0, size);
    }

    /** The child, of {@code n} from {@code lo} each {@code sub} wide, that holds {@code at}, clamped. */
    private static int child(double at, double lo, double sub, int n) {
        return (int) Math.clamp(Math.floor((at - lo) / sub), 0, n - 1);
    }
}
