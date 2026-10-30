package dev.vexelray.fields.vexel;

/**
 * What a vexel field's smoke is baked from: material that is not a solid's surface but builds up over distance, a
 * plume, a cloud, a shell of dust. Its density is a collision rate, collisions per unit distance a ray travels through
 * it, so it is all granular: a ray is stopped somewhere along its path, with no normal.
 */
public interface Medium {

    /** Collisions per unit distance at {@code (x, y, z)}; zero where there is none. */
    double density(double x, double y, double z);

    /** The material a ray stopped in the medium has hit. */
    default int material() {
        return 0;
    }
}
