package dev.vexelray.fields.vexel;

import java.util.List;

/**
 * Where a ray through a {@link VexelField} may be stopped, in expectation: one {@link Hit} per cell it may stop in,
 * nearest first, each with the probability that it is stopped there and not before. Their sum is the chance it is
 * stopped at all; the rest goes through. What a hashed {@link Collision} converges to, averaged over seeds.
 */
public record RayHits(List<Hit> hits) {

    /**
     * One cell the ray may stop in. A renderer compositing the hits front to back, each shaded and weighted by its
     * probability, the background by what is left, draws the field with its opacity: smoke as thin as it is, rather
     * than one hashed outcome.
     *
     * @param probability that the ray stops here and not before
     * @param t           the expected distance along the ray of the stop: the plane's crossing and the granular
     *                    share's mean, weighted by their chances
     * @param point       the point at {@code t}
     * @param normal      the outward normal of the face a flat stop enters by, unit length
     * @param granularity the cell's {@link Vexel#granularity()}
     * @param smoke       the share of this stop that is the smoke's, {@code 0..1}
     * @param grains      the share that is grains of solid, a granular stop with no normal; the rest is flat, on
     *                    {@code normal}
     * @param materials   the chance of each material, given a stop here on the solid
     */
    public record Hit(double probability, double t, Vec3 point, Vec3 normal, double granularity, double smoke,
                      double grains, float[] materials) {

        /** The share of this stop that is a flat face: what is neither smoke nor grains. */
        public double flat() {
            return Math.max(0, 1 - smoke - grains);
        }
    }

    public RayHits {
        hits = List.copyOf(hits);
    }

    /** The chance the ray is stopped at all. */
    public double probability() {
        double p = 0;
        for (Hit h : hits) {
            p += h.probability();
        }
        return p;
    }

    /** The expected distance to the stop, given there is one; infinity if there cannot be. */
    public double expectedDistance() {
        double p = 0, t = 0;
        for (Hit h : hits) {
            p += h.probability();
            t += h.probability() * h.t();
        }
        return p == 0 ? Double.POSITIVE_INFINITY : t / p;
    }
}
