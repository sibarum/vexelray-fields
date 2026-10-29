package dev.vexelray.fields.shape;

/** An axis-aligned box in a shape's units. {@link #EMPTY} is inverted, so the first point included sets it. */
public record Bounds(double xMin, double yMin, double xMax, double yMax) {

    public static final Bounds EMPTY = new Bounds(Double.MAX_VALUE, Double.MAX_VALUE,
            -Double.MAX_VALUE, -Double.MAX_VALUE);

    public boolean isEmpty() {
        return xMin > xMax || yMin > yMax;
    }

    public double width() {
        return xMax - xMin;
    }

    public double height() {
        return yMax - yMin;
    }

    public Bounds include(Vec2 p) {
        return new Bounds(Math.min(xMin, p.x()), Math.min(yMin, p.y()),
                Math.max(xMax, p.x()), Math.max(yMax, p.y()));
    }

    public Bounds include(Bounds b) {
        return b.isEmpty() ? this : new Bounds(Math.min(xMin, b.xMin), Math.min(yMin, b.yMin),
                Math.max(xMax, b.xMax), Math.max(yMax, b.yMax));
    }
}
