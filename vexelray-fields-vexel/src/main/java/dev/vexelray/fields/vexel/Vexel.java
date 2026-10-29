package dev.vexelray.fields.vexel;

/**
 * One cell of a {@link VexelField}, read out: how much of it is solid, how much surface is in it, and the moments of
 * that surface.
 *
 * <p>The normals in a cell are modelled as a mixture: a share {@code |meanNormal|} of the surface faces exactly along
 * its direction, the rest every way alike. One flat patch has {@code |meanNormal| = 1}; a cell of fine bumps, or of
 * a whole small sphere, has less, and that shortfall is the roughness a coarser view of it ought to show.
 *
 * @param occupancy        the share of the cell inside the solid, {@code 0..1}
 * @param surfaceDensity   surface area per unit volume (in 2D, outline length per unit area)
 * @param meanNormal       the area-weighted mean of the outward unit normals; length {@code 0..1}
 * @param offsetMean       where the surface sits along {@code meanNormal}'s direction, from the cell's centre
 * @param offsetVariance   how far it strays from that, squared
 * @param attributeMean    the area-weighted mean of each attribute
 * @param attributeVariance and its variance
 */
public record Vexel(double occupancy, double surfaceDensity, Vec3 meanNormal, double offsetMean, double offsetVariance,
                   float[] attributeMean, float[] attributeVariance) {

    /** {@code 1 - |meanNormal|}: zero for one flat patch, toward one as the normals spread every way. */
    public double roughness() {
        return surfaceDensity == 0 ? 0 : 1 - meanNormal.length();
    }

    public boolean hasSurface() {
        return surfaceDensity > 0;
    }
}
