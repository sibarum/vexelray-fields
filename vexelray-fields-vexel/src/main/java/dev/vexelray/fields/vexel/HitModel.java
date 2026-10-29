package dev.vexelray.fields.vexel;

/**
 * How the surface in a cell is taken to be arranged when a ray asks whether it is stopped there. The two ends of
 * the range; the truth for any real cell lies between.
 */
public enum HitModel {

    /**
     * The surface is sheets. The share of it facing along the mean normal ({@code |n̄|}) is one sheet, offset from
     * the cell's centre as the offset moments say: the ray is stopped by it with the probability that the sheet's
     * offset falls within the stretch of offsets the ray's path through the cell covers. Exact for a flat surface: a
     * ray that crosses the plane inside the cell is stopped there, at any angle, and one that does not is not. The
     * rest, facing every way, is crossed {@code N} times on average by the Crofton formula and stops the ray with
     * probability {@code min(1, N)}.
     */
    SHEET,
    /**
     * The surface is many small pieces scattered independently, as foliage or a cloud of particles is: the ray is
     * stopped with probability {@code 1 - exp(-N)}, {@code N} the Crofton count of the whole surface. Never certain;
     * the transmittance of a participating medium.
     */
    SCATTERED
}
