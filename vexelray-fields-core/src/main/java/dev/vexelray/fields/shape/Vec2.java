package dev.vexelray.fields.shape;

/** A point or a direction in the plane, in a shape's own units. */
public record Vec2(double x, double y) {

    public static final Vec2 ZERO = new Vec2(0, 0);

    public Vec2 plus(Vec2 o) {
        return new Vec2(x + o.x, y + o.y);
    }

    public Vec2 minus(Vec2 o) {
        return new Vec2(x - o.x, y - o.y);
    }

    public Vec2 times(double s) {
        return new Vec2(x * s, y * s);
    }

    public Vec2 negate() {
        return new Vec2(-x, -y);
    }

    public double dot(Vec2 o) {
        return x * o.x + y * o.y;
    }

    /** The z of the cross product: positive when {@code o} turns left from this, in a y-up frame. */
    public double cross(Vec2 o) {
        return x * o.y - y * o.x;
    }

    public double length() {
        return Math.hypot(x, y);
    }

    public boolean isZero() {
        return x == 0 && y == 0;
    }

    /** This at length one; {@link #ZERO} for zero. */
    public Vec2 normalize() {
        double l = length();
        return l == 0 ? ZERO : new Vec2(x / l, y / l);
    }

    /** This turned a quarter right, at length one: the side a contour's inside is on. */
    public Vec2 rightNormal() {
        double l = length();
        return l == 0 ? ZERO : new Vec2(y / l, -x / l);
    }

    /** The point {@code t} of the way from {@code a} to {@code b}. */
    public static Vec2 mix(Vec2 a, Vec2 b, double t) {
        return new Vec2(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t);
    }
}
