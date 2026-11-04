package dev.vexelray.fields.fractal;

/**
 * What covers the boxes: a separable footprint, so a box's coverage is the product of one term per axis. Either a
 * blurred sample, {@link Point}, or a raster cell's mean over every such sample in it, {@link Cell}.
 */
public sealed interface Footprint permits Footprint.Point, Footprint.Cell {

    /** How much of the footprint lands in {@code [a, b]} on {@code axis} (0, 1, 2 for x, y, z). */
    double weight(int axis, double a, double b);

    /** Where the footprint begins on {@code axis}; nothing below it is covered. */
    double lo(int axis);

    /** Where the footprint ends on {@code axis}. */
    double hi(int axis);

    /** How wide the footprint is, as a standard deviation: what decides how fine a structure it still resolves. */
    double width();

    /**
     * A Gaussian sample centred at {@code (x, y, z)}, of width {@code sigma}, truncated at {@link Gauss#REACH}σ.
     */
    record Point(double x, double y, double z, double sigma, Cdf cdf) implements Footprint {

        public Point {
            if (!(sigma > 0)) {
                throw new IllegalArgumentException("a footprint of width " + sigma);
            }
        }

        public double centre(int axis) {
            return axis == 0 ? x : axis == 1 ? y : z;
        }

        @Override
        public double weight(int axis, double a, double b) {
            return Gauss.mass(cdf, a, b, centre(axis), sigma);
        }

        @Override
        public double lo(int axis) {
            return centre(axis) - Gauss.REACH * sigma;
        }

        @Override
        public double hi(int axis) {
            return centre(axis) + Gauss.REACH * sigma;
        }

        @Override
        public double width() {
            return sigma;
        }
    }

    /**
     * The raster cell {@code [x0, x1] × [y0, y1] × [z0, z1]}: the mean, over every centre in it, of a Gaussian sample of
     * width {@code sigma}. The exact area under the curve, not a blur of samples.
     */
    record Cell(double x0, double y0, double z0, double x1, double y1, double z1, double sigma, Cdf cdf)
            implements Footprint {

        public Cell {
            if (!(sigma > 0) || !(x1 >= x0) || !(y1 >= y0) || !(z1 >= z0)) {
                throw new IllegalArgumentException("a raster cell of width " + sigma);
            }
        }

        private double from(int axis) {
            return axis == 0 ? x0 : axis == 1 ? y0 : z0;
        }

        private double to(int axis) {
            return axis == 0 ? x1 : axis == 1 ? y1 : z1;
        }

        @Override
        public double weight(int axis, double a, double b) {
            return Gauss.meanMass(cdf, a, b, from(axis), to(axis), sigma);
        }

        @Override
        public double lo(int axis) {
            return from(axis) - Gauss.REACH * sigma;
        }

        @Override
        public double hi(int axis) {
            return to(axis) + Gauss.REACH * sigma;
        }

        /** The Gaussian widened by the cell: a box of width {@code w} has variance {@code w² / 12}. */
        @Override
        public double width() {
            double w = Math.max(x1 - x0, Math.max(y1 - y0, z1 - z0));
            return Math.sqrt(sigma * sigma + w * w / 12);
        }
    }
}
