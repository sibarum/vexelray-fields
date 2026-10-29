package dev.vexelray.fields.vexel;

import java.util.List;

/**
 * Where a ray through a {@link VexelField} may be stopped, in expectation: one {@link Hit} per cell it may stop in,
 * nearest first, each with the probability that it is stopped there and not before. Their sum is the chance it is
 * stopped at all; the rest goes through. What a hashed {@link Collision} converges to, averaged over seeds.
 */
public record RayHits(List<Hit> hits) {

    /**
     * One cell the ray may stop in.
     *
     * @param probability that the ray stops here, having got here
     * @param t           the expected distance along the ray of the stop: the plane's crossing and the granular
     *                    share's mean, weighted by their chances
     * @param point       the point at {@code t}
     * @param normal      the cell's plane's outward normal, unit length
     * @param granularity the cell's {@link Vexel#granularity()}
     * @param materials   the chance of each material, given a stop here
     */
    public record Hit(double probability, double t, Vec3 point, Vec3 normal, double granularity, float[] materials) {
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
