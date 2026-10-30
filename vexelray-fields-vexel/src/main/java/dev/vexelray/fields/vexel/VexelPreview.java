package dev.vexelray.fields.vexel;

import java.util.stream.IntStream;

/**
 * A vexel field drawn on the CPU, for looking at: one ray per pixel from a pinhole camera, each a
 * {@link VexelField#collide hashed collision}, shaded plainly. A stop on a flat share is grey-blue, lit by its normal;
 * a granular stop, which has no normal, is orange, so where detail has turned granular shows at a glance; a ray that
 * goes through is the dark background. Pixels are {@code 0xRRGGBB}, top row first.
 *
 * <p>Not how a renderer ought to shade a field: what it shows is what the field says, one stop at a time.
 */
public final class VexelPreview {

    /**
     * A pinhole camera at {@code eye} looking at {@code target}, {@code up} roughly up, with a vertical field of view
     * of {@code fov} degrees.
     */
    public record Camera(Vec3 eye, Vec3 target, Vec3 up, double fov) {

        /** The direction of the ray through pixel {@code (px, py)} of a {@code w × h} image. */
        Vec3 ray(double px, double py, int w, int h) {
            Vec3 forward = target.minus(eye).normalize();
            Vec3 right = forward.cross(up).normalize();
            Vec3 down = forward.cross(right).normalize();
            double s = Math.tan(Math.toRadians(fov) / 2);
            double u = (2 * (px + 0.5) / w - 1) * s * w / h;
            double v = (2 * (py + 0.5) / h - 1) * s;
            return forward.plus(right.times(u)).plus(down.times(v)).normalize();
        }
    }

    /** Where the light comes from, toward. */
    private static final Vec3 LIGHT = new Vec3(-0.45, -0.55, 0.7).normalize();
    private static final double[] SURFACE = {0.72, 0.80, 0.92};
    private static final double[] GRAIN = {0.98, 0.58, 0.22};
    private static final double[] BACK = {0.10, 0.11, 0.13};

    private VexelPreview() {
    }

    /** One frame: every pixel's outcome drawn by {@code hash}. */
    public static int[] render(VexelField field, Camera camera, int w, int h, VexelHash hash) {
        int[] out = new int[w * h];
        IntStream.range(0, h).parallel().forEach(y -> {
            for (int x = 0; x < w; x++) {
                out[y * w + x] = pack(shade(field.collide(camera.eye(), camera.ray(x, y, w, h),
                        Double.POSITIVE_INFINITY, hash)));
            }
        });
        return out;
    }

    /**
     * The average of {@code seeds} frames, the seed moved on between them: what frames accumulated over time
     * converge to, the expected image {@link VexelField#trace} describes.
     */
    public static int[] average(VexelField field, Camera camera, int w, int h, int steps, int seeds) {
        int[] out = new int[w * h];
        IntStream.range(0, h).parallel().forEach(y -> {
            for (int x = 0; x < w; x++) {
                Vec3 d = camera.ray(x, y, w, h);
                double[] sum = new double[3];
                for (int s = 0; s < seeds; s++) {
                    double[] c = shade(field.collide(camera.eye(), d, Double.POSITIVE_INFINITY,
                            new VexelHash(s, steps)));
                    for (int k = 0; k < 3; k++) {
                        sum[k] += c[k] / seeds;
                    }
                }
                out[y * w + x] = pack(sum);
            }
        });
        return out;
    }

    /**
     * The solid itself, for comparison: its distance ray-marched to the surface, shaded as a flat stop would be.
     * {@code solid}'s distance must not overstate (an exact distance, or less), as sphere tracing needs.
     */
    public static int[] reference(Solid solid, Camera camera, int w, int h, double far) {
        int[] out = new int[w * h];
        IntStream.range(0, h).parallel().forEach(y -> {
            for (int x = 0; x < w; x++) {
                Vec3 d = camera.ray(x, y, w, h);
                double t = 0;
                double[] c = BACK;
                for (int i = 0; i < 512 && t < far; i++) {
                    Vec3 p = camera.eye().plus(d.times(t));
                    double s = solid.distance(p.x(), p.y(), p.z());
                    if (s > -1e-4) {
                        c = lit(normal(solid, p));
                        break;
                    }
                    t += Math.max(-s, 1e-4);
                }
                out[y * w + x] = pack(c);
            }
        });
        return out;
    }

    private static Vec3 normal(Solid s, Vec3 p) {
        double e = 1e-4;
        return new Vec3(s.distance(p.x() - e, p.y(), p.z()) - s.distance(p.x() + e, p.y(), p.z()),
                s.distance(p.x(), p.y() - e, p.z()) - s.distance(p.x(), p.y() + e, p.z()),
                s.distance(p.x(), p.y(), p.z() - e) - s.distance(p.x(), p.y(), p.z() + e)).normalize();
    }

    private static double[] shade(Collision c) {
        if (c == null) {
            return BACK;
        }
        if (c.granular()) {
            return GRAIN;
        }
        return lit(c.normal());
    }

    private static double[] lit(Vec3 n) {
        double k = 0.22 + 0.78 * Math.max(0, n.dot(LIGHT));
        return new double[]{SURFACE[0] * k, SURFACE[1] * k, SURFACE[2] * k};
    }

    private static int pack(double[] c) {
        int r = (int) Math.round(Math.clamp(c[0], 0, 1) * 255);
        int g = (int) Math.round(Math.clamp(c[1], 0, 1) * 255);
        int b = (int) Math.round(Math.clamp(c[2], 0, 1) * 255);
        return r << 16 | g << 8 | b;
    }
}
