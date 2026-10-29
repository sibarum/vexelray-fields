package dev.vexelray.fields;

import dev.vexelray.fields.shape.Bounds;
import dev.vexelray.fields.shape.Vec2;

/**
 * Where a shape sits on a field's texels: {@code scale} texels to a shape unit, the shape point at the field's
 * top-left corner, and which way the shape's y runs. Texel {@code (x, y)} covers {@code [x, x+1) × [y, y+1)} with
 * row 0 at the top, and is sampled at its centre.
 *
 * @param scale   texels per shape unit
 * @param originX the shape x at the field's left edge
 * @param originY the shape y at the field's top edge
 * @param yUp     whether shape y grows upward (font units) rather than downward (SVG)
 */
public record Framing(double scale, double originX, double originY, boolean yUp) {

    public Framing {
        if (!(scale > 0) || Double.isInfinite(scale)) {
            throw new IllegalArgumentException("a framing at a scale of " + scale);
        }
    }

    /**
     * The framing that fits {@code bounds} into a {@code width × height} field with {@code padding} texels clear
     * round it, as large as it fits and centred.
     */
    public static Framing fit(Bounds bounds, int width, int height, double padding, boolean yUp) {
        double w = Math.max(bounds.width(), 1e-12);
        double h = Math.max(bounds.height(), 1e-12);
        double scale = Math.min((width - 2 * padding) / w, (height - 2 * padding) / h);
        double cx = (bounds.xMin() + bounds.xMax()) / 2;
        double cy = (bounds.yMin() + bounds.yMax()) / 2;
        double halfW = width / 2.0 / scale;
        double halfH = height / 2.0 / scale;
        return new Framing(scale, cx - halfW, yUp ? cy + halfH : cy - halfH, yUp);
    }

    /** The shape point at texel coordinates {@code (u, v)}; the centre of texel {@code (x, y)} is {@code (x+.5, y+.5)}. */
    public Vec2 toShape(double u, double v) {
        return new Vec2(originX + u / scale, yUp ? originY - v / scale : originY + v / scale);
    }

    /** The texel coordinates of shape point {@code p}. */
    public Vec2 toTexels(Vec2 p) {
        return new Vec2((p.x() - originX) * scale, (yUp ? originY - p.y() : p.y() - originY) * scale);
    }
}
