package dev.vexelray.fields.convert;

/**
 * Inside or out, per sample, on a grid laid over a drawing's units: what a shape's fill is before it is a distance, and
 * what a sampled field is traced back from.
 *
 * <p>Sample {@code (x, y)} covers {@code [originX + x · unitsPerSample, ... + unitsPerSample)} across and likewise down
 * (y down, as SVG and SDG run), row 0 at the top. {@code oversample} samples make a texel each way, for the field the
 * mask is made into: {@code width} and {@code height} are whole texels of it.
 *
 * @param inside {@code inside[y * width + x]}; never changed after the mask is made
 */
public record Mask(int width, int height, boolean[] inside, int oversample, double originX, double originY,
                   double unitsPerSample) {

    public Mask {
        if (width <= 0 || height <= 0 || inside.length != width * height || oversample <= 0
                || width % oversample != 0 || height % oversample != 0 || !(unitsPerSample > 0)) {
            throw new IllegalArgumentException("a mask " + width + " × " + height + " of " + inside.length
                    + " samples, " + oversample + " to a texel, " + unitsPerSample + " units each");
        }
    }

    public boolean at(int x, int y) {
        return x >= 0 && y >= 0 && x < width && y < height && inside[y * width + x];
    }
}
