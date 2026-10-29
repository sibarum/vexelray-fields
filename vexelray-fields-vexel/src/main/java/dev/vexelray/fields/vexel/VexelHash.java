package dev.vexelray.fields.vexel;

/**
 * Where a vexel field's chance outcomes get their randomness: a hash of the cell, the ray's direction quantized to a
 * bucket, a seed and what is being decided, never a running random number generator. A cell seen from one angle
 * bucket always gives the same answer, so an image is stable from frame to frame and from pixel to pixel; the
 * answer changes only as the view crosses into another bucket, or the seed is moved on.
 *
 * <p>The grain is the cell: every ray through a cell in one bucket shares its outcome, so a cell of half coverage is
 * wholly there or wholly not, from that direction. Averaging seeds (over frames, say) recovers the expected value
 * {@link VexelField#trace} gives.
 *
 * @param seed  moves every outcome at once; a renderer accumulating frames advances it
 * @param steps angle buckets a side: of the octahedral map of directions in 3D ({@code steps²} buckets), of the
 *              circle in 2D ({@code steps} buckets)
 */
public record VexelHash(long seed, int steps) {

    /** What an outcome decides; each draws its own value. */
    static final int FLAT = 0, GRANULAR = 1, MATERIAL = 2, SOLID = 3, KIND = 4;

    public VexelHash {
        if (steps <= 0) {
            throw new IllegalArgumentException("a hash of " + steps + " angle steps");
        }
    }

    /** The bucket of unit direction {@code d}, in a field of {@code dims} dimensions. */
    public int bucket(Vec3 d, int dims) {
        if (dims == 2) {
            double a = (Math.atan2(d.y(), d.x()) + Math.PI) / (2 * Math.PI);
            return Math.min(steps - 1, (int) (a * steps));
        }
        double s = Math.abs(d.x()) + Math.abs(d.y()) + Math.abs(d.z());
        double u = d.x() / s, v = d.y() / s;
        if (d.z() < 0) {   // fold the lower half over the diagonals: the octahedral map
            double fu = (1 - Math.abs(v)) * Math.signum(u == 0 ? 1 : u);
            double fv = (1 - Math.abs(u)) * Math.signum(v == 0 ? 1 : v);
            u = fu;
            v = fv;
        }
        int i = Math.min(steps - 1, (int) ((u + 1) / 2 * steps));
        int j = Math.min(steps - 1, (int) ((v + 1) / 2 * steps));
        return j * steps + i;
    }

    /** A value uniform in {@code [0, 1)} for deciding {@code what} at cell {@code (x, y, z)} from {@code bucket}. */
    public double u(int x, int y, int z, int bucket, int what) {
        long h = seed;
        h = mix(h ^ x);
        h = mix(h ^ ((long) y << 21));
        h = mix(h ^ ((long) z << 42));
        h = mix(h ^ ((long) bucket << 8 | what));
        return (h >>> 11) * 0x1.0p-53;
    }

    /** splitmix64's finaliser. */
    private static long mix(long z) {
        z += 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
