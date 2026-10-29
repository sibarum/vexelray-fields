package dev.vexelray.fields.shape;

import java.util.ArrayList;
import java.util.List;

/** A closed loop of edges, each starting where the one before ends. */
public record Contour(List<Edge> edges) {

    public Contour {
        edges = List.copyOf(edges);
        if (edges.isEmpty()) {
            throw new IllegalArgumentException("a contour of no edges");
        }
    }

    public Bounds bounds() {
        Bounds b = Bounds.EMPTY;
        for (Edge e : edges) {
            b = b.include(e.bounds());
        }
        return b;
    }

    /** The same loop the other way round. */
    public Contour reversed() {
        List<Edge> out = new ArrayList<>(edges.size());
        for (int i = edges.size() - 1; i >= 0; i--) {
            out.add(edges.get(i).reversed());
        }
        return new Contour(out);
    }

    /**
     * Twice the signed area the loop's polygon of ends and control points encloses: positive anticlockwise in a
     * y-up frame. Exact for lines; for curves, the sign is what matters, and it is right for any loop that does not
     * cross itself.
     */
    public double winding() {
        double total = 0;
        for (Edge e : edges) {
            Vec2 a = e.start();
            Vec2 b = e.end();
            Vec2 m = e.point(0.5);
            total += a.cross(m) + m.cross(b);
        }
        return total;
    }
}
