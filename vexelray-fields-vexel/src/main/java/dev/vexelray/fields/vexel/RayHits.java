package dev.vexelray.fields.vexel;

import java.util.List;

/**
 * Where a ray through a {@link VexelField} may be stopped: one {@link Hit} per cell it may stop in, nearest first,
 * each with the probability that it is stopped there and not before. Their sum is the chance it is stopped at all;
 * the rest goes through.
 */
public record RayHits(List<Hit> hits) {

    /**
     * One cell the ray may stop in.
     *
     * @param probability that the ray stops here, having got here
     * @param t           the expected distance along the ray of the stop, where it crosses the cell's mean surface
     * @param point       the point at {@code t}
     * @param normal      the cell's mean outward normal, unit length
     * @param roughness   the cell's {@link Vexel#roughness()}
     * @param attributes  the surface's mean attributes there
     */
    public record Hit(double probability, double t, Vec3 point, Vec3 normal, double roughness, float[] attributes) {
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

    /**
     * One outcome, chosen by {@code u} uniform in {@code [0, 1)}: the hit whose share of the probability {@code u}
     * falls in, or null for a ray that goes through. What a path tracer draws per ray.
     */
    public Hit sample(double u) {
        double acc = 0;
        for (Hit h : hits) {
            acc += h.probability();
            if (u < acc) {
                return h;
            }
        }
        return null;
    }
}
