package dev.vexelray.fields.shape;

/**
 * How far a point is from an edge, signed, and how squarely the edge meets it there: {@code dot} is zero where the
 * nearest point is inside the edge, and the cosine between the edge's end direction and the way to the point where
 * it is an end. Of two equal distances, the one met more squarely (the smaller {@code dot}) is the nearer, which is
 * what picks the right edge at a corner.
 *
 * @param distance positive on the edge's right, the inside of a contour wound as {@link Shape#oriented} winds it
 * @param dot      zero for a perpendicular foot, else how obliquely the nearest end is met
 */
public record SignedDistance(double distance, double dot) {

    public static final SignedDistance FAR = new SignedDistance(-Double.MAX_VALUE, 1);

    /** Whether this is nearer than {@code o}. */
    public boolean closerThan(SignedDistance o) {
        double a = Math.abs(distance);
        double b = Math.abs(o.distance);
        return a < b || (a == b && dot < o.dot);
    }
}
