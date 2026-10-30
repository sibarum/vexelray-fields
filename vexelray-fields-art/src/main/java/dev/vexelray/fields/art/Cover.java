package dev.vexelray.fields.art;

import dev.vexelray.fields.shape.Bounds;
import dev.vexelray.fields.shape.Scanline;
import dev.vexelray.fields.shape.Shape;
import dev.vexelray.fields.shape.Vec2;

import java.util.Arrays;

/**
 * Which pixels one paint covers, each sampled at its centre: a fill by its rule, or a stroke as the union of the pieces
 * {@link Stroker} cuts it into. Only the box it has touched is looked at again, so many small paints on a large canvas
 * cost what they cover.
 */
final class Cover {

    final int width;
    final int height;
    final boolean[] bits;
    int x0, y0, x1, y1;   // the box touched, x1 and y1 past it

    Cover(int width, int height) {
        this.width = width;
        this.height = height;
        this.bits = new boolean[width * height];
        reset();
    }

    boolean isEmpty() {
        return x0 >= x1 || y0 >= y1;
    }

    void clear() {
        for (int y = y0; y < y1; y++) {
            Arrays.fill(bits, y * width + x0, y * width + x1, false);
        }
        reset();
    }

    private void reset() {
        x0 = width;
        y0 = height;
        x1 = 0;
        y1 = 0;
    }

    private void touch(int ax, int ay, int bx, int by) {
        x0 = Math.min(x0, ax);
        y0 = Math.min(y0, ay);
        x1 = Math.max(x1, bx);
        y1 = Math.max(y1, by);
    }

    /** The pixel range whose centres can fall in {@code [lo, hi]}, clamped to {@code [0, n)}. */
    private static int first(double lo, int n) {
        return Math.clamp((long) Math.ceil(lo - 0.5), 0, n);
    }

    private static int last(double hi, int n) {
        return Math.clamp((long) Math.floor(hi - 0.5) + 1, 0, n);
    }

    /** {@code shape}, in pixels, filled by the nonzero rule or, if {@code evenOdd}, the even-odd. */
    void fill(Shape shape, boolean evenOdd) {
        Bounds b = shape.bounds();
        if (b.isEmpty()) {
            return;
        }
        int ya = first(b.yMin(), height), yb = last(b.yMax(), height);
        int xa = first(b.xMin(), width), xb = last(b.xMax(), width);
        for (int y = ya; y < yb; y++) {
            Scanline line = Scanline.of(shape, y + 0.5);
            if (line.size() == 0) {
                continue;
            }
            for (int x = xa; x < xb; x++) {
                int w = line.winding(x + 0.5);
                if (evenOdd ? (w & 1) != 0 : w != 0) {
                    bits[y * width + x] = true;
                }
            }
        }
        touch(xa, ya, xb, yb);
    }

    /** A convex polygon, wound either way. */
    void convex(Vec2... p) {
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (Vec2 v : p) {
            minX = Math.min(minX, v.x());
            minY = Math.min(minY, v.y());
            maxX = Math.max(maxX, v.x());
            maxY = Math.max(maxY, v.y());
        }
        int ya = first(minY, height), yb = last(maxY, height);
        int xa = first(minX, width), xb = last(maxX, width);
        int n = p.length;
        for (int y = ya; y < yb; y++) {
            double cy = y + 0.5;
            for (int x = xa; x < xb; x++) {
                double cx = x + 0.5;
                boolean pos = false, neg = false;
                for (int i = 0; i < n && !(pos && neg); i++) {
                    Vec2 a = p[i], b = p[(i + 1) % n];
                    double s = (b.x() - a.x()) * (cy - a.y()) - (b.y() - a.y()) * (cx - a.x());
                    pos |= s > 0;
                    neg |= s < 0;
                }
                if (!(pos && neg)) {
                    bits[y * width + x] = true;
                }
            }
        }
        touch(xa, ya, xb, yb);
    }

    /** A disc of radius {@code r} round {@code c}. */
    void disc(Vec2 c, double r) {
        int ya = first(c.y() - r, height), yb = last(c.y() + r, height);
        int xa = first(c.x() - r, width), xb = last(c.x() + r, width);
        double rr = r * r;
        for (int y = ya; y < yb; y++) {
            double dy = y + 0.5 - c.y();
            for (int x = xa; x < xb; x++) {
                double dx = x + 0.5 - c.x();
                if (dx * dx + dy * dy <= rr) {
                    bits[y * width + x] = true;
                }
            }
        }
        touch(xa, ya, xb, yb);
    }
}
