package dev.vexelray.fields;

/**
 * A field drawn back to coverage, on the CPU, the way the stack's shaders draw one: sample, take the distance in
 * output pixels, and cut half a pixel either side of the edge. For previews, and for checking a field against the
 * shape it was made from.
 */
public final class Coverage {

    private Coverage() {
    }

    /**
     * The field stretched over {@code width × height} pixels: per pixel, coverage {@code 0..1}, top row first. One
     * texel spans {@code width / field.width()} pixels, which is what turns a distance in texels into one in pixels.
     */
    public static float[] render(Field field, int width, int height) {
        float[] out = new float[width * height];
        double sx = (double) field.width() / width;
        double sy = (double) field.height() / height;
        double pixelsPerTexel = 1 / Math.max(sx, sy);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                float d = field.sample((x + 0.5) * sx, (y + 0.5) * sy);
                out[y * width + x] = (float) Math.clamp(0.5 + d * pixelsPerTexel, 0, 1);
            }
        }
        return out;
    }
}
