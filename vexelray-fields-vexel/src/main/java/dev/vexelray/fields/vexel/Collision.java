package dev.vexelray.fields.vexel;

/**
 * Where one ray was stopped, as a {@link VexelHash} decided it: what a renderer shades.
 *
 * @param t        the distance along the ray
 * @param point    the point at {@code t}
 * @param granular whether the granular share stopped it, anywhere along its path, rather than the cell's flat solid
 * @param smoke    whether, of the granular share, it was the medium (smoke) rather than grains of solid
 * @param normal   the outward unit normal of the face it entered by; zero for a granular stop, which has none
 * @param material the material hit
 * @param x        the cell, across
 * @param y        the cell, down
 * @param z        the cell, deep; zero in 2D
 */
public record Collision(double t, Vec3 point, boolean granular, boolean smoke, Vec3 normal, int material,
                        int x, int y, int z) {
}
