package dev.vexelray.fields.fractal;

/**
 * One level of a {@link Fractal}: how a box at the level above becomes its {@code n³} children, as a weight per child
 * at local digits {@code (i, j, l)}.
 *
 * <p>A modulus rule has weights of {@code 0} and {@code 1}: a child is kept or dropped. A small texture is a level too,
 * its texels' probabilities the weights: each scales its child box's coverage. A {@linkplain #hashed hashed} level
 * draws instead: a child is kept outright with its weight as the chance, from a hash of its parent's hash and its own
 * digits, so the draw holds still under zoom and from frame to frame. Either way the level's expected fill is the mean
 * of its weights, which is all the renderer needs from the levels it does not resolve.
 *
 * <p>Levels are compared by identity: a {@link Fractal} that repeats one level shares one baked table for it.
 */
public final class Level {

    /** Which children a modulus rule keeps, from their local digits along x, y and z. */
    @FunctionalInterface
    public interface Rule {
        boolean keep(int i, int j, int l);
    }

    private final int n;
    private final double[] weights;
    private final boolean hashed;
    private final double fill;

    private Level(int n, double[] weights, boolean hashed) {
        if (n < 2) {
            throw new IllegalArgumentException("a level of base " + n);
        }
        if (weights.length != n * n * n) {
            throw new IllegalArgumentException(weights.length + " weights for base " + n);
        }
        double sum = 0;
        for (double w : weights) {
            if (!(w >= 0 && w <= 1)) {
                throw new IllegalArgumentException("a weight of " + w);
            }
            sum += w;
        }
        this.n = n;
        this.weights = weights;
        this.hashed = hashed;
        this.fill = sum / weights.length;
    }

    /** The children of base {@code n} that {@code rule} keeps. */
    public static Level modulus(int n, Rule rule) {
        double[] w = new double[n * n * n];
        for (int l = 0; l < n; l++) {
            for (int j = 0; j < n; j++) {
                for (int i = 0; i < n; i++) {
                    w[(l * n + j) * n + i] = rule.keep(i, j, l) ? 1 : 0;
                }
            }
        }
        return new Level(n, w, false);
    }

    /** The Menger sponge's rule: a child is dropped where two or more of its digits are 1. Keeps 20 of 27. */
    public static Level menger() {
        return modulus(3, (i, j, l) -> (i == 1 ? 1 : 0) + (j == 1 ? 1 : 0) + (l == 1 ? 1 : 0) < 2);
    }

    /** Cantor dust: a child is kept where none of its digits is 1. Keeps 8 of 27. */
    public static Level cantorDust() {
        return modulus(3, (i, j, l) -> i != 1 && j != 1 && l != 1);
    }

    /** Every child kept: a solid box. */
    public static Level solid(int n) {
        return modulus(n, (i, j, l) -> true);
    }

    /** A texture of {@code n³} probabilities, x fastest then y then z, each scaling its child box. */
    public static Level of(int n, double... weights) {
        return new Level(n, weights.clone(), false);
    }

    /** This level drawn rather than weighted: each child kept outright with chance its weight times {@code p}. */
    public Level randomized(double p) {
        if (!(p >= 0 && p <= 1)) {
            throw new IllegalArgumentException("a chance of " + p);
        }
        double[] w = weights.clone();
        for (int k = 0; k < w.length; k++) {
            w[k] *= p;
        }
        return new Level(n, w, true);
    }

    public int base() {
        return n;
    }

    public boolean hashed() {
        return hashed;
    }

    /** The weight of the child at local digits {@code (i, j, l)}: its chance of being kept, if hashed. */
    public double weight(int i, int j, int l) {
        return weights[(l * n + j) * n + i];
    }

    /** The expected fill fraction: the mean weight. */
    public double fill() {
        return fill;
    }
}
