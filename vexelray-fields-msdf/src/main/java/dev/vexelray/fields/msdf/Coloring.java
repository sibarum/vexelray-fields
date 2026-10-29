package dev.vexelray.fields.msdf;

import dev.vexelray.fields.shape.Contour;
import dev.vexelray.fields.shape.Edge;
import dev.vexelray.fields.shape.Shape;
import dev.vexelray.fields.shape.Vec2;

import java.util.ArrayList;
import java.util.List;

/**
 * Which channels each edge is drawn into: the colouring an MSDF is made by. Two edges that meet at a corner never
 * share all of their channels, so at the corner each channel sees the corner's two sides as one straight edge running
 * on, and the median of the three keeps the corner sharp. Smooth joins share a colour, so a curve stays one curve.
 *
 * <p>This is msdfgen's simple colouring, deterministic for a seed: a contour with no corners is one colour, one with
 * one corner (a teardrop) is split three ways symmetrically about it, and one with more changes colour at each.
 *
 * @param shape  the shape coloured, with any contour of fewer than three edges and one corner split into thirds
 * @param colors per contour, per edge, the channels as a mask of {@link #RED}, {@link #GREEN} and {@link #BLUE}
 */
public record Coloring(Shape shape, int[][] colors) {

    public static final int RED = 1;
    public static final int GREEN = 2;
    public static final int BLUE = 4;
    public static final int YELLOW = RED | GREEN;
    public static final int MAGENTA = RED | BLUE;
    public static final int CYAN = GREEN | BLUE;
    public static final int WHITE = RED | GREEN | BLUE;

    /** The angle, in radians, a join must turn by to be a corner; msdfgen's default. */
    public static final double CORNER_ANGLE = 3.0;

    /** msdfgen's simple colouring with its default corner angle and seed. */
    public static Coloring simple(Shape shape) {
        return simple(shape, CORNER_ANGLE, 0);
    }

    public static Coloring simple(Shape shape, double cornerAngle, long seed) {
        double crossThreshold = Math.sin(cornerAngle);
        long[] s = {seed};
        int color = WHITE;
        List<Contour> contours = new ArrayList<>();
        int[][] colors = new int[shape.contours().size()][];
        for (int ci = 0; ci < shape.contours().size(); ci++) {
            List<Edge> edges = new ArrayList<>(shape.contours().get(ci).edges());
            int m = edges.size();
            List<Integer> corners = new ArrayList<>();
            Vec2 prev = edges.get(m - 1).direction(1);
            for (int i = 0; i < m; i++) {
                if (isCorner(prev.normalize(), edges.get(i).direction(0).normalize(), crossThreshold)) {
                    corners.add(i);
                }
                prev = edges.get(i).direction(1);
            }
            int[] out;
            if (corners.isEmpty()) {
                color = next(color, s, 0);
                out = new int[m];
                java.util.Arrays.fill(out, color);
            } else if (corners.size() == 1) {
                int[] three = new int[3];
                color = next(color, s, 0);
                three[0] = color;
                three[1] = WHITE;
                color = next(color, s, 0);
                three[2] = color;
                int corner = corners.getFirst();
                if (m >= 3) {
                    out = new int[m];
                    for (int i = 0; i < m; i++) {
                        out[(corner + i) % m] = three[1 + trichotomy(i, m)];
                    }
                } else {
                    // fewer edges than colours: split each in three, the corner's edge first
                    Edge[] parts = new Edge[6];
                    Edge[] a = edges.get(0).thirds();
                    System.arraycopy(a, 0, parts, 3 * corner, 3);
                    if (m == 2) {
                        Edge[] b = edges.get(1).thirds();
                        System.arraycopy(b, 0, parts, 3 - 3 * corner, 3);
                        out = new int[]{three[0], three[0], three[1], three[1], three[2], three[2]};
                    } else {
                        out = new int[]{three[0], three[1], three[2]};
                    }
                    edges = new ArrayList<>(java.util.Arrays.asList(parts).subList(0, out.length));
                }
            } else {
                int cornerCount = corners.size();
                int spline = 0;
                int start = corners.getFirst();
                color = next(color, s, 0);
                int initial = color;
                out = new int[m];
                for (int i = 0; i < m; i++) {
                    int index = (start + i) % m;
                    if (spline + 1 < cornerCount && corners.get(spline + 1) == index) {
                        spline++;
                        color = next(color, s, spline == cornerCount - 1 ? initial : 0);
                    }
                    out[index] = color;
                }
            }
            contours.add(new Contour(edges));
            colors[ci] = out;
        }
        return new Coloring(new Shape(contours), colors);
    }

    private static boolean isCorner(Vec2 a, Vec2 b, double crossThreshold) {
        return a.dot(b) <= 0 || Math.abs(a.cross(b)) > crossThreshold;
    }

    /** Which of three parts, -1, 0 or 1, position {@code i} of {@code n} falls in, symmetrically. */
    private static int trichotomy(int i, int n) {
        return (int) (3 + 2.875 * i / (n - 1) - 1.4375 + 0.5) - 3;
    }

    /** The colour after {@code color}: two channels, never the same two, and never sharing {@code banned}'s pair. */
    private static int next(int color, long[] seed, int banned) {
        int combined = color & banned;
        if (combined == RED || combined == GREEN || combined == BLUE) {
            return combined ^ WHITE;
        }
        if (color == 0 || color == WHITE) {
            int[] start = {CYAN, MAGENTA, YELLOW};
            int c = start[(int) Long.remainderUnsigned(seed[0], 3)];
            seed[0] = Long.divideUnsigned(seed[0], 3);
            return c;
        }
        int shifted = color << (1 + (int) (seed[0] & 1));
        seed[0] >>>= 1;
        return (shifted | shifted >> 3) & WHITE;
    }
}
