package dev.vexelray.fields.fractal;

import java.util.Arrays;
import java.util.List;

/**
 * A fractal on the unit cube: boxes on a rational grid of base {@code n}, kept, weighted or drawn at each of
 * {@link #depth} levels by that level's {@link Level}. The root, level 0, is the whole cube.
 *
 * <p>A box at level {@code k} has integer index {@code (i, j, l)} and bounds {@code [i/nᵏ, (i+1)/nᵏ]} on each axis.
 * Indices are integers, so a box's place is exact at any depth; only its bounds as doubles round. The depth is an
 * authored cap on detail: past it, a kept box is solid.
 *
 * <p>The hash chain: the root's hash comes from the seed, and each child's from its parent's, its level and its local
 * digits. A hashed level's draw reads it, so the randomness is stable under zoom and from frame to frame, and two
 * fractals with the same seed and levels are the same.
 */
public final class Fractal {

    private final int n;
    private final Level[] levels;   // levels[k - 1] makes level k from level k - 1
    private final long seed;
    private final long[] scale;     // nᵏ
    private final double[] tail;    // tail[k]: the product of fills of levels k..depth, 1 past depth

    private Fractal(long seed, Level[] levels) {
        if (levels.length == 0) {
            throw new IllegalArgumentException("a fractal of no levels");
        }
        this.n = levels[0].base();
        for (Level level : levels) {
            if (level.base() != n) {
                throw new IllegalArgumentException("levels of base " + n + " and " + level.base());
            }
        }
        this.levels = levels;
        this.seed = seed;
        this.scale = new long[levels.length + 1];
        scale[0] = 1;
        for (int k = 1; k <= levels.length; k++) {
            scale[k] = Math.multiplyExact(scale[k - 1], n);
            if (scale[k] > 1L << 53) {
                throw new IllegalArgumentException("base " + n + " to depth " + levels.length + " is past a double's"
                        + " integers");
            }
        }
        this.tail = new double[levels.length + 2];
        tail[levels.length + 1] = 1;
        for (int k = levels.length; k >= 1; k--) {
            tail[k] = tail[k + 1] * levels[k - 1].fill();
        }
    }

    /** {@code levels} in order, level 1 first. */
    public static Fractal of(long seed, List<Level> levels) {
        return new Fractal(seed, levels.toArray(Level[]::new));
    }

    /** {@code level} at every one of {@code depth} levels. */
    public static Fractal repeat(Level level, int depth, long seed) {
        Level[] l = new Level[depth];
        Arrays.fill(l, level);
        return new Fractal(seed, l);
    }

    /** This fractal with level {@code k} (1-based) replaced: one randomized level in the middle, say. */
    public Fractal with(int k, Level level) {
        Level[] l = levels.clone();
        l[k - 1] = level;
        return new Fractal(seed, l);
    }

    public int base() {
        return n;
    }

    public int depth() {
        return levels.length;
    }

    public long seed() {
        return seed;
    }

    /** Level {@code k}, 1-based: what made the boxes at level {@code k}. */
    public Level level(int k) {
        return levels[k - 1];
    }

    /** {@code nᵏ}: boxes a side at level {@code k}. */
    public long scale(int k) {
        return scale[k];
    }

    /** ρ for levels {@code from} to the depth, multiplied: how much of a box at level {@code from − 1} is filled. */
    public double tailFill(int from) {
        return tail[Math.min(from, levels.length + 1)];
    }

    /** The root's hash. */
    public long rootHash() {
        return mix(seed);
    }

    /** A child's hash, from its parent's, its level {@code k} and its local digits. */
    public static long childHash(long parent, int k, int i, int j, int l) {
        return mix(parent ^ ((long) k << 48 | (long) l << 32 | (long) j << 16 | i));
    }

    /**
     * What the child at level {@code k}, local digits {@code (i, j, l)} and hash {@code hash} contributes: its weight,
     * or, on a hashed level, {@code 1} if its draw keeps it and {@code 0} if not.
     */
    public double weight(int k, long hash, int i, int j, int l) {
        Level level = levels[k - 1];
        double w = level.weight(i, j, l);
        if (!level.hashed() || w == 0 || w == 1) {
            return w;
        }
        return unit(hash) < w ? 1 : 0;
    }

    /**
     * The fill at a point at full depth, by its digits alone, with no tree walk: {@code 1} inside a kept box and
     * {@code 0} outside for modulus rules, the product of the weights on the way down for textures. O(depth). Points
     * outside {@code [0, 1)³} are empty.
     */
    public double density(double x, double y, double z) {
        if (!(x >= 0 && x < 1 && y >= 0 && y < 1 && z >= 0 && z < 1)) {
            return 0;
        }
        long top = scale[levels.length];
        long ix = Math.min(top - 1, (long) Math.floor(x * top));
        long iy = Math.min(top - 1, (long) Math.floor(y * top));
        long iz = Math.min(top - 1, (long) Math.floor(z * top));
        return densityAt(ix, iy, iz);
    }

    /** {@link #density} of the box at full depth with index {@code (ix, iy, iz)}. */
    public double densityAt(long ix, long iy, long iz) {
        double d = 1;
        long hash = rootHash();
        int depth = levels.length;
        for (int k = 1; k <= depth; k++) {
            long below = scale[depth - k];
            int i = (int) (ix / below % n), j = (int) (iy / below % n), l = (int) (iz / below % n);
            hash = childHash(hash, k, i, j, l);
            d *= weight(k, hash, i, j, l);
            if (d == 0) {
                return 0;
            }
        }
        return d;
    }

    /** A hash's value uniform in {@code [0, 1)}. */
    static double unit(long hash) {
        return (mix(hash ^ 0x5DEECE66DL) >>> 11) * 0x1.0p-53;
    }

    /** splitmix64's finaliser. */
    static long mix(long z) {
        z += 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
