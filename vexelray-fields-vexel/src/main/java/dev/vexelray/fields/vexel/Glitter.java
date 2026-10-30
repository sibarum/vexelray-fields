package dev.vexelray.fields.vexel;

/**
 * Sparkle: tiny mirror flakes over a granular stop, each a cube {@code size} across in the field's own space with a
 * tilt drawn from its coordinates and the view's angle bucket. Because the grid is fixed in the field's space and not
 * in its cells, a flake on a surface is in the same place at every level of the field; because the tilt is drawn
 * per angle bucket, it holds still while the view stays in one and flips when it crosses into another.
 *
 * <p>A flake flashes when its normal lies within a cone of the half-vector between the light and the eye: the
 * narrower the cone ({@code sharpness}), the rarer and briefer each flash. {@code mirrors} is the share of flakes that
 * are mirrors at all; the rest are matte.
 *
 * @param size      a flake's side, in the field's units
 * @param mirrors   the share of flakes that reflect, {@code 0..1}
 * @param sharpness how tightly a flake must face the half-vector to flash: the cone's {@code 1 - cos}, inverted
 * @param hash      where the draws come from; its seed moves every flake at once
 */
public record Glitter(double size, double mirrors, double sharpness, VexelHash hash) {

    /** Draws a flake makes, past those a field's own outcomes use. */
    private static final int TILT_Z = 16, TILT_PHI = 17, MIRROR = 18;

    public Glitter {
        if (!(size > 0) || mirrors < 0 || mirrors > 1 || !(sharpness > 0)) {
            throw new IllegalArgumentException("glitter of " + size + ", " + mirrors + " mirrors, " + sharpness);
        }
    }

    /**
     * The flake at {@code p}, seen along {@code view}: its normal, unit and uniform over the sphere, or null if it is
     * matte. {@code p} and {@code view} in the field's own space.
     */
    public Vec3 flake(Vec3 p, Vec3 view, int dims) {
        int x = (int) Math.floor(p.x() / size), y = (int) Math.floor(p.y() / size), z = (int) Math.floor(p.z() / size);
        int bucket = hash.bucket(view.normalize(), dims);
        if (hash.u(x, y, z, bucket, MIRROR) >= mirrors) {
            return null;
        }
        double cz = 2 * hash.u(x, y, z, bucket, TILT_Z) - 1, phi = 2 * Math.PI * hash.u(x, y, z, bucket, TILT_PHI);
        double r = Math.sqrt(1 - cz * cz);
        return new Vec3(r * Math.cos(phi), r * Math.sin(phi), cz);
    }

    /**
     * How brightly the flake at {@code p} flashes toward the eye, {@code 0..1}: seen along {@code view}, lit from
     * {@code toLight} (the direction to the light). Zero for a matte flake or one facing away; one for a flake square
     * on the half-vector.
     */
    public double flash(Vec3 p, Vec3 view, Vec3 toLight, int dims) {
        Vec3 n = flake(p, view, dims);
        if (n == null) {
            return 0;
        }
        Vec3 half = toLight.normalize().minus(view.normalize()).normalize();
        double c = Math.abs(n.dot(half));   // a flake is a mirror on both faces
        return Math.exp(-(1 - c) * sharpness);
    }
}
