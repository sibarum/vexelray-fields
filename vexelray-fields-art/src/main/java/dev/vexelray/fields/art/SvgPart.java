package dev.vexelray.fields.art;

import dev.vexelray.fields.shape.Bounds;
import dev.vexelray.fields.shape.Path;
import dev.vexelray.fields.shape.Shape;

import java.util.Optional;

/**
 * One paint of a drawing: a shape's fill or its stroke, in the view box's units with y down, and what it is painted
 * with. {@link Svg#parts} lists a drawing's parts in the order they are painted, for whatever keeps a drawing as layers
 * rather than pixels.
 */
public final class SvgPart implements SvgNode {

    private final Path path;          // in view-box units
    private final boolean stroke;
    private final boolean evenOdd;
    private final double strokeWidth; // in view-box units
    private final Stroker.Cap cap;
    private final Stroker.Join join;
    private final double miterLimit;
    private final double[] dashes;
    private final double dashOffset;
    private final Gradient.Shader shader;
    private final float[] solid;      // straight rgb, or null for a gradient
    private final float opacity;
    private final String id;          // the outermost id it is drawn under, or null

    SvgPart(Path path, boolean stroke, boolean evenOdd, double strokeWidth, Stroker.Cap cap, Stroker.Join join,
            double miterLimit, double[] dashes, double dashOffset, Gradient.Shader shader, float[] solid,
            float opacity, String id) {
        this.id = id;
        this.path = path;
        this.stroke = stroke;
        this.evenOdd = evenOdd;
        this.strokeWidth = strokeWidth;
        this.cap = cap;
        this.join = join;
        this.miterLimit = miterLimit;
        this.dashes = dashes;
        this.dashOffset = dashOffset;
        this.shader = shader;
        this.solid = solid;
        this.opacity = opacity;
    }

    /**
     * The id of the outermost element it is drawn under that has one (itself, its group, a group of that), as
     * {@link Svg#only} and {@link Svg#without} name a part; empty if none has.
     */
    public Optional<String> id() {
        return Optional.ofNullable(id);
    }

    /** Whether this is a stroke rather than a fill. */
    public boolean stroke() {
        return stroke;
    }

    /** The fill or stroke opacity, times nothing else: a part never sits under a group's opacity. */
    public float opacity() {
        return opacity;
    }

    /** The paint's one colour, straight {@code r, g, b} in {@code 0..1}; empty for a gradient. */
    public Optional<float[]> solid() {
        return solid == null ? Optional.empty() : Optional.of(solid.clone());
    }

    /** The paint at {@code (x, y)} in view-box units, premultiplied {@code r, g, b, a} into {@code out}. */
    public void colour(double x, double y, float[] out) {
        shader.at(x, y, out);
    }

    /** What the part may cover, in view-box units: its path's box, and for a stroke as far as a miter can reach. */
    public Bounds bounds() {
        Bounds b = path.bounds();
        if (!stroke) {
            return b;
        }
        double r = strokeWidth / 2 * Math.max(Math.sqrt(2), join == Stroker.Join.MITER ? miterLimit : 1);
        return new Bounds(b.xMin() - r, b.yMin() - r, b.xMax() + r, b.yMax() + r);
    }

    /**
     * The area exactly, as edges, for a fill by the nonzero rule; empty for a stroke or an even-odd fill, which are
     * drawn as a {@link #mask} instead.
     */
    public Optional<Shape> outline() {
        if (stroke || evenOdd) {
            return Optional.empty();
        }
        Shape s = path.shape().oriented();
        return s.contours().isEmpty() ? Optional.empty() : Optional.of(s);
    }

    /**
     * What the part covers, as a mask {@code width × height}: pixel {@code (i, j)} is the view-box point
     * {@code (originX + (i + .5) / scale, originY + (j + .5) / scale)}, covered or not at that point.
     */
    public boolean[] mask(double originX, double originY, double scale, int width, int height) {
        Affine m = Affine.scale(scale, scale).times(Affine.translate(-originX, -originY));
        Path px = path.mapped(m::apply);
        Cover cover = new Cover(width, height);
        if (stroke) {
            double[] d = dashes == null ? null : dashes.clone();
            if (d != null) {
                for (int i = 0; i < d.length; i++) {
                    d[i] *= scale;
                }
            }
            Stroker.stroke(cover, px, strokeWidth * scale, cap, join, miterLimit, d, dashOffset * scale);
        } else {
            cover.fill(px.shape(), evenOdd);
        }
        return cover.bits;
    }
}
