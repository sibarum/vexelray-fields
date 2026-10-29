package dev.vexelray.fields.vexel;

/**
 * One cell of a {@link VexelField}, read out: what a renderer asks of it, and the moments that is derived from.
 *
 * <p>What a renderer asks: the cell's material splits into a granular share, {@code granularity}, and a flat share,
 * the rest. The granular share stops a ray with a {@code rate} of collisions per unit distance, as smoke does, and
 * has no normal; the rate is the cell's average, concentrated where the cell's solid is. The flat share is solid:
 * the half-space behind a plane ({@code normal}, {@code offset}) within its {@code footprint}, never a sheet with no
 * thickness. A ray that enters it there is stopped, at any angle; one that leaves it is not. Either way, what is hit
 * is material {@code i} with probability {@code materials[i]}.
 *
 * <p>The moments, which are what a coarser level is made from: how much of the cell is solid and where, how much
 * surface is in it, the area-weighted mean of its outward normals (length {@code 1} for one flat patch, toward
 * {@code 0} as they spread every way), where the surface sits along that mean normal and how far it strays.
 *
 * @param granularity       the granular share, {@code 0..1}: {@code 0} one flat patch, {@code 1} smoke
 * @param rate              collisions per unit distance in the granular share, on average over the cell
 * @param normal            the plane's outward unit normal, pointing out of the solid; zero where there is no surface
 * @param offset            the plane's distance along {@code normal} from the cell's centre
 * @param footprint         where on the plane the solid's face lies; null for the plane's whole slice of the cell
 * @param materials         the chance of each material, given a hit; summing to one where there is surface
 * @param occupancy         the share of the cell inside the solid
 * @param solidCentroid     the centroid of that solid, from the cell's centre
 * @param solidSpread       the solid's mean squared distance from its centroid
 * @param solidFill         how much of the ball of that centroid and spread is solid: {@code 1} for a solid ball,
 *                          toward {@code 0} for a solid scattered thin; a ray through it is stopped with this chance
 * @param surfaceDensity    surface area per unit volume (in 2D, outline length per unit area)
 * @param meanNormal        the area-weighted mean of the outward unit normals
 * @param offsetVariance    how far the surface strays from {@code offset}, squared
 * @param attributeMean     the area-weighted mean of each continuous attribute
 * @param attributeVariance and its variance
 */
public record Vexel(double granularity, double rate, Vec3 normal, double offset, Footprint footprint,
                   float[] materials, double occupancy, Vec3 solidCentroid, double solidSpread, double solidFill,
                   double surfaceDensity, Vec3 meanNormal, double offsetVariance,
                   float[] attributeMean, float[] attributeVariance) {

    /**
     * A box on the plane, the part of it the cell's surface covers: from the surface's centroid and covariance, grown
     * until it holds the surface's area within the cell. In 2D a segment, {@code halfBreadth} unused.
     *
     * @param centre      its centre, on the plane, from the cell's centre
     * @param axis        the direction of its length, in the plane, unit length
     * @param halfLength  half its extent along {@code axis}
     * @param halfBreadth half its extent across it
     */
    public record Footprint(Vec3 centre, Vec3 axis, double halfLength, double halfBreadth) {
    }

    public boolean hasSurface() {
        return surfaceDensity > 0;
    }
}
