package dev.vexelray.fields.convert;

/**
 * How much of each pixel is inside, {@code 0..1}, top row first: a field drawn back as the stack's shaders draw one.
 *
 * @param cover {@code cover[y * width + x]}; never changed after the map is made
 */
public record CoverageMap(int width, int height, float[] cover) {

    public CoverageMap {
        if (width <= 0 || height <= 0 || cover.length != width * height) {
            throw new IllegalArgumentException("a coverage map " + width + " × " + height + " of " + cover.length);
        }
    }
}
