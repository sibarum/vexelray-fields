package dev.vexelray.fields.art;

import dev.vexelray.fields.shape.Bounds;
import dev.vexelray.fields.shape.Vec2;

import java.util.List;
import java.util.Optional;

/**
 * A {@code <text>} as {@link Svg#tree} gives it: its characters in runs, not yet set in any font, since setting them
 * needs the font, which the art does not have. A run is characters of one style that follow on from each other; each
 * run either follows on from the one before it, or, when SVG positions its first character ({@code x}, {@code y},
 * {@code dx}, {@code dy}), starts there. White space is already as SVG's default {@code xml:space} leaves it: line
 * breaks gone, tabs spaces, runs of spaces one, none at either end.
 *
 * <p>Positions are in the element's own user units, y down, with the pen on the baseline; {@link #place} takes one to
 * the view box. Text whose transform turns, skews or flips it is refused when read, so {@link #scale} is all a size
 * needs.
 */
public final class SvgText implements SvgNode {

    private final String id;
    private final List<Run> runs;
    private final Affine ctm;

    SvgText(String id, List<Run> runs, Affine ctm) {
        this.id = id;
        this.runs = List.copyOf(runs);
        this.ctm = ctm;
    }

    /** The element's id, or the outermost id it is drawn under. */
    public Optional<String> name() {
        return Optional.ofNullable(id);
    }

    /** In document order. */
    public List<Run> runs() {
        return runs;
    }

    /** A point in the text's user units, in the view box's. */
    public Vec2 place(double x, double y) {
        return ctm.apply(new Vec2(x, y));
    }

    /** View-box units a user unit: the text's transform is a uniform scale and a move. */
    public double scale() {
        return ctm.scale();
    }

    /**
     * Characters of one style.
     *
     * @param text          the characters, white space already collapsed
     * @param x             where the run's first character goes across, in user units; NaN to follow on
     * @param y             and down, the baseline; NaN to follow on
     * @param dx            a shift across before the first character, after {@code x}
     * @param dy            and down
     * @param family        {@code font-family}, as written: a list, generic names last
     * @param size          {@code font-size} in user units
     * @param weight        {@code font-weight}, 100 to 900
     * @param italic        whether {@code font-style} is italic or oblique
     * @param anchor        {@code text-anchor} where the run starts a chunk: start, middle or end
     * @param letterSpacing extra user units after each character
     * @param fill          the run's colour
     * @param opacity       {@code fill-opacity} times the element's {@code opacity}
     */
    public record Run(String text, double x, double y, double dx, double dy, String family, double size, int weight,
                      boolean italic, String anchor, double letterSpacing, Fill fill, float opacity) {

        /** Whether the run starts a chunk of its own, as SVG's {@code text-anchor} counts them. */
        public boolean startsChunk() {
            return !Double.isNaN(x) || !Double.isNaN(y);
        }
    }

    /** What colour a run is at a view-box point, straight RGBA. */
    public interface Colour {
        void at(double x, double y, float[] out);
    }

    /** A run's fill: one colour, or a gradient, which needs the text's box to know where it lies. */
    public static final class Fill {

        private final Style.Paint paint;
        private final Affine ctm;
        private final Bounds viewBox;

        Fill(Style.Paint paint, Affine ctm, Bounds viewBox) {
            this.paint = paint;
            this.ctm = ctm;
            this.viewBox = viewBox;
        }

        /** Straight RGB, when the fill is one colour. */
        public Optional<float[]> solid() {
            return paint.gradient() == null ? Optional.of(paint.rgb().clone()) : Optional.empty();
        }

        /**
         * The gradient laid over {@code box}, the text's box in its user units (what a gradient in
         * {@code objectBoundingBox} units spans); empty if it draws nothing there, a box with no area say.
         */
        public Optional<Colour> gradient(Bounds box) {
            if (paint.gradient() == null) {
                float[] c = paint.rgb();
                return Optional.of((x, y, out) -> {
                    out[0] = c[0];
                    out[1] = c[1];
                    out[2] = c[2];
                    out[3] = 1;
                });
            }
            Gradient.Shader shader = paint.gradient().shader(box, ctm, viewBox);
            return shader == null ? Optional.empty() : Optional.of(shader::at);
        }

        /** Whether two runs are filled alike, and so can be one layer. */
        public boolean sameAs(Fill other) {
            return paint == other.paint || paint.gradient() == null && other.paint.gradient() == null
                    && java.util.Arrays.equals(paint.rgb(), other.paint.rgb());
        }
    }
}
