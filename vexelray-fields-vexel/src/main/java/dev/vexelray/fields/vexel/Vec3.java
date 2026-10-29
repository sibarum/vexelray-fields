package dev.vexelray.fields.vexel;

/** A point or direction in a vexel field's world units. */
public record Vec3(double x, double y, double z) {

    public static final Vec3 ZERO = new Vec3(0, 0, 0);

    public Vec3 plus(Vec3 o) {
        return new Vec3(x + o.x, y + o.y, z + o.z);
    }

    public Vec3 minus(Vec3 o) {
        return new Vec3(x - o.x, y - o.y, z - o.z);
    }

    public Vec3 times(double s) {
        return new Vec3(x * s, y * s, z * s);
    }

    public double dot(Vec3 o) {
        return x * o.x + y * o.y + z * o.z;
    }

    public double length() {
        return Math.sqrt(dot(this));
    }

    /** This at length one; {@link #ZERO} for zero. */
    public Vec3 normalize() {
        double l = length();
        return l == 0 ? ZERO : times(1 / l);
    }
}
