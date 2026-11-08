package dev.vexelray.fields.convert;

/**
 * Per texel, the probability that a Gaussian footprint centred there lands inside the shape, and the footprint's
 * {@code σ} there, in texels. Near a straight edge at signed distance {@code d} (positive inside) that probability is
 * {@code Φ(d / σ)}; a field of them is what a distance becomes when the question is how likely a hit is, not where the
 * edge is.
 *
 * @param p     {@code p[y * width + x]} in {@code [0, 1]}; never changed after the field is made
 * @param sigma {@code sigma[y * width + x]}, positive, in texels; never changed after the field is made
 */
public record ProbabilityField(int width, int height, float[] p, float[] sigma) {

    public ProbabilityField {
        if (width <= 0 || height <= 0 || p.length != width * height || sigma.length != p.length) {
            throw new IllegalArgumentException("a probability field " + width + " × " + height + " of " + p.length
                    + " and " + sigma.length);
        }
    }
}
