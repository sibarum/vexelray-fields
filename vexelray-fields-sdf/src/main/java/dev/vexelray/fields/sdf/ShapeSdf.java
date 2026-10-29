package dev.vexelray.fields.sdf;

import dev.vexelray.fields.Field;
import dev.vexelray.fields.FieldKind;
import dev.vexelray.fields.Framing;
import dev.vexelray.fields.shape.Contour;
import dev.vexelray.fields.shape.Edge;
import dev.vexelray.fields.shape.Scanline;
import dev.vexelray.fields.shape.Shape;
import dev.vexelray.fields.shape.Vec2;

/**
 * A shape's signed distance field from its edges: at each texel's centre, the exact distance to the nearest edge,
 * signed by the nonzero fill. The sign comes from a {@link Scanline} per row, not from the nearest edge, so contours
 * wound either way, or overlapping, come out as they are filled.
 */
public final class ShapeSdf {

    private ShapeSdf() {
    }

    /** The field of {@code shape} placed by {@code framing}, {@code width × height} texels, distances in texels. */
    public static Field generate(Shape shape, Framing framing, int width, int height) {
        Field f = new Field(FieldKind.SDF, width, height);
        Edge[] edges = shape.contours().stream().map(Contour::edges).flatMap(java.util.List::stream)
                .toArray(Edge[]::new);
        for (int y = 0; y < height; y++) {
            Scanline line = Scanline.of(shape, framing.toShape(0, y + 0.5).y());
            for (int x = 0; x < width; x++) {
                Vec2 p = framing.toShape(x + 0.5, y + 0.5);
                double min = Double.MAX_VALUE;
                for (Edge e : edges) {
                    min = Math.min(min, Math.abs(e.nearest(p).distance().distance()));
                }
                double d = Math.min(min * framing.scale(), Float.MAX_VALUE);
                f.set(x, y, 0, (float) (line.filled(p.x()) ? d : -d));
            }
        }
        return f;
    }
}
