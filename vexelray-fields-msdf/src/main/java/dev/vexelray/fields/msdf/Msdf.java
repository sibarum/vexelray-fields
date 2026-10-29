package dev.vexelray.fields.msdf;

import dev.vexelray.fields.Field;
import dev.vexelray.fields.FieldKind;
import dev.vexelray.fields.Framing;
import dev.vexelray.fields.shape.Contour;
import dev.vexelray.fields.shape.Edge;
import dev.vexelray.fields.shape.Scanline;
import dev.vexelray.fields.shape.Shape;
import dev.vexelray.fields.shape.SignedDistance;
import dev.vexelray.fields.shape.Vec2;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Multi-channel signed distance fields, after Chlumský's msdfgen: per texel, three signed pseudo-distances, each to the
 * nearest edge of its colour, whose median is the edge, so a corner is two straight edges crossing rather than a
 * rounded blur. {@link FieldKind#MTSDF} adds the true distance in a fourth channel.
 *
 * <p>In three passes. Each channel's distance is the {@linkplain Edge#pseudoDistance pseudo-distance} of the
 * nearest edge of its colour. Then the sign: the shape is filled by the nonzero rule along each row, and a texel whose
 * median says otherwise has its channels turned over, so overlapping contours come out right. Then clashes: where
 * two neighbours' channels jump past each other (two edges of one colour meeting where the colouring could not keep
 * them apart), the texel farther from the edge is flattened to its median, which costs that corner its sharpness
 * there and saves the artefact.
 */
public final class Msdf {

    /** How far apart, in texels, two neighbours' channels may be before it is a clash: msdfgen's 1.001. */
    public static final double CLASH_THRESHOLD = 1.001;

    private Msdf() {
    }

    /** An {@link FieldKind#MSDF} of {@code shape}, coloured by {@link Coloring#simple(Shape)}. */
    public static Field generate(Shape shape, Framing framing, int width, int height) {
        return generate(FieldKind.MSDF, shape, framing, width, height);
    }

    /** An {@link FieldKind#MTSDF} of {@code shape}: the MSDF's three channels and the true distance. */
    public static Field generateMtsdf(Shape shape, Framing framing, int width, int height) {
        return generate(FieldKind.MTSDF, shape, framing, width, height);
    }

    public static Field generate(FieldKind kind, Shape shape, Framing framing, int width, int height) {
        return generate(kind, Coloring.simple(shape.oriented()), framing, width, height);
    }

    /**
     * A field of {@code kind} ({@link FieldKind#MSDF} or {@link FieldKind#MTSDF}) from a colouring in hand, whose
     * shape is already {@linkplain Shape#oriented() oriented}.
     */
    public static Field generate(FieldKind kind, Coloring coloring, Framing framing, int width, int height) {
        if (kind == FieldKind.SDF) {
            throw new IllegalArgumentException("a one-channel field is dev.vexelray.fields.sdf's");
        }
        List<Edge> edgeList = new ArrayList<>();
        List<Integer> colorList = new ArrayList<>();
        List<Contour> contours = coloring.shape().contours();
        for (int c = 0; c < contours.size(); c++) {
            List<Edge> es = contours.get(c).edges();
            for (int e = 0; e < es.size(); e++) {
                edgeList.add(es.get(e));
                colorList.add(coloring.colors()[c][e]);
            }
        }
        Edge[] edges = edgeList.toArray(Edge[]::new);
        int[] colors = colorList.stream().mapToInt(Integer::intValue).toArray();
        Shape shape = coloring.shape();
        Field f = new Field(kind, width, height);
        IntStream.range(0, height).parallel().forEach(y -> row(f, y, shape, edges, colors, framing));
        correctClashes(f, CLASH_THRESHOLD);
        return f;
    }

    private static void row(Field f, int y, Shape shape, Edge[] edges, int[] colors, Framing framing) {
        Scanline line = Scanline.of(shape, framing.toShape(0, y + 0.5).y());
        double scale = framing.scale();
        int n = f.channels();
        float[] data = f.data();
        for (int x = 0; x < f.width(); x++) {
            Vec2 p = framing.toShape(x + 0.5, y + 0.5);
            SignedDistance[] best = {SignedDistance.FAR, SignedDistance.FAR, SignedDistance.FAR};
            Edge.Nearest[] nearest = new Edge.Nearest[3];
            Edge[] edge = new Edge[3];
            double trueMin = Double.MAX_VALUE;
            for (int i = 0; i < edges.length; i++) {
                Edge.Nearest nr = edges[i].nearest(p);
                trueMin = Math.min(trueMin, Math.abs(nr.distance().distance()));
                for (int c = 0; c < 3; c++) {
                    if ((colors[i] & (1 << c)) != 0 && nr.distance().closerThan(best[c])) {
                        best[c] = nr.distance();
                        nearest[c] = nr;
                        edge[c] = edges[i];
                    }
                }
            }
            float[] d = new float[3];
            for (int c = 0; c < 3; c++) {
                d[c] = edge[c] == null ? -Float.MAX_VALUE
                        : (float) (edge[c].pseudoDistance(nearest[c], p).distance() * scale);
            }
            boolean filled = line.filled(p.x());
            if ((Field.median(d[0], d[1], d[2]) > 0) != filled) {
                for (int c = 0; c < 3; c++) {
                    d[c] = -d[c];
                }
            }
            int i = (y * f.width() + x) * n;
            data[i] = d[0];
            data[i + 1] = d[1];
            data[i + 2] = d[2];
            if (n == 4) {
                float t = (float) Math.min(trueMin * scale, Float.MAX_VALUE);
                data[i + 3] = filled ? t : -t;
            }
        }
    }

    /**
     * Flatten every texel that clashes with a neighbour (across, or diagonally at {@code √2} the threshold) to the
     * median of its three channels. {@code threshold} in texels.
     */
    public static void correctClashes(Field f, double threshold) {
        int w = f.width(), h = f.height();
        double diagonal = threshold * Math.sqrt(2);
        List<int[]> clashes = new ArrayList<>();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if ((x > 0 && clash(f, x, y, x - 1, y, threshold))
                        || (x < w - 1 && clash(f, x, y, x + 1, y, threshold))
                        || (y > 0 && clash(f, x, y, x, y - 1, threshold))
                        || (y < h - 1 && clash(f, x, y, x, y + 1, threshold))
                        || (x > 0 && y > 0 && clash(f, x, y, x - 1, y - 1, diagonal))
                        || (x < w - 1 && y > 0 && clash(f, x, y, x + 1, y - 1, diagonal))
                        || (x > 0 && y < h - 1 && clash(f, x, y, x - 1, y + 1, diagonal))
                        || (x < w - 1 && y < h - 1 && clash(f, x, y, x + 1, y + 1, diagonal))) {
                    clashes.add(new int[]{x, y});
                }
            }
        }
        for (int[] c : clashes) {
            float m = f.distance(c[0], c[1]);
            for (int k = 0; k < 3; k++) {
                f.set(c[0], c[1], k, m);
            }
        }
    }

    /**
     * Whether texel {@code a} clashes with neighbour {@code b}: both inside or both outside by the median, neither all
     * three channels on one side, exactly two channels crossing zero between them and each by at least
     * {@code threshold}, and {@code a} the one farther from the edge by the third.
     */
    private static boolean clash(Field f, int ax, int ay, int bx, int by, double threshold) {
        float[] a = {f.get(ax, ay, 0), f.get(ax, ay, 1), f.get(ax, ay, 2)};
        float[] b = {f.get(bx, by, 0), f.get(bx, by, 1), f.get(bx, by, 2)};
        if ((inside(a) >= 2) != (inside(b) >= 2)) {
            return false;
        }
        if (inside(a) % 3 == 0 || inside(b) % 3 == 0) {
            return false;   // a texel with every channel on one side is not where a clash is
        }
        int[] flipped = new int[3];
        int n = 0;
        for (int c = 0; c < 3; c++) {
            if ((a[c] > 0) != (b[c] > 0) && (a[c] < 0) != (b[c] < 0)) {
                if (n == 2) {
                    return false;
                }
                flipped[n++] = c;
            }
        }
        if (n != 2) {
            return false;
        }
        int rest = 3 - flipped[0] - flipped[1];
        return Math.abs(a[flipped[0]] - b[flipped[0]]) >= threshold
                && Math.abs(a[flipped[1]] - b[flipped[1]]) >= threshold
                && Math.abs(a[rest]) >= Math.abs(b[rest]);
    }

    private static int inside(float[] v) {
        return (v[0] > 0 ? 1 : 0) + (v[1] > 0 ? 1 : 0) + (v[2] > 0 ? 1 : 0);
    }
}
