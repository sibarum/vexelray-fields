package dev.vexelray.fields.vexel;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

/**
 * A vexel field: a grid of cells that each keep, instead of one distance, the statistics of the surface inside them —
 * how much of the cell is solid, how much surface there is, and the moments of its normal, its offset and its
 * attributes ({@link Vexel}). From those, the probability that a ray is stopped in a cell, and what it stops on.
 *
 * <p>Why: a distance field says where one surface is, and averaging distances (a mip level, a texel seen from far
 * away) gives the distance to no surface at all. These statistics average exactly. Four flat patches facing four
 * ways become one cell of the same area whose mean normal is short: the field knows the cell is rough, and a ray
 * through it is stopped with the right probability from any direction. Coarser levels ({@link #coarser()}) are
 * made by combining moments, never by resampling.
 *
 * <p>The probability of a hit comes from the Crofton formula: a ray crossing a cell over length {@code l} meets on
 * average {@code N = S · l · E|n·ω|} pieces of surface, {@code S} the surface density and {@code E|n·ω|} the mean
 * cosine between the surface's normals and the ray, which the normal mixture of {@link Vexel} gives as
 * {@code |n̄·ω| + (1 - |n̄|)·c}, {@code c} the mean cosine of normals spread every way ({@code 1/2} in 3D, {@code 2/π}
 * in 2D). A {@link HitModel} says what is made of that: {@link HitModel#SHEET} takes the share facing one way as a
 * sheet where the offset moments put it, and counts only the rest by {@code N}; {@link HitModel#SCATTERED} counts it
 * all.
 *
 * <p>Experimental: the layout of a cell, the models and the bake are all expected to change. A 2D field is one cell
 * deep, its normals and rays in the xy plane.
 */
public final class VexelField {

    private static final int OCC = 0, AREA = 1, NX = 2, NY = 3, NZ = 4, H_MEAN = 5, H_VAR = 6, ATTR = 7;

    private final int dims;
    private final Vec3 origin;
    private final double cell;
    private final int nx, ny, nz;
    private final int attributes;
    private final int stride;
    private final float[] data;

    private VexelField(int dims, Vec3 origin, double cell, int nx, int ny, int nz, int attributes) {
        if (cell <= 0 || nx <= 0 || ny <= 0 || nz <= 0 || attributes < 0) {
            throw new IllegalArgumentException("a vexel field " + nx + " × " + ny + " × " + nz + " of " + cell);
        }
        this.dims = dims;
        this.origin = origin;
        this.cell = cell;
        this.nx = nx;
        this.ny = ny;
        this.nz = nz;
        this.attributes = attributes;
        this.stride = ATTR + 2 * attributes;
        this.data = new float[nx * ny * nz * stride];
    }

    /**
     * Bake {@code solid} into {@code nx × ny × nz} cells of side {@code cell} from {@code origin}, each sampled at
     * {@code samples} points a side. A sample within a sample's spacing of the surface counts toward its area by a tent
     * of that width (exact for a flat surface), and is moved onto the surface along the distance's gradient to read
     * the normal, the offset and the attributes there.
     */
    public static VexelField bake(Solid solid, Vec3 origin, double cell, int nx, int ny, int nz, int samples) {
        VexelField f = new VexelField(3, origin, cell, nx, ny, nz, solid.attributes());
        f.bakeAll(solid, samples);
        return f;
    }

    /**
     * A 2D field of {@code nx × ny} cells in the plane {@code z = 0}, from {@code (originX, originY)}: a
     * {@link Solid#prism} of a shape, say. Area is outline length, volume is area.
     */
    public static VexelField bake2d(Solid solid, double originX, double originY, double cell, int nx, int ny,
                                    int samples) {
        VexelField f = new VexelField(2, new Vec3(originX, originY, -cell / 2), cell, nx, ny, 1, solid.attributes());
        f.bakeAll(solid, samples);
        return f;
    }

    public int dimensions() {
        return dims;
    }

    public Vec3 origin() {
        return origin;
    }

    public double cellSize() {
        return cell;
    }

    public int sizeX() {
        return nx;
    }

    public int sizeY() {
        return ny;
    }

    public int sizeZ() {
        return nz;
    }

    public int attributeCount() {
        return attributes;
    }

    /** The centre of cell {@code (x, y, z)}; on the plane {@code z = 0} for a 2D field. */
    public Vec3 centre(int x, int y, int z) {
        return new Vec3(origin.x() + (x + 0.5) * cell, origin.y() + (y + 0.5) * cell, origin.z() + (z + 0.5) * cell);
    }

    public Vexel vexel(int x, int y, int z) {
        int i = index(x, y, z);
        float[] mean = new float[attributes];
        float[] var = new float[attributes];
        for (int k = 0; k < attributes; k++) {
            mean[k] = data[i + ATTR + k];
            var[k] = Math.max(0, data[i + ATTR + attributes + k] - mean[k] * mean[k]);
        }
        return new Vexel(data[i + OCC], data[i + AREA], new Vec3(data[i + NX], data[i + NY], data[i + NZ]),
                data[i + H_MEAN], data[i + H_VAR], mean, var);
    }

    /** The solid volume the field holds (area, in 2D). */
    public double volume() {
        double v = 0;
        for (int i = 0; i < data.length; i += stride) {
            v += data[i + OCC];
        }
        return v * Math.pow(cell, dims);
    }

    /** The surface area the field holds (outline length, in 2D). */
    public double area() {
        double a = 0;
        for (int i = 0; i < data.length; i += stride) {
            a += data[i + AREA];
        }
        return a * Math.pow(cell, dims);
    }

    private int index(int x, int y, int z) {
        return ((z * ny + y) * nx + x) * stride;
    }

    // ---- baking

    private record Sample(double w, Vec3 at, Vec3 normal) {
    }

    private void bakeAll(Solid solid, int samples) {
        if (samples <= 0) {
            throw new IllegalArgumentException("a bake of " + samples + " samples a side");
        }
        IntStream.range(0, ny * nz).parallel().forEach(row -> {
            int y = row % ny, z = row / ny;
            for (int x = 0; x < nx; x++) {
                bakeCell(solid, samples, x, y, z);
            }
        });
    }

    private void bakeCell(Solid solid, int s, int cx, int cy, int cz) {
        double h = cell / s;
        int sz = dims == 3 ? s : 1;
        double sampleVolume = Math.pow(h, dims);
        double cellVolume = Math.pow(cell, dims);
        Vec3 c = centre(cx, cy, cz);
        double inside = 0;
        List<Sample> band = new ArrayList<>();
        for (int k = 0; k < sz; k++) {
            double z = dims == 3 ? origin.z() + cz * cell + (k + 0.5) * h : 0;
            for (int j = 0; j < s; j++) {
                double y = origin.y() + cy * cell + (j + 0.5) * h;
                for (int i = 0; i < s; i++) {
                    double x = origin.x() + cx * cell + (i + 0.5) * h;
                    double d = solid.distance(x, y, z);
                    inside += Math.clamp(0.5 + d / h, 0, 1);   // the share of the sample's slab that is solid
                    if (Math.abs(d) < h) {
                        Vec3 n = gradient(solid, x, y, z, h * 0.25).times(-1).normalize();
                        if (n != Vec3.ZERO) {
                            double w = (1 - Math.abs(d) / h) / h * sampleVolume;
                            band.add(new Sample(w, new Vec3(x, y, z).plus(n.times(d)), n));
                        }
                    }
                }
            }
        }
        int i = index(cx, cy, cz);
        data[i + OCC] = (float) inside / (s * s * sz);
        double area = 0;
        Vec3 nsum = Vec3.ZERO;
        for (Sample b : band) {
            area += b.w;
            nsum = nsum.plus(b.normal.times(b.w));
        }
        if (area == 0) {
            return;
        }
        Vec3 mean = nsum.times(1 / area);
        Vec3 axis = mean.normalize();
        double hm = 0, h2 = 0;
        double[] am = new double[attributes], a2 = new double[attributes];
        for (Sample b : band) {
            double off = b.at.minus(c).dot(axis);
            hm += b.w * off;
            h2 += b.w * off * off;
            float[] attr = solid.attributes(b.at, b.normal);
            for (int k = 0; k < attributes; k++) {
                am[k] += b.w * attr[k];
                a2[k] += b.w * attr[k] * attr[k];
            }
        }
        hm /= area;
        data[i + AREA] = (float) (area / cellVolume);
        data[i + NX] = (float) mean.x();
        data[i + NY] = (float) mean.y();
        data[i + NZ] = (float) mean.z();
        data[i + H_MEAN] = (float) hm;
        data[i + H_VAR] = (float) Math.max(0, h2 / area - hm * hm);
        for (int k = 0; k < attributes; k++) {
            data[i + ATTR + k] = (float) (am[k] / area);
            data[i + ATTR + attributes + k] = (float) (a2[k] / area);
        }
    }

    private Vec3 gradient(Solid solid, double x, double y, double z, double e) {
        double gx = solid.distance(x + e, y, z) - solid.distance(x - e, y, z);
        double gy = solid.distance(x, y + e, z) - solid.distance(x, y - e, z);
        double gz = dims == 3 ? solid.distance(x, y, z + e) - solid.distance(x, y, z - e) : 0;
        return new Vec3(gx, gy, gz).times(1 / (2 * e));
    }

    // ---- filtering

    /**
     * The field at twice the cell size: each cell the moments of the two (2D) or eight (3D) beneath it, combined
     * exactly where they can be. Volume and area are kept; the mean normal of surfaces facing different ways shortens,
     * which is roughness appearing where detail is lost. Offsets are carried onto the new mean direction, a tilted
     * child's own extent adding to their variance.
     */
    public VexelField coarser() {
        int px = (nx + 1) / 2, py = (ny + 1) / 2, pz = dims == 3 ? (nz + 1) / 2 : 1;
        VexelField p = new VexelField(dims, origin, cell * 2, px, py, pz, attributes);
        int children = dims == 3 ? 8 : 4;
        for (int z = 0; z < pz; z++) {
            for (int y = 0; y < py; y++) {
                for (int x = 0; x < px; x++) {
                    p.combine(this, x, y, z, children);
                }
            }
        }
        return p;
    }

    private void combine(VexelField f, int x, int y, int z, int children) {
        int o = index(x, y, z);
        List<Integer> kids = new ArrayList<>(children);
        for (int dz = 0; dz < (dims == 3 ? 2 : 1); dz++) {
            for (int dy = 0; dy < 2; dy++) {
                for (int dx = 0; dx < 2; dx++) {
                    int cx = 2 * x + dx, cy = 2 * y + dy, cz = dims == 3 ? 2 * z + dz : 0;
                    if (cx < f.nx && cy < f.ny && cz < f.nz) {
                        kids.add((cz * f.ny + cy) * f.nx + cx);
                    }
                }
            }
        }
        double occ = 0, area = 0;
        Vec3 nsum = Vec3.ZERO;
        for (int k : kids) {
            int i = k * f.stride;
            occ += f.data[i + OCC];
            double a = f.data[i + AREA];
            area += a;
            nsum = nsum.plus(new Vec3(f.data[i + NX], f.data[i + NY], f.data[i + NZ]).times(a));
        }
        data[o + OCC] = (float) (occ / children);
        if (area == 0) {
            return;
        }
        Vec3 mean = nsum.times(1 / area);
        Vec3 axis = mean.normalize();
        Vec3 c = centre(x, y, z);
        double hm = 0, h2 = 0;
        double[] am = new double[attributes], a2 = new double[attributes];
        for (int k : kids) {
            int i = k * f.stride;
            double a = f.data[i + AREA];
            if (a == 0) {
                continue;
            }
            int cx = k % f.nx, cy = (k / f.nx) % f.ny, cz = k / (f.nx * f.ny);
            Vec3 cn = new Vec3(f.data[i + NX], f.data[i + NY], f.data[i + NZ]).normalize();
            double cos = cn == Vec3.ZERO ? 0 : cn.dot(axis);
            double shifted = f.centre(cx, cy, cz).minus(c).dot(axis) + f.data[i + H_MEAN] * cos;
            double tilt = (1 - cos * cos) * f.cell * f.cell / 12;
            hm += a * shifted;
            h2 += a * (shifted * shifted + f.data[i + H_VAR] * cos * cos + tilt);
            for (int q = 0; q < attributes; q++) {
                am[q] += a * f.data[i + ATTR + q];
                a2[q] += a * f.data[i + ATTR + attributes + q];
            }
        }
        hm /= area;
        data[o + AREA] = (float) (area / children);
        data[o + NX] = (float) mean.x();
        data[o + NY] = (float) mean.y();
        data[o + NZ] = (float) mean.z();
        data[o + H_MEAN] = (float) hm;
        data[o + H_VAR] = (float) Math.max(0, h2 / area - hm * hm);
        for (int q = 0; q < attributes; q++) {
            data[o + ATTR + q] = (float) (am[q] / area);
            data[o + ATTR + attributes + q] = (float) (a2[q] / area);
        }
    }

    // ---- rays

    /** {@link #trace(Vec3, Vec3, double, HitModel)} with {@link HitModel#SHEET}, as far as the field goes. */
    public RayHits trace(Vec3 from, Vec3 direction) {
        return trace(from, direction, Double.POSITIVE_INFINITY, HitModel.SHEET);
    }

    /** A ray in a 2D field's plane. */
    public RayHits trace2d(double x, double y, double dx, double dy, HitModel model) {
        return trace(new Vec3(x, y, 0), new Vec3(dx, dy, 0), Double.POSITIVE_INFINITY, model);
    }

    /**
     * Walk the ray from {@code from} along {@code direction} up to {@code tMax}, cell by cell, and give the chance of
     * its stopping in each cell it crosses that has surface in it. Distances along the ray are in world units.
     */
    public RayHits trace(Vec3 from, Vec3 direction, double tMax, HitModel model) {
        Vec3 d = direction.normalize();
        if (d == Vec3.ZERO) {
            throw new IllegalArgumentException("a ray with no direction");
        }
        double[] o = {from.x(), from.y(), from.z()};
        double[] dir = {d.x(), d.y(), d.z()};
        double[] lo = {origin.x(), origin.y(), origin.z()};
        int[] n = {nx, ny, nz};
        double t0 = 0, t1 = tMax;
        for (int a = 0; a < 3; a++) {
            double hi = lo[a] + n[a] * cell;
            if (dir[a] == 0) {
                if (o[a] < lo[a] || o[a] >= hi) {
                    return new RayHits(List.of());
                }
            } else {
                double ta = (lo[a] - o[a]) / dir[a], tb = (hi - o[a]) / dir[a];
                t0 = Math.max(t0, Math.min(ta, tb));
                t1 = Math.min(t1, Math.max(ta, tb));
            }
        }
        if (t0 >= t1) {
            return new RayHits(List.of());
        }
        int[] idx = new int[3];
        int[] step = new int[3];
        double[] next = new double[3];
        double[] delta = new double[3];
        for (int a = 0; a < 3; a++) {
            double p = o[a] + dir[a] * (t0 + 1e-9 * cell);
            idx[a] = Math.clamp((long) Math.floor((p - lo[a]) / cell), 0, n[a] - 1);
            if (dir[a] > 0) {
                step[a] = 1;
                next[a] = (lo[a] + (idx[a] + 1) * cell - o[a]) / dir[a];
                delta[a] = cell / dir[a];
            } else if (dir[a] < 0) {
                step[a] = -1;
                next[a] = (lo[a] + idx[a] * cell - o[a]) / dir[a];
                delta[a] = -cell / dir[a];
            } else {
                next[a] = Double.POSITIVE_INFINITY;
                delta[a] = Double.POSITIVE_INFINITY;
            }
        }
        double iso = dims == 3 ? 0.5 : 2 / Math.PI;
        double through = 1;
        double t = t0;
        List<RayHits.Hit> hits = new ArrayList<>();
        while (t < t1 && through > 1e-6) {
            int a = next[0] < next[1] ? (next[0] < next[2] ? 0 : 2) : (next[1] < next[2] ? 1 : 2);
            double exit = Math.min(next[a], t1);
            int i = index(idx[0], idx[1], idx[2]);
            double s = data[i + AREA];
            if (s > 0 && exit > t) {
                Vec3 mean = new Vec3(data[i + NX], data[i + NY], data[i + NZ]);
                Vec3 axis = mean.normalize();
                Vec3 c = centre(idx[0], idx[1], idx[2]);
                double r = mean.length();
                double p;
                if (model == HitModel.SCATTERED) {
                    p = 1 - Math.exp(-s * (exit - t) * (Math.abs(mean.dot(d)) + (1 - r) * iso));
                } else {
                    double in = from.plus(d.times(t)).minus(c).dot(axis);
                    double out = from.plus(d.times(exit)).minus(c).dot(axis);
                    double sheet = within(data[i + H_MEAN], Math.sqrt(data[i + H_VAR]), Math.min(in, out),
                            Math.max(in, out));
                    double rest = Math.min(1, s * (exit - t) * (1 - r) * iso);
                    p = 1 - (1 - r * sheet) * (1 - rest);
                }
                if (p > 0) {
                    double along = axis.dot(d);
                    double th = Math.abs(along) > 1e-9
                            ? (data[i + H_MEAN] - axis.dot(from.minus(c))) / along
                            : 0.5 * (t + exit);
                    th = Math.clamp(th, t, exit);
                    float[] attr = new float[attributes];
                    System.arraycopy(data, i + ATTR, attr, 0, attributes);
                    hits.add(new RayHits.Hit(through * p, th, from.plus(d.times(th)), axis, 1 - r, attr));
                    through *= 1 - p;
                }
            }
            t = exit;
            if (next[a] >= t1) {
                break;
            }
            idx[a] += step[a];
            if (idx[a] < 0 || idx[a] >= n[a]) {
                break;
            }
            next[a] += delta[a];
        }
        return new RayHits(hits);
    }

    /**
     * The chance that an offset distributed normally about {@code mean}, by {@code sd}, falls in {@code [lo, hi]}; a
     * step for an offset known exactly.
     */
    private double within(double mean, double sd, double lo, double hi) {
        if (sd < 1e-6 * cell) {
            return mean >= lo - 1e-9 * cell && mean <= hi + 1e-9 * cell ? 1 : 0;
        }
        return Math.max(0, phi((hi - mean) / sd) - phi((lo - mean) / sd));
    }

    /** The standard normal distribution function, by Abramowitz and Stegun 7.1.26: within 1.5e-7. */
    private static double phi(double x) {
        double z = Math.abs(x) / Math.sqrt(2);
        double k = 1 / (1 + 0.3275911 * z);
        double erf = 1 - k * (0.254829592 + k * (-0.284496736 + k * (1.421413741 + k * (-1.453152027
                + k * 1.061405429)))) * Math.exp(-z * z);
        return 0.5 * (1 + (x < 0 ? -erf : erf));
    }
}
