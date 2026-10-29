package dev.vexelray.fields.vexel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

/**
 * A vexel field: a grid of cells that each say, instead of one distance, how likely a ray is to collide in them and
 * with what. Per cell ({@link Vexel}):
 *
 * <ul>
 *   <li>a probability of collision per unit distance travelled, for the cell's granular share;</li>
 *   <li>the probability of each material, given a collision;</li>
 *   <li>a granularity: {@code 1} is smoke, material building up over distance with no particular normal; {@code 0}
 *       is a surface, where the collision happens at one point with one normal. Between, the cell is both: its
 *       granular share collides at a rate, and its flat share is a plane that stops a ray crossing it with a
 *       coverage.</li>
 * </ul>
 *
 * <p>The two shares compete: along a ray through a cell, the granular share's collision falls where an exponential
 * of its rate puts it, the plane's where the ray crosses it, and whichever comes first is the collision. The rate
 * is a rate per distance because granular material is met more the further a ray goes through it; the flat share
 * is a chance per crossing because a surface is met once by any ray that crosses it, at any angle.
 *
 * <p>Underneath, a cell keeps the moments of what it holds — how much of it is solid, how much surface, the
 * area-weighted mean of the surface's normals, where it sits along that mean and how far it strays, the share of
 * each material — and the above is derived from them. Moments average exactly, which distances do not, so a coarser
 * level ({@link #coarser()}) is made by combining them, never by resampling: four flat patches facing four ways
 * become one cell of the same area whose mean normal is short, and so a granular cell. Detail lost to distance
 * turns into granularity, which is what it looks like from there.
 *
 * <p>Two ways to ask a ray: {@link #trace} gives the expected outcome, a probability per cell; {@link #collide}
 * gives one outcome, decided by a {@link VexelHash} of the cell and the ray's quantized angle so that it holds still
 * from frame to frame.
 *
 * <p>Experimental: the layout of a cell, the derivation and the bake are all expected to change. A 2D field is one
 * cell deep, its normals and rays in the xy plane.
 */
public final class VexelField {

    /** The moments: what is kept, baked and combined. */
    private static final int OCC = 0, AREA = 1, NX = 2, NY = 3, NZ = 4, H_MEAN = 5, H_VAR = 6,
            SX = 7, SY = 8, SZ = 9, S_VAR = 10, FX = 11, FY = 12, FZ = 13, F_COV = 14, MAT = 20;
    /** The order a covariance's six terms are kept in, after {@link #F_COV}. */
    private static final int[][] COV = {{0, 3, 4}, {3, 1, 5}, {4, 5, 2}};
    /** What is derived from them, per cell. */
    private static final int RATE = 0, COVER = 1, GRAN = 2, PX = 3, PY = 4, PZ = 5, PH = 6, GX = 7, GY = 8, GZ = 9,
            G_VAR = 10, BALL = 11, FILL = 12, BX = 13, BY = 14, BZ = 15, UX = 16, UY = 17, UZ = 18, W1 = 19, W2 = 20,
            DERIVED = 21;

    private final int dims;
    private final Vec3 origin;
    private final double cell;
    private final int nx, ny, nz;
    private final int materials;
    private final int attributes;
    private final int attr;
    private final int stride;
    private final float[] data;
    private final float[] derived;

    private VexelField(int dims, Vec3 origin, double cell, int nx, int ny, int nz, int materials, int attributes) {
        if (cell <= 0 || nx <= 0 || ny <= 0 || nz <= 0 || materials <= 0 || attributes < 0) {
            throw new IllegalArgumentException("a vexel field " + nx + " × " + ny + " × " + nz + " of " + cell
                    + " with " + materials + " materials");
        }
        this.dims = dims;
        this.origin = origin;
        this.cell = cell;
        this.nx = nx;
        this.ny = ny;
        this.nz = nz;
        this.materials = materials;
        this.attributes = attributes;
        this.attr = MAT + materials;
        this.stride = attr + 2 * attributes;
        this.data = new float[nx * ny * nz * stride];
        this.derived = new float[nx * ny * nz * DERIVED];
    }

    /**
     * Bake {@code solid} into {@code nx × ny × nz} cells of side {@code cell} from {@code origin}, each sampled at
     * {@code samples} points a side. A sample within a sample's spacing of the surface is moved onto it along the
     * distance's gradient and counts toward the area of the cell its foot falls in, by a tent of that width (exact
     * for a flat surface): each piece of surface belongs to one cell, even where it lies on a face between two.
     */
    public static VexelField bake(Solid solid, Vec3 origin, double cell, int nx, int ny, int nz, int samples) {
        VexelField f = new VexelField(3, origin, cell, nx, ny, nz, solid.materials(), solid.attributes());
        f.bakeAll(solid, samples);
        return f;
    }

    /**
     * A 2D field of {@code nx × ny} cells in the plane {@code z = 0}, from {@code (originX, originY)}: a
     * {@link Solid#prism} of a shape, say. Area is outline length, volume is area.
     */
    public static VexelField bake2d(Solid solid, double originX, double originY, double cell, int nx, int ny,
                                    int samples) {
        VexelField f = new VexelField(2, new Vec3(originX, originY, -cell / 2), cell, nx, ny, 1, solid.materials(),
                solid.attributes());
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

    public int materialCount() {
        return materials;
    }

    public int attributeCount() {
        return attributes;
    }

    /** The centre of cell {@code (x, y, z)}; on the plane {@code z = 0} for a 2D field. */
    public Vec3 centre(int x, int y, int z) {
        return new Vec3(origin.x() + (x + 0.5) * cell, origin.y() + (y + 0.5) * cell, origin.z() + (z + 0.5) * cell);
    }

    public Vexel vexel(int x, int y, int z) {
        int c = cellIndex(x, y, z);
        int i = c * stride, k = c * DERIVED;
        float[] mean = new float[attributes];
        float[] var = new float[attributes];
        for (int a = 0; a < attributes; a++) {
            mean[a] = data[i + attr + a];
            var[a] = Math.max(0, data[i + attr + attributes + a] - mean[a] * mean[a]);
        }
        Vexel.Footprint footprint = derived[k + W1] < 0 ? null : new Vexel.Footprint(
                new Vec3(derived[k + BX], derived[k + BY], derived[k + BZ]),
                new Vec3(derived[k + UX], derived[k + UY], derived[k + UZ]), derived[k + W1], derived[k + W2]);
        return new Vexel(derived[k + GRAN], derived[k + RATE], derived[k + COVER],
                new Vec3(derived[k + PX], derived[k + PY], derived[k + PZ]), derived[k + PH], footprint,
                Arrays.copyOfRange(data, i + MAT, i + MAT + materials),
                data[i + OCC], new Vec3(data[i + SX], data[i + SY], data[i + SZ]), data[i + S_VAR], derived[k + FILL],
                data[i + AREA], new Vec3(data[i + NX], data[i + NY], data[i + NZ]), data[i + H_VAR], mean, var);
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

    private int cellIndex(int x, int y, int z) {
        return (z * ny + y) * nx + x;
    }

    /** The world position of cell {@code c}'s solid centroid. */
    private Vec3 solidCentroid(int c) {
        int i = c * stride;
        return centre(c % nx, (c / nx) % ny, c / (nx * ny))
                .plus(new Vec3(data[i + SX], data[i + SY], data[i + SZ]));
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
                derive(cellIndex(x, y, z));
            }
        });
    }

    /**
     * One cell, sampled on a grid that runs a sample past it each way: the samples inside measure how much of it is
     * solid, and every sample near the surface, inside or just outside, offers the cell the surface point at its foot.
     */
    private void bakeCell(Solid solid, int s, int cx, int cy, int cz) {
        double h = cell / s;
        double sampleVolume = Math.pow(h, dims);
        double x0 = origin.x() + cx * cell, y0 = origin.y() + cy * cell, z0 = origin.z() + cz * cell;
        int kFrom = dims == 3 ? -1 : 0, kTo = dims == 3 ? s : 0;
        double inside = 0;
        Vec3 solidSum = Vec3.ZERO;
        double solidSquares = 0;
        Vec3 c = centre(cx, cy, cz);
        List<Sample> band = new ArrayList<>();
        for (int k = kFrom; k <= kTo; k++) {
            double z = dims == 3 ? z0 + (k + 0.5) * h : 0;
            for (int j = -1; j <= s; j++) {
                double y = y0 + (j + 0.5) * h;
                for (int i = -1; i <= s; i++) {
                    double x = x0 + (i + 0.5) * h;
                    double d = solid.distance(x, y, z);
                    Vec3 n = Math.abs(d) < h ? gradient(solid, x, y, z, h * 0.25).times(-1).normalize() : Vec3.ZERO;
                    boolean own = i >= 0 && i < s && j >= 0 && j < s && (dims == 2 || (k >= 0 && k < s));
                    if (own) {
                        double w = Math.clamp(0.5 + d / h, 0, 1);   // the share of the sample's slab that is solid
                        if (w > 0) {
                            // a slab cut by the surface: its solid part sits on the inside, (1 - w) h / 2 off centre,
                            // and is w h deep along the normal, h across it; exact for a flat surface
                            Vec3 r = new Vec3(x, y, z).minus(c).minus(n.times((1 - w) * h / 2));
                            inside += w;
                            solidSum = solidSum.plus(r.times(w));
                            solidSquares += w * (r.dot(r) + ((dims - 1) + w * w) * h * h / 12);
                        }
                    }
                    if (Math.abs(d) < h) {
                        Vec3 foot = new Vec3(x, y, z).plus(n.times(d));
                        if (n != Vec3.ZERO && within(foot.x(), x0) && within(foot.y(), y0)
                                && (dims == 2 || within(foot.z(), z0))) {
                            band.add(new Sample((1 - Math.abs(d) / h) / h * sampleVolume, foot, n));
                        }
                    }
                }
            }
        }
        int i = cellIndex(cx, cy, cz) * stride;
        data[i + OCC] = (float) (inside / (s * s * (dims == 3 ? s : 1)));
        if (inside > 0) {
            Vec3 centroid = solidSum.times(1 / inside);
            storeSolid(i, centroid, solidSquares / inside - centroid.dot(centroid));
        }
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
        double[] mat = new double[materials];
        double[] am = new double[attributes], a2 = new double[attributes];
        double[] fs = new double[3], ff = new double[6];
        for (Sample b : band) {
            double off = b.at.minus(c).dot(axis);
            hm += b.w * off;
            h2 += b.w * off * off;
            double[] f = xyz(b.at.minus(c));
            for (int p = 0; p < 3; p++) {
                fs[p] += b.w * f[p];
                for (int q = p; q < 3; q++) {
                    ff[COV[p][q]] += b.w * f[p] * f[q];
                }
            }
            mat[Math.clamp(solid.material(b.at, b.normal), 0, materials - 1)] += b.w;
            float[] values = solid.attributes(b.at, b.normal);
            for (int a = 0; a < attributes; a++) {
                am[a] += b.w * values[a];
                a2[a] += b.w * values[a] * values[a];
            }
        }
        hm /= area;
        store(i, area / Math.pow(cell, dims), mean, hm, h2 / area - hm * hm, mat, am, a2, area);
        // where the surface is: each foot stands for a patch h across, whose own spread, h²/12 an axis, is added
        double[] m = {fs[0] / area, fs[1] / area, fs[2] / area};
        double[] cov = new double[6];
        for (int p = 0; p < 3; p++) {
            for (int q = p; q < 3; q++) {
                cov[COV[p][q]] = ff[COV[p][q]] / area - m[p] * m[q] + (p == q && (p < 2 || dims == 3) ? h * h / 12 : 0);
            }
        }
        storeSurface(i, m, cov);
    }

    private static double[] xyz(Vec3 v) {
        return new double[]{v.x(), v.y(), v.z()};
    }

    /** Write where a cell's surface is: its area-weighted centroid from the cell's centre, and its covariance. */
    private void storeSurface(int i, double[] centroid, double[] cov) {
        data[i + FX] = (float) centroid[0];
        data[i + FY] = (float) centroid[1];
        data[i + FZ] = (float) centroid[2];
        for (int p = 0; p < 6; p++) {
            data[i + F_COV + p] = (float) cov[p];
        }
    }

    private double cov(int i, int p, int q) {
        return data[i + F_COV + COV[p][q]];
    }

    /** Whether {@code v} is in {@code [from, from + cell)}. */
    private boolean within(double v, double from) {
        return v >= from && v < from + cell;
    }

    private Vec3 gradient(Solid solid, double x, double y, double z, double e) {
        double gx = solid.distance(x + e, y, z) - solid.distance(x - e, y, z);
        double gy = solid.distance(x, y + e, z) - solid.distance(x, y - e, z);
        double gz = dims == 3 ? solid.distance(x, y, z + e) - solid.distance(x, y, z - e) : 0;
        return new Vec3(gx, gy, gz).times(1 / (2 * e));
    }

    /** Write where a cell's solid is: its centroid from the cell's centre, and its mean squared distance from that. */
    private void storeSolid(int i, Vec3 centroid, double spread) {
        data[i + SX] = (float) centroid.x();
        data[i + SY] = (float) centroid.y();
        data[i + SZ] = (float) centroid.z();
        data[i + S_VAR] = (float) Math.max(0, spread);
    }

    /** Write a cell's surface moments, the weighted sums divided by {@code weight}. */
    private void store(int i, double density, Vec3 mean, double hm, double hv, double[] mat, double[] am,
                       double[] a2, double weight) {
        data[i + AREA] = (float) density;
        data[i + NX] = (float) mean.x();
        data[i + NY] = (float) mean.y();
        data[i + NZ] = (float) mean.z();
        data[i + H_MEAN] = (float) hm;
        data[i + H_VAR] = (float) Math.max(0, hv);
        for (int m = 0; m < materials; m++) {
            data[i + MAT + m] = (float) (mat[m] / weight);
        }
        for (int a = 0; a < attributes; a++) {
            data[i + attr + a] = (float) (am[a] / weight);
            data[i + attr + attributes + a] = (float) (a2[a] / weight);
        }
    }

    // ---- what a renderer reads

    /**
     * A cell's granularity, rate, coverage and plane, from its moments. The flat share is the coherence of its
     * surface: the mean normal's length, less as the offsets spread, reaching nothing when they are as spread as a
     * uniform scatter across the cell. Its area over the area of the mean plane's slice of the cell is the plane's
     * coverage.
     *
     * <p>The granular share is taken as grains of solid scattered as a Boolean model: a ray coming from the empty
     * space between them enters one at a rate of {@code S/4 ÷ (1 - occupancy)} per unit distance in 3D
     * ({@code S/π ÷ (1 - occupancy)} in 2D), {@code S} the granular surface density. A quarter of the Crofton
     * crossings, because half of all crossings are exits and the rest are spread over every angle; divided by the free
     * share because a ray is only ever in the free space between grains until it hits. So a cell mostly solid stops a
     * ray almost at once. That rate is the cell's average: where in the cell the grains are is the solid's centroid
     * and spread, and {@link #depth} spreads the rate as a Gaussian there.
     *
     * <p>And the solid itself, as a ball at its centroid whose mean squared radius is its spread, a share of which,
     * its fill, is solid: one for a solid ball, less for a solid spread thinner, as foliage is. See {@link #through}.
     */
    private void derive(int c) {
        int i = c * stride, k = c * DERIVED;
        Arrays.fill(derived, k, k + DERIVED, 0f);
        derived[k + W1] = -1;   // no footprint: the coverage, if any, is the slice's
        double s = data[i + AREA];
        if (s == 0) {
            return;
        }
        Vec3 mean = new Vec3(data[i + NX], data[i + NY], data[i + NZ]);
        Vec3 axis = mean.normalize();
        double spread = Math.sqrt(data[i + H_VAR]) / (cell / Math.sqrt(12));
        double coherence = mean.length() * Math.max(0, 1 - spread);
        double slice = axis == Vec3.ZERO ? 0 : sliceArea(axis, data[i + H_MEAN]);
        double coverage = 0;
        if (slice > 1e-9 * Math.pow(cell, dims - 1)) {
            coverage = Math.min(1, coherence * s * Math.pow(cell, dims) / slice);
        } else {
            coherence = 0;   // a mean plane that misses the cell is no plane
        }
        double granularity = 1 - coherence;
        double free = Math.max(1e-3, 1 - data[i + OCC]);
        derived[k + RATE] = (float) (granularity * s * (dims == 3 ? 0.25 : 1 / Math.PI) / free);
        derived[k + GX] = data[i + SX];
        derived[k + GY] = data[i + SY];
        derived[k + GZ] = data[i + SZ];
        derived[k + G_VAR] = data[i + OCC] > 0 ? data[i + S_VAR] / dims : 0;
        if (data[i + OCC] > 0) {
            // the solid as a ball of its own spread (a uniform ball's mean squared radius is 3/5 R², a disc's 1/2 R²)
            // and how full of solid that ball would be
            double ball = Math.sqrt(data[i + S_VAR] * (dims == 3 ? 5 / 3.0 : 2));
            double ballVolume = dims == 3 ? 4 / 3.0 * Math.PI * ball * ball * ball : Math.PI * ball * ball;
            derived[k + BALL] = (float) ball;
            derived[k + FILL] = ballVolume > 0
                    ? (float) Math.min(1, data[i + OCC] * Math.pow(cell, dims) / ballVolume) : 0;
        }
        if (coverage > 0) {
            footprint(i, k, axis, coherence * s * Math.pow(cell, dims), slice);
            coverage = derived[k + COVER];
        }
        derived[k + COVER] = (float) coverage;
        derived[k + GRAN] = (float) granularity;
        derived[k + PX] = (float) axis.x();
        derived[k + PY] = (float) axis.y();
        derived[k + PZ] = (float) axis.z();
        derived[k + PH] = data[i + H_MEAN];
    }

    /**
     * How much of its slice a box must already hold before it is taken as the whole slice. Below, the box keeps a
     * curved cap's plane from stopping rays past the cap's edge; above, a surface that spans its cell keeps every
     * corner of its slice, so neighbouring cells leave no crack between them along their shared walls. Tuned on a
     * sphere: 0.8 let the leak back at cells of 2, and 0.9 opened cracks at cells of 1.
     */
    private static final double SPANS = 0.85;

    /**
     * Where on its plane a cell's surface lies, from the surface's centroid and covariance: a box (a segment, in 2D)
     * centred on the centroid's foot on the plane, along the principal axes of the covariance projected into the
     * plane, each half-width {@code √(3 · variance)}, the box a uniform rectangle's moments give back exactly. Grown
     * until, within the plane's slice of the cell, it holds the flat area; taken as the whole slice once it holds
     * {@link #SPANS} of it. The coverage within it is the flat area over the area it holds, so the area covered is
     * kept. Writes the box and the coverage into {@code derived}; no box ({@code W1} negative) means the slice.
     */
    private void footprint(int i, int k, Vec3 n, double flatArea, double slice) {
        Vec3 m = new Vec3(data[i + FX], data[i + FY], data[i + FZ]);
        Vec3 b = m.minus(n.times(n.dot(m) - data[i + H_MEAN]));
        Vec3 t1 = dims == 2 ? new Vec3(-n.y(), n.x(), 0)
                : (Math.abs(n.x()) < 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0)).cross(n).normalize();
        Vec3 t2 = n.cross(t1);
        double a11 = quad(i, t1, t1);
        Vec3 u = t1;
        double w1, w2 = 0;
        if (dims == 2) {
            w1 = Math.sqrt(3 * Math.max(0, a11));
        } else {
            double a12 = quad(i, t1, t2), a22 = quad(i, t2, t2);
            double mid = (a11 + a22) / 2, diff = Math.hypot((a11 - a22) / 2, a12);
            double l1 = mid + diff, l2 = mid - diff;
            if (Math.abs(a12) > 1e-12 * (Math.abs(a11) + Math.abs(a22))) {
                u = t1.times(a12).plus(t2.times(l1 - a11)).normalize();
            } else if (a22 > a11) {
                u = t2;
            }
            w1 = Math.sqrt(3 * Math.max(0, l1));
            w2 = Math.sqrt(3 * Math.max(0, l2));
        }
        // the slice, in the box's own frame: along u, and across it
        Vec3 v = n.cross(u);
        List<Vec3> points = slice(n, data[i + H_MEAN]);
        double[][] poly = new double[points.size()][];
        double reach = 0;
        w1 = Math.max(w1, 1e-3 * cell);
        w2 = Math.max(w2, 1e-3 * cell);
        for (int j = 0; j < poly.length; j++) {
            Vec3 p = points.get(j).minus(b);
            poly[j] = new double[]{p.dot(u), p.dot(v)};
            reach = Math.max(reach, Math.max(Math.abs(poly[j][0]) / w1, dims == 3 ? Math.abs(poly[j][1]) / w2 : 0));
        }
        // grow the box until, within the slice, it holds the flat area
        double want = Math.min(flatArea, slice) * (1 - 1e-6);
        double grow = 1;
        if (held(poly, w1, w2) < want) {
            double lo = 1, hi = Math.max(1, reach);
            for (int j = 0; j < 40; j++) {
                double mid = 0.5 * (lo + hi);
                if (held(poly, w1 * mid, w2 * mid) < want) {
                    lo = mid;
                } else {
                    hi = mid;
                }
            }
            grow = hi;
        }
        w1 *= grow;
        w2 *= grow;
        double inside = held(poly, w1, w2);
        if (inside <= 1e-9 * Math.pow(cell, dims - 1) || inside >= SPANS * slice) {
            derived[k + COVER] = (float) Math.min(1, flatArea / slice);   // the whole slice
            return;
        }
        derived[k + BX] = (float) b.x();
        derived[k + BY] = (float) b.y();
        derived[k + BZ] = (float) b.z();
        derived[k + UX] = (float) u.x();
        derived[k + UY] = (float) u.y();
        derived[k + UZ] = (float) u.z();
        derived[k + W1] = (float) w1;
        derived[k + W2] = (float) w2;
        derived[k + COVER] = (float) Math.min(1, flatArea / inside);
    }

    /** How much of the slice {@code poly} (the box's frame) lies in the box of half-widths {@code w1}, {@code w2}. */
    private double held(double[][] poly, double w1, double w2) {
        if (dims == 2) {
            if (poly.length < 2) {
                return 0;
            }
            double a = Math.min(poly[0][0], poly[1][0]), b = Math.max(poly[0][0], poly[1][0]);
            return Math.max(0, Math.min(b, w1) - Math.max(a, -w1));
        }
        return poly.length < 3 ? 0 : clippedArea(poly, w1, w2);
    }

    /** {@code aᵀ C b} for cell {@code i}'s surface covariance {@code C}. */
    private double quad(int i, Vec3 a, Vec3 b) {
        double[] x = xyz(a), y = xyz(b);
        double sum = 0;
        for (int p = 0; p < 3; p++) {
            for (int q = 0; q < 3; q++) {
                sum += x[p] * cov(i, Math.min(p, q), Math.max(p, q)) * y[q];
            }
        }
        return sum;
    }

    /**
     * The chance the plane of the cell at {@code k} stops a ray that crosses it at distance {@code plane}: its
     * coverage within the box its surface lies in, nothing outside.
     */
    private double flatChance(int k, int x, int y, int z, Vec3 from, Vec3 d, double plane) {
        if (Double.isNaN(plane)) {
            return 0;
        }
        double w1 = derived[k + W1];
        if (w1 < 0) {
            return derived[k + COVER];
        }
        Vec3 q = from.plus(d.times(plane)).minus(centre(x, y, z))
                .minus(new Vec3(derived[k + BX], derived[k + BY], derived[k + BZ]));
        Vec3 u = new Vec3(derived[k + UX], derived[k + UY], derived[k + UZ]);
        double eps = 1e-6 * cell;
        if (Math.abs(q.dot(u)) > w1 + eps) {
            return 0;
        }
        if (dims == 3 && Math.abs(q.dot(planeNormal(k).cross(u))) > derived[k + W2] + eps) {
            return 0;
        }
        return derived[k + COVER];
    }

    /**
     * The area of the plane {@code n·x = h} inside a cell centred on the origin (its length, in 2D): the polygon
     * where it cuts the cell's edges, ordered round its centroid.
     */
    private double sliceArea(Vec3 n, double h) {
        List<Vec3> points = slice(n, h);
        if (dims == 2) {
            return points.size() < 2 ? 0 : points.get(0).minus(points.get(1)).length();
        }
        if (points.size() < 3) {
            return 0;
        }
        Vec3 t1 = (Math.abs(n.x()) < 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0)).cross(n).normalize();
        Vec3 t2 = n.cross(t1);
        double[][] flat = new double[points.size()][];
        for (int j = 0; j < flat.length; j++) {
            flat[j] = new double[]{points.get(j).dot(t1), points.get(j).dot(t2)};
        }
        return area(flat);
    }

    /**
     * The plane {@code n·x = h} inside a cell centred on the origin: in 3D the polygon where it cuts the cell's
     * edges, in order round it; in 2D the two ends of the segment.
     */
    private List<Vec3> slice(Vec3 n, double h) {
        double a = cell / 2;
        int bits = dims == 3 ? 3 : 2;
        double eps = 1e-9 * cell;
        List<Vec3> points = new ArrayList<>();
        for (int corner = 0; corner < (1 << bits); corner++) {
            Vec3 p = corner(corner, a);
            double f = n.dot(p) - h;
            if (Math.abs(f) <= eps) {
                points.add(p);   // a corner on the plane: a plane on a face has only these
                continue;
            }
            for (int b = 0; b < bits; b++) {
                if ((corner & (1 << b)) != 0) {
                    continue;
                }
                Vec3 q = corner(corner | (1 << b), a);
                double g = n.dot(q) - h;
                if (Math.abs(g) > eps && (f < 0) != (g < 0)) {
                    points.add(p.plus(q.minus(p).times(f / (f - g))));
                }
            }
        }
        if (dims == 2) {
            Vec3 p0 = null, p1 = null;
            double longest = -1;
            for (Vec3 p : points) {
                for (Vec3 q : points) {
                    if (p.minus(q).length() > longest) {
                        longest = p.minus(q).length();
                        p0 = p;
                        p1 = q;
                    }
                }
            }
            return p0 == null ? List.of() : List.of(p0, p1);
        }
        if (points.size() < 3) {
            return points;
        }
        Vec3 centroid = Vec3.ZERO;
        for (Vec3 p : points) {
            centroid = centroid.plus(p);
        }
        Vec3 o = centroid.times(1.0 / points.size());
        Vec3 t1 = (Math.abs(n.x()) < 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0)).cross(n).normalize();
        Vec3 t2 = n.cross(t1);
        points.sort((p, q) -> Double.compare(Math.atan2(p.minus(o).dot(t2), p.minus(o).dot(t1)),
                Math.atan2(q.minus(o).dot(t2), q.minus(o).dot(t1))));
        return points;
    }

    /** The area of a polygon in the plane, by the shoelace formula. */
    private static double area(double[][] p) {
        double twice = 0;
        for (int j = 0; j < p.length; j++) {
            double[] a = p[j], b = p[(j + 1) % p.length];
            twice += a[0] * b[1] - a[1] * b[0];
        }
        return Math.abs(twice) / 2;
    }

    /** The area of a convex polygon inside the box {@code |x| ≤ w1, |y| ≤ w2}, by clipping it to each side. */
    private static double clippedArea(double[][] polygon, double w1, double w2) {
        List<double[]> p = new ArrayList<>(Arrays.asList(polygon));
        double[][] sides = {{1, 0, w1}, {-1, 0, w1}, {0, 1, w2}, {0, -1, w2}};   // a·x + b·y ≤ c
        for (double[] s : sides) {
            List<double[]> out = new ArrayList<>();
            for (int j = 0; j < p.size(); j++) {
                double[] a = p.get(j), b = p.get((j + 1) % p.size());
                double fa = s[0] * a[0] + s[1] * a[1] - s[2], fb = s[0] * b[0] + s[1] * b[1] - s[2];
                if (fa <= 0) {
                    out.add(a);
                }
                if ((fa < 0) != (fb < 0) && fa != 0 && fb != 0) {
                    double t = fa / (fa - fb);
                    out.add(new double[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t});
                }
            }
            p = out;
            if (p.size() < 3) {
                return 0;
            }
        }
        return area(p.toArray(double[][]::new));
    }

    private Vec3 corner(int bits, double a) {
        return new Vec3((bits & 1) != 0 ? a : -a, (bits & 2) != 0 ? a : -a,
                dims == 3 ? ((bits & 4) != 0 ? a : -a) : 0);
    }

    // ---- filtering

    /**
     * The field at twice the cell size: each cell the moments of the two by two (2D) or two by two by two (3D)
     * beneath it, combined exactly where they can be. Volume, area and the share of each material are kept; the
     * mean normal of surfaces facing different ways shortens. Offsets are carried onto the new mean direction, a
     * tilted child's own extent adding to their spread. Granularity, rate and coverage are derived afresh.
     */
    public VexelField coarser() {
        int px = (nx + 1) / 2, py = (ny + 1) / 2, pz = dims == 3 ? (nz + 1) / 2 : 1;
        VexelField p = new VexelField(dims, origin, cell * 2, px, py, pz, materials, attributes);
        for (int z = 0; z < pz; z++) {
            for (int y = 0; y < py; y++) {
                for (int x = 0; x < px; x++) {
                    p.combine(this, x, y, z);
                    p.derive(p.cellIndex(x, y, z));
                }
            }
        }
        return p;
    }

    private void combine(VexelField f, int x, int y, int z) {
        int o = cellIndex(x, y, z) * stride;
        int children = dims == 3 ? 8 : 4;
        List<Integer> kids = new ArrayList<>(children);
        for (int dz = 0; dz < (dims == 3 ? 2 : 1); dz++) {
            for (int dy = 0; dy < 2; dy++) {
                for (int dx = 0; dx < 2; dx++) {
                    int cx = 2 * x + dx, cy = 2 * y + dy, cz = dims == 3 ? 2 * z + dz : 0;
                    if (cx < f.nx && cy < f.ny && cz < f.nz) {
                        kids.add(f.cellIndex(cx, cy, cz));
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
        Vec3 c = centre(x, y, z);
        if (occ > 0) {
            // the solid's centroid and spread, by the parallel-axis rule over the children's
            Vec3 sum = Vec3.ZERO;
            for (int k : kids) {
                int i = k * f.stride;
                sum = sum.plus(f.solidCentroid(k).times(f.data[i + OCC]));
            }
            Vec3 centroid = sum.times(1 / occ);
            double spread = 0;
            for (int k : kids) {
                int i = k * f.stride;
                Vec3 off = f.solidCentroid(k).minus(centroid);
                spread += f.data[i + OCC] * (f.data[i + S_VAR] + off.dot(off));
            }
            storeSolid(o, centroid.minus(c), spread / occ);
        }
        if (area == 0) {
            return;
        }
        Vec3 mean = nsum.times(1 / area);
        Vec3 axis = mean.normalize();
        double hm = 0, h2 = 0;
        double[] mat = new double[materials];
        double[] am = new double[attributes], a2 = new double[attributes];
        for (int k : kids) {
            int i = k * f.stride;
            double a = f.data[i + AREA];
            if (a == 0) {
                continue;
            }
            int cx = k % f.nx, cy = (k / f.nx) % f.ny, cz = k / (f.nx * f.ny);
            Vec3 cn = new Vec3(f.data[i + NX], f.data[i + NY], f.data[i + NZ]).normalize();
            double cos = cn.dot(axis);
            double shifted = f.centre(cx, cy, cz).minus(c).dot(axis) + f.data[i + H_MEAN] * cos;
            double tilt = (1 - cos * cos) * f.cell * f.cell / 12;
            hm += a * shifted;
            h2 += a * (shifted * shifted + f.data[i + H_VAR] * cos * cos + tilt);
            for (int m = 0; m < materials; m++) {
                mat[m] += a * f.data[i + MAT + m];
            }
            for (int q = 0; q < attributes; q++) {
                am[q] += a * f.data[i + f.attr + q];
                a2[q] += a * f.data[i + f.attr + attributes + q];
            }
        }
        hm /= area;
        store(o, area / children, mean, hm, h2 / area - hm * hm, mat, am, a2, area);
        // where the surface is, by the parallel-axis rule over the children's centroids and covariances
        double[] fs = new double[3], ff = new double[6];
        for (int k : kids) {
            int i = k * f.stride;
            double a = f.data[i + AREA];
            if (a == 0) {
                continue;
            }
            int cx = k % f.nx, cy = (k / f.nx) % f.ny, cz = k / (f.nx * f.ny);
            double[] p = xyz(f.centre(cx, cy, cz).minus(c)
                    .plus(new Vec3(f.data[i + FX], f.data[i + FY], f.data[i + FZ])));
            for (int u = 0; u < 3; u++) {
                fs[u] += a * p[u];
                for (int v = u; v < 3; v++) {
                    ff[COV[u][v]] += a * (f.cov(i, u, v) + p[u] * p[v]);
                }
            }
        }
        double[] m = {fs[0] / area, fs[1] / area, fs[2] / area};
        double[] cov = new double[6];
        for (int u = 0; u < 3; u++) {
            for (int v = u; v < 3; v++) {
                cov[COV[u][v]] = ff[COV[u][v]] / area - m[u] * m[v];
            }
        }
        storeSurface(o, m, cov);
    }

    // ---- rays

    /** A cell a ray crosses, from {@code t} to {@code exit}, and what it holds along there. */
    private interface Visit {
        /** False to stop walking. */
        boolean cell(int x, int y, int z, double t, double exit);
    }

    /** The expected outcome of a ray in a 2D field's plane. */
    public RayHits trace2d(double x, double y, double dx, double dy) {
        return trace(new Vec3(x, y, 0), new Vec3(dx, dy, 0), Double.POSITIVE_INFINITY);
    }

    /** One outcome of a ray in a 2D field's plane, or null for one that goes through. */
    public Collision collide2d(double x, double y, double dx, double dy, VexelHash hash) {
        return collide(new Vec3(x, y, 0), new Vec3(dx, dy, 0), Double.POSITIVE_INFINITY, hash);
    }

    /** {@link #trace(Vec3, Vec3, double)} as far as the field goes. */
    public RayHits trace(Vec3 from, Vec3 direction) {
        return trace(from, direction, Double.POSITIVE_INFINITY);
    }

    /**
     * The expected outcome of the ray from {@code from} along {@code direction}, up to {@code tMax}: per cell it
     * crosses, the probability of its colliding there. In a cell, the plane stops it with the cell's coverage if it
     * crosses the plane there, and the granular share with {@code 1 - exp(-rate · length)}; the two are independent.
     * Distances along the ray are in world units.
     */
    public RayHits trace(Vec3 from, Vec3 direction, double tMax) {
        Vec3 d = direction.normalize();
        List<RayHits.Hit> hits = new ArrayList<>();
        double[] through = {1};
        walk(from, d, tMax, (x, y, z, t, exit) -> {
            int c = cellIndex(x, y, z), k = c * DERIVED;
            double plane = crossing(c, x, y, z, from, d, t, exit);
            double flat = flatChance(k, x, y, z, from, d, plane);
            double granular = 1 - Math.exp(-depth(c, x, y, z, from, d, t, exit));
            double entry = through(k, x, y, z, from, d, t, exit);
            double solid = Double.isNaN(entry) ? 0 : derived[k + FILL];
            double p = 1 - (1 - flat) * (1 - granular) * (1 - solid);
            if (p > 0) {
                double g = derived[k + GRAN];
                double th = (flat * (Double.isNaN(plane) ? 0 : plane)
                        + (granular > 0 ? granular * meanStop(c, x, y, z, from, d, t, exit, granular) : 0)
                        + (solid > 0 ? solid * ((1 - g) * (Double.isNaN(plane) ? entry : plane) + g * entry) : 0))
                        / (flat + granular + solid);
                int i = c * stride;
                hits.add(new RayHits.Hit(through[0] * p, th, from.plus(d.times(th)), planeNormal(k),
                        derived[k + GRAN], Arrays.copyOfRange(data, i + MAT, i + MAT + materials)));
                through[0] *= 1 - p;
            }
            return through[0] > 1e-6;
        });
        return new RayHits(hits);
    }

    /**
     * One outcome of the ray, decided by {@code hash}: the first cell whose plane (by its coverage) or granular share
     * (by an exponential of its rate) stops it, and where, and on what material; null for a ray that goes through.
     * Every value drawn is a hash of the cell and the ray's angle bucket, so the outcome holds still while they do.
     */
    public Collision collide(Vec3 from, Vec3 direction, double tMax, VexelHash hash) {
        Vec3 d = direction.normalize();
        int bucket = hash.bucket(d, dims);
        Collision[] out = {null};
        walk(from, d, tMax, (x, y, z, t, exit) -> {
            int c = cellIndex(x, y, z), k = c * DERIVED;
            double best = Double.POSITIVE_INFINITY;
            boolean granular = false;
            double plane = crossing(c, x, y, z, from, d, t, exit);
            if (hash.u(x, y, z, bucket, VexelHash.FLAT) < flatChance(k, x, y, z, from, d, plane)) {
                best = plane;
            }
            double target = -Math.log(1 - hash.u(x, y, z, bucket, VexelHash.GRANULAR));
            if (depth(c, x, y, z, from, d, t, exit) > target) {
                // where the expected count of collisions since entering reaches the drawn one: monotone, so bisect
                double lo = t, hi = exit;
                for (int i = 0; i < 48; i++) {
                    double mid = 0.5 * (lo + hi);
                    if (depth(c, x, y, z, from, d, t, mid) < target) {
                        lo = mid;
                    } else {
                        hi = mid;
                    }
                }
                double tg = 0.5 * (lo + hi);
                if (tg < best) {
                    best = tg;
                    granular = true;
                }
            }
            double entry = through(k, x, y, z, from, d, t, exit);
            if (!Double.isNaN(entry) && hash.u(x, y, z, bucket, VexelHash.SOLID) < derived[k + FILL]) {
                boolean kind = hash.u(x, y, z, bucket, VexelHash.KIND) < derived[k + GRAN];
                double ts = kind || Double.isNaN(plane) ? entry : plane;
                if (ts < best) {
                    best = ts;
                    granular = kind;
                }
            }
            if (best == Double.POSITIVE_INFINITY) {
                return true;
            }
            int material = pick(c * stride, hash.u(x, y, z, bucket, VexelHash.MATERIAL));
            out[0] = new Collision(best, from.plus(d.times(best)), granular, granular ? Vec3.ZERO : planeNormal(k),
                    material, x, y, z);
            return false;
        });
        return out[0];
    }

    /**
     * Where the ray enters the cell's solid, as the ball of the solid's centroid and spread, between {@code t} and
     * {@code exit}; NaN if it passes it by. A ray from outside that goes through solid has met a surface, so this
     * stops it with the ball's fill: certainly for a compact solid, seldom for a scattered one. What it is stopped by
     * goes by granularity: the plane, where the ray crosses it in the cell, or the granular share, with no normal.
     * The plane's coverage alone is spread evenly over its slice of the cell, which a curved piece of surface (a
     * sphere's cap) covers only in part, and so lets through rays that go through the cap's solid.
     */
    private double through(int k, int x, int y, int z, Vec3 from, Vec3 d, double t, double exit) {
        double r = derived[k + BALL];
        if (r <= 0 || derived[k + FILL] <= 0) {
            return Double.NaN;
        }
        Vec3 w = centre(x, y, z).plus(new Vec3(derived[k + GX], derived[k + GY], derived[k + GZ])).minus(from);
        double tc = w.dot(d);
        double across = w.dot(w) - tc * tc;
        if (across >= r * r) {
            return Double.NaN;
        }
        double half = Math.sqrt(r * r - across);
        double in = Math.max(t, tc - half), out = Math.min(exit, tc + half);
        if (derived[k + COVER] > 0) {
            // a cell with a plane has its solid behind it: only the part of the ball there counts, or the ball of a
            // thin layer of solid would stand out past its surface and stop rays that graze it
            Vec3 n = planeNormal(k);
            double at = from.minus(centre(x, y, z)).dot(n) - derived[k + PH];   // signed height above the plane
            double rise = d.dot(n);
            if (rise == 0) {
                if (at > 0) {
                    return Double.NaN;
                }
            } else if (rise > 0) {
                out = Math.min(out, -at / rise);
            } else {
                in = Math.max(in, -at / rise);
            }
        }
        return in < out ? in : Double.NaN;
    }

    private Vec3 planeNormal(int k) {
        return new Vec3(derived[k + PX], derived[k + PY], derived[k + PZ]);
    }

    /** Where along the ray it crosses cell {@code c}'s plane between {@code t} and {@code exit}; NaN if it does not. */
    private double crossing(int c, int x, int y, int z, Vec3 from, Vec3 d, double t, double exit) {
        int k = c * DERIVED;
        if (derived[k + COVER] == 0) {
            return Double.NaN;
        }
        Vec3 n = planeNormal(k);
        Vec3 centre = centre(x, y, z);
        double h = derived[k + PH];
        double in = from.plus(d.times(t)).minus(centre).dot(n) - h;
        double out = from.plus(d.times(exit)).minus(centre).dot(n) - h;
        double eps = 1e-9 * cell;
        if (Math.min(in, out) > eps || Math.max(in, out) < -eps) {
            return Double.NaN;
        }
        return in == out ? 0.5 * (t + exit) : Math.clamp(t + (exit - t) * in / (in - out), t, exit);
    }

    /**
     * The expected number of collisions with cell {@code c}'s granular share along the ray from {@code a} to
     * {@code b}: its rate spread over the cell as an isotropic Gaussian at the solid's centroid, of the solid's
     * spread, so a ray through the middle of a ball meets more of it than one past its edge, the cell's total kept.
     * In closed form: across the ray the Gaussian is a constant, along it a difference of normal distributions. A cell
     * with no solid, only surface, has its rate spread evenly. The Gaussian's tails past the cell's walls are lost,
     * which a cell whose solid is spread wide undercounts.
     */
    private double depth(int c, int x, int y, int z, Vec3 from, Vec3 d, double a, double b) {
        int k = c * DERIVED;
        double rate = derived[k + RATE];
        double v = derived[k + G_VAR];
        if (rate == 0 || b <= a) {
            return 0;
        }
        if (v <= 1e-12 * cell * cell) {
            return rate * (b - a);
        }
        Vec3 w = centre(x, y, z).plus(new Vec3(derived[k + GX], derived[k + GY], derived[k + GZ])).minus(from);
        double tc = w.dot(d);
        double across = Math.max(0, w.dot(w) - tc * tc);
        double s = Math.sqrt(v);
        double density = Math.pow(2 * Math.PI * v, -(dims - 1) / 2.0) * Math.exp(-across / (2 * v));
        return rate * Math.pow(cell, dims) * density * (phi((b - tc) / s) - phi((a - tc) / s));
    }

    /** The expected distance of the granular share's stop, given one between {@code a} and {@code b}, by Simpson. */
    private double meanStop(int c, int x, int y, int z, Vec3 from, Vec3 d, double a, double b, double stopped) {
        int n = 32;
        double h = (b - a) / n;
        double end = 1 - stopped;
        double sum = 0;
        for (int i = 0; i <= n; i++) {
            double survive = Math.exp(-depth(c, x, y, z, from, d, a, a + i * h)) - end;
            sum += survive * (i == 0 || i == n ? 1 : i % 2 == 1 ? 4 : 2);
        }
        return a + sum * h / 3 / stopped;
    }

    /** The standard normal distribution function, by Abramowitz and Stegun 7.1.26: within 1.5e-7. */
    private static double phi(double x) {
        double z = Math.abs(x) / Math.sqrt(2);
        double k = 1 / (1 + 0.3275911 * z);
        double erf = 1 - k * (0.254829592 + k * (-0.284496736 + k * (1.421413741 + k * (-1.453152027
                + k * 1.061405429)))) * Math.exp(-z * z);
        return 0.5 * (1 + (x < 0 ? -erf : erf));
    }

    /** The material whose share of the cell at {@code i} the value {@code u} falls in. */
    private int pick(int i, double u) {
        double total = 0;
        for (int m = 0; m < materials; m++) {
            total += data[i + MAT + m];
        }
        double acc = 0;
        for (int m = 0; m < materials; m++) {
            acc += data[i + MAT + m];
            if (u * total < acc) {
                return m;
            }
        }
        return materials - 1;
    }

    /** Walk the cells the ray crosses with surface in them, nearest first, by Amanatides and Woo. */
    private void walk(Vec3 from, Vec3 d, double tMax, Visit visit) {
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
                    return;
                }
            } else {
                double ta = (lo[a] - o[a]) / dir[a], tb = (hi - o[a]) / dir[a];
                t0 = Math.max(t0, Math.min(ta, tb));
                t1 = Math.min(t1, Math.max(ta, tb));
            }
        }
        if (t0 >= t1) {
            return;
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
        double t = t0;
        while (t < t1) {
            int a = next[0] < next[1] ? (next[0] < next[2] ? 0 : 2) : (next[1] < next[2] ? 1 : 2);
            double exit = Math.min(next[a], t1);
            if (exit > t && data[cellIndex(idx[0], idx[1], idx[2]) * stride + AREA] > 0
                    && !visit.cell(idx[0], idx[1], idx[2], t, exit)) {
                return;
            }
            t = exit;
            if (next[a] >= t1) {
                return;
            }
            idx[a] += step[a];
            if (idx[a] < 0 || idx[a] >= n[a]) {
                return;
            }
            next[a] += delta[a];
        }
    }
}
