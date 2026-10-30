package dev.vexelray.fields.vexel;

import dev.vexelray.fields.shape.Contour;
import dev.vexelray.fields.shape.Edge;
import dev.vexelray.fields.shape.Shape;
import dev.vexelray.fields.shape.SignedDistance;
import dev.vexelray.fields.shape.Vec2;

/**
 * What a vexel field is baked from: a signed distance, positive inside as everywhere in this library, and what the
 * surface is made of at a point on it. Need not be exact: the bake reads the distance only near the surface, and only
 * its zero and its gradient there matter.
 */
public interface Solid {

    /** The signed distance at {@code (x, y, z)}, positive inside. */
    double distance(double x, double y, double z);

    /** How many materials {@link #material} chooses among. One by default. */
    default int materials() {
        return 1;
    }

    /** The material of the surface at {@code p}, a point on it whose outward normal is {@code normal}. */
    default int material(Vec3 p, Vec3 normal) {
        return 0;
    }

    /** How many continuous attributes {@link #attributes(Vec3, Vec3)} gives. None by default. */
    default int attributes() {
        return 0;
    }

    /**
     * Continuous attributes of the surface at {@code p}, beside its material: a tint, a wetness, whatever varies
     * smoothly. A field keeps their mean and variance.
     */
    default float[] attributes(Vec3 p, Vec3 normal) {
        return new float[0];
    }

    /** No solid anywhere: for a field of smoke alone. */
    static Solid empty() {
        return (x, y, z) -> -1e9;
    }

    /** A sphere of {@code radius} round {@code centre}. */
    static Solid sphere(Vec3 centre, double radius) {
        return (x, y, z) -> radius - new Vec3(x, y, z).minus(centre).length();
    }

    /** The half-space on the side of the plane through {@code point} away from {@code outward}. */
    static Solid halfSpace(Vec3 point, Vec3 outward) {
        Vec3 n = outward.normalize();
        return (x, y, z) -> -new Vec3(x, y, z).minus(point).dot(n);
    }

    /**
     * A 2D shape as a solid prism along z, its distance the shape's own, from the nearest edge of the shape
     * {@linkplain Shape#oriented() oriented}. For a 2D vexel field.
     */
    static Solid prism(Shape shape) {
        Edge[] edges = shape.oriented().contours().stream().map(Contour::edges)
                .flatMap(java.util.List::stream).toArray(Edge[]::new);
        return (x, y, z) -> {
            Vec2 p = new Vec2(x, y);
            SignedDistance best = SignedDistance.FAR;
            for (Edge e : edges) {
                SignedDistance d = e.nearest(p).distance();
                if (d.closerThan(best)) {
                    best = d;
                }
            }
            return best.distance();
        };
    }
}
