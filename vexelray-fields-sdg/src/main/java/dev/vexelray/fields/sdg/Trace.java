package dev.vexelray.fields.sdg;

import dev.vexelray.fields.Field;
import dev.vexelray.fields.art.Raster;
import dev.vexelray.fields.sdf.MaskSdf;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * A raster traced into signed distance graphics: its colours gathered into regions, each region a layer whose stencil
 * is where the region is and whose paint is the raster's own colour there. Edges come out sharp at any size; colour
 * within a region is the raster's, read smoothly, so shading inside a region is kept.
 *
 * <ol>
 *   <li><b>Regions.</b> The pixels at least half opaque are clustered by premultiplied colour (k-means, seeded by
 *       k-means++ from a fixed seed, so a trace is the same every time). Transparency is a region of its own, never
 *       painted.</li>
 *   <li><b>Shares.</b> An antialiased pixel is a blend of the regions either side of the edge through it. Each pixel
 *       is taken as the blend of two regions found round it that comes nearest its colour, in premultiplied colour,
 *       where a blend with transparency is linear too: its share of each is how far along the blend it is. That puts
 *       an edge within a fraction of a pixel, where a pixel's nearest region alone would put it on the pixel grid.</li>
 *   <li><b>Cut.</b> On a fine grid of {@code oversample} pixels a texel, each point belongs to the region whose share,
 *       read between pixels, is largest there. That partitions the plane: no point is in two regions or none.</li>
 *   <li><b>Stacked.</b> Regions are layered largest first. Each layer's stencil is its region and, beneath the regions
 *       layered over it, {@code under} texels more of them. An edge between two regions is then drawn once, by the
 *       upper one's coverage over the lower one solid, so no seam of the background shows through where two
 *       antialiased edges meet, and no third colour fringes it.</li>
 *   <li><b>Paint.</b> A region whose colour varies by less than {@code flatness} (RMS, of 255) is one colour, its mean.
 *       Otherwise it is a texture at the raster's resolution: the pixels wholly in the region as they are, and the
 *       rest, out to the stencil's edge, carried outward from them, so reading between texels at an edge never mixes
 *       in the neighbour's colour or an antialiased blend.</li>
 * </ol>
 */
public final class Trace {

    /** Transparency's region. */
    private static final int CLEAR = -1;

    /** A pixel this far into one region is wholly in it, for its colour. */
    private static final float PURE = 0.9f;

    /**
     * Regions whose colours are this near (premultiplied, Euclidean, of 1) meet at a soft boundary, a gradient's, not
     * an edge: the pixels either side of it are their regions' own colour.
     */
    private static final double SOFT = 0.25;

    /**
     * @param colours    regions of colour to gather the raster's into
     * @param texels     texels of field to a pixel of the raster, each way
     * @param spread     texels of margin round each layer, the farthest its field reaches, and half its range
     * @param under      texels a layer reaches beneath the regions layered over it
     * @param oversample fine pixels a texel each way, where regions are cut
     * @param flatness   below this RMS variation (of 255) a region's paint is one colour
     */
    public record Options(int colours, int texels, int spread, double under, int oversample, double flatness) {

        public static final Options DEFAULT = new Options(8, 1, 4, 2, 8, 1);

        public Options {
            if (colours < 1 || colours > 254 || texels < 1 || spread < 1 || under < 0 || oversample < 1
                    || flatness < 0) {
                throw new IllegalArgumentException(this.toString());
            }
        }

        public Options withColours(int colours) {
            return new Options(colours, texels, spread, under, oversample, flatness);
        }

        public Options withTexels(int texels) {
            return new Options(colours, texels, spread, under, oversample, flatness);
        }
    }

    private Trace() {
    }

    static Sdg trace(Raster raster, Options o) {
        int w = raster.width(), h = raster.height(), n = w * h;
        float[] pm = premultiplied(raster.rgba());

        // 1. regions
        float[][] centre = cluster(pm, n, o.colours());
        int k = centre.length;
        int[] label = new int[n];
        for (int i = 0; i < n; i++) {
            label[i] = pm[i * 4 + 3] < 0.5f ? CLEAR : nearest(pm, i, centre);
        }

        // 2. shares: each pixel a blend of regions a and b, share wa of a
        int[] la = new int[n], lb = new int[n];
        float[] wa = new float[n];
        shares(pm, w, h, label, centre, la, lb, wa);

        // the order: largest first
        long[] area = new long[k];
        for (int i = 0; i < n; i++) {
            if (label[i] != CLEAR) {
                area[label[i]]++;
            }
        }
        Integer[] order = new Integer[k];
        for (int i = 0; i < k; i++) {
            order[i] = i;
        }
        Arrays.sort(order, (p, q) -> Long.compare(area[q], area[p]) != 0 ? Long.compare(area[q], area[p]) : p - q);
        int[] rank = new int[k];
        for (int r = 0; r < k; r++) {
            rank[order[r]] = r;
        }

        // 3. cut on the fine grid: each point the rank of its region, or CLEAR
        int f = o.texels() * o.oversample();
        int fw = w * f, fh = h * f;
        int[] fine = cut(la, lb, wa, w, h, f, rank);

        // 4 and 5. a layer per region, bottom first
        Assembly a = new Assembly(w, h);
        int under = (int) Math.round(o.under() * o.oversample());
        int texelsPerPixel = o.texels();
        for (int r = 0; r < k; r++) {
            int region = order[r];
            if (area[region] == 0) {
                continue;
            }
            boolean[] mask = stacked(fine, fw, fh, r, under);
            int[] box = bounds(mask, fw, fh);
            if (box == null) {
                continue;
            }
            // the box in texels, then the mask cropped to it
            int tx0 = box[0] / o.oversample(), ty0 = box[1] / o.oversample();
            int tx1 = (box[2] + o.oversample() - 1) / o.oversample(), ty1 = (box[3] + o.oversample() - 1) / o.oversample();
            int cw = (tx1 - tx0) * o.oversample(), ch = (ty1 - ty0) * o.oversample();
            boolean[] crop = new boolean[cw * ch];
            for (int y = 0; y < ch; y++) {
                System.arraycopy(mask, (ty0 * o.oversample() + y) * fw + tx0 * o.oversample(), crop, y * cw, cw);
            }
            Field field = MaskSdf.of(crop, cw, ch, o.oversample(), o.spread());
            double scale = texelsPerPixel;
            Assembly.Stencil stencil = new Assembly.Stencil(field, (tx0 - o.spread()) / scale,
                    (ty0 - o.spread()) / scale, scale, 2.0 * o.spread());
            String name = "region " + r;

            // the paint, from the pixels wholly in the region
            boolean[] known = known(label, la, wa, centre, w, h, region);
            double[] sum = new double[4], sq = new double[4];
            int pure = 0;
            for (int i = 0; i < n; i++) {
                if (known[i]) {
                    pure++;
                    for (int c = 0; c < 4; c++) {
                        float v = straight(raster.rgba(), i, c);
                        sum[c] += v;
                        sq[c] += v * v;
                    }
                }
            }
            if (pure == 0) {
                float[] cc = centre[region];
                float inv = cc[3] > 0 ? 1 / cc[3] : 0;
                a.add(name, stencil, Sdg.Paint.Solid.of(cc[0] * inv, cc[1] * inv, cc[2] * inv, cc[3]), 1f);
                continue;
            }
            double var = 0;
            for (int c = 0; c < 4; c++) {
                double m = sum[c] / pure;
                var += sq[c] / pure - m * m;
            }
            double rms = Math.sqrt(Math.max(0, var / 4)) * 255;
            if (rms < o.flatness()) {
                a.add(name, stencil, Sdg.Paint.Solid.of((float) (sum[0] / pure), (float) (sum[1] / pure),
                        (float) (sum[2] / pure), (float) (sum[3] / pure)), 1f);
            } else {
                a.add(name, stencil, texture(raster, known, tx0, ty0, tx1, ty1, o, texelsPerPixel), 1f);
            }
        }
        return a.build();
    }

    // ------------------------------------------------------------------------------------------------ regions

    private static float[] premultiplied(byte[] rgba) {
        float[] pm = new float[rgba.length];
        for (int i = 0; i < rgba.length; i += 4) {
            float a = (rgba[i + 3] & 0xFF) / 255f;
            pm[i] = (rgba[i] & 0xFF) / 255f * a;
            pm[i + 1] = (rgba[i + 1] & 0xFF) / 255f * a;
            pm[i + 2] = (rgba[i + 2] & 0xFF) / 255f * a;
            pm[i + 3] = a;
        }
        return pm;
    }

    private static float straight(byte[] rgba, int i, int c) {
        return (rgba[i * 4 + c] & 0xFF) / 255f;
    }

    /** Up to {@code k} centres for the pixels at least half opaque, by k-means seeded by k-means++. */
    private static float[][] cluster(float[] pm, int n, int k) {
        int[] idx = new int[n];
        int m = 0;
        for (int i = 0; i < n; i++) {
            if (pm[i * 4 + 3] >= 0.5f) {
                idx[m++] = i;
            }
        }
        if (m == 0) {
            return new float[0][];
        }
        Random random = new Random(0x5D6);
        List<float[]> centres = new ArrayList<>();
        centres.add(Arrays.copyOfRange(pm, idx[0] * 4, idx[0] * 4 + 4));
        double[] d2 = new double[m];
        while (centres.size() < k) {
            double total = 0;
            for (int j = 0; j < m; j++) {
                d2[j] = Double.MAX_VALUE;
                for (float[] c : centres) {
                    d2[j] = Math.min(d2[j], dist2(pm, idx[j], c));
                }
                total += d2[j];
            }
            if (total < 1e-9) {
                break;   // fewer colours than asked for
            }
            double pick = random.nextDouble() * total;
            int j = 0;
            while (j < m - 1 && (pick -= d2[j]) > 0) {
                j++;
            }
            centres.add(Arrays.copyOfRange(pm, idx[j] * 4, idx[j] * 4 + 4));
        }
        float[][] c = centres.toArray(new float[0][]);
        int[] of = new int[m];
        for (int iter = 0; iter < 24; iter++) {
            boolean moved = false;
            for (int j = 0; j < m; j++) {
                int best = nearest(pm, idx[j], c);
                moved |= best != of[j] || iter == 0;
                of[j] = best;
            }
            double[][] sum = new double[c.length][4];
            int[] count = new int[c.length];
            for (int j = 0; j < m; j++) {
                count[of[j]]++;
                for (int q = 0; q < 4; q++) {
                    sum[of[j]][q] += pm[idx[j] * 4 + q];
                }
            }
            for (int q = 0; q < c.length; q++) {
                if (count[q] > 0) {
                    for (int r = 0; r < 4; r++) {
                        c[q][r] = (float) (sum[q][r] / count[q]);
                    }
                }
            }
            if (!moved) {
                break;
            }
        }
        return c;
    }

    private static int nearest(float[] pm, int i, float[][] centre) {
        int best = 0;
        double bd = Double.MAX_VALUE;
        for (int q = 0; q < centre.length; q++) {
            double d = dist2(pm, i, centre[q]);
            if (d < bd) {
                bd = d;
                best = q;
            }
        }
        return best;
    }

    private static double dist2(float[] pm, int i, float[] c) {
        double s = 0;
        for (int q = 0; q < 4; q++) {
            double d = pm[i * 4 + q] - c[q];
            s += d * d;
        }
        return s;
    }

    /**
     * Each pixel as the blend of two regions found in the 3 × 3 round it (transparency among them, its colour
     * nothing) that comes nearest its colour: {@code la} with share {@code wa}, {@code lb} with the rest.
     */
    private static void shares(float[] pm, int w, int h, int[] label, float[][] centre, int[] la, int[] lb,
                               float[] wa) {
        int[] near = new int[10];
        float[] zero = new float[4];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                int m = 0;
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = x + dx, ny = y + dy;
                        if (nx < 0 || ny < 0 || nx >= w || ny >= h) {
                            continue;
                        }
                        int l = label[ny * w + nx];
                        boolean seen = false;
                        for (int q = 0; q < m; q++) {
                            seen |= near[q] == l;
                        }
                        if (!seen) {
                            near[m++] = l;
                        }
                    }
                }
                double best = Double.MAX_VALUE;
                int ba = label[i], bb = label[i];
                float bt = 0;
                for (int p = 0; p < m; p++) {
                    for (int q = p; q < m; q++) {
                        float[] ca = near[p] == CLEAR ? zero : centre[near[p]];
                        float[] cb = near[q] == CLEAR ? zero : centre[near[q]];
                        // t along a→b nearest the pixel
                        double dd = 0, dot = 0;
                        for (int c = 0; c < 4; c++) {
                            double e = cb[c] - ca[c];
                            dd += e * e;
                            dot += (pm[i * 4 + c] - ca[c]) * e;
                        }
                        double t = dd > 1e-12 ? Math.clamp(dot / dd, 0, 1) : 0;
                        double err = 0;
                        for (int c = 0; c < 4; c++) {
                            double v = ca[c] + (cb[c] - ca[c]) * t - pm[i * 4 + c];
                            err += v * v;
                        }
                        // a blend must do clearly better than one region alone
                        if (p != q) {
                            err += 1e-4;
                        }
                        if (err < best) {
                            best = err;
                            ba = near[p];
                            bb = near[q];
                            bt = (float) t;
                        }
                    }
                }
                la[i] = ba;
                lb[i] = bb;
                wa[i] = ba == bb ? 1 : 1 - bt;
            }
        }
    }

    /**
     * The fine grid, {@code f} points a pixel each way: at each point, read bilinearly between pixel centres, the rank
     * of the highest-layered region that with every region over it has at least half the share there; {@link #CLEAR}
     * where none has. A plurality would not do: where a gradient's regions meet, the gradient's share is split
     * between them, and a region over them would win where it has only a third.
     */
    private static int[] cut(int[] la, int[] lb, float[] wa, int w, int h, int f, int[] rank) {
        int fw = w * f, fh = h * f;
        int[] fine = new int[fw * fh];
        int[] ids = new int[8];
        float[] sh = new float[8];
        for (int fy = 0; fy < fh; fy++) {
            double sy = (fy + 0.5) / f - 0.5;
            int y0 = (int) Math.floor(sy);
            float ty = (float) (sy - y0);
            for (int fx = 0; fx < fw; fx++) {
                double sx = (fx + 0.5) / f - 0.5;
                int x0 = (int) Math.floor(sx);
                float tx = (float) (sx - x0);
                int m = 0;
                for (int c = 0; c < 4; c++) {
                    int px = Math.clamp(x0 + (c & 1), 0, w - 1), py = Math.clamp(y0 + (c >> 1), 0, h - 1);
                    float wt = ((c & 1) == 0 ? 1 - tx : tx) * ((c >> 1) == 0 ? 1 - ty : ty);
                    int i = py * w + px;
                    m = add(ids, sh, m, la[i], wt * wa[i]);
                    if (lb[i] != la[i]) {
                        m = add(ids, sh, m, lb[i], wt * (1 - wa[i]));
                    }
                }
                // as the layers stack: the highest region whose share, with all those over it, is at least half
                int best = CLEAR;
                float above = 0;
                while (m > 0) {
                    int top = -1;
                    for (int q = 0; q < m; q++) {
                        if (ids[q] != CLEAR && (top < 0 || rank[ids[q]] > rank[ids[top]])) {
                            top = q;
                        }
                    }
                    if (top < 0) {
                        break;
                    }
                    above += sh[top];
                    if (above >= 0.5f) {
                        best = rank[ids[top]];
                        break;
                    }
                    ids[top] = ids[m - 1];
                    sh[top] = sh[m - 1];
                    m--;
                }
                fine[fy * fw + fx] = best;
            }
        }
        return fine;
    }

    private static int add(int[] ids, float[] sh, int m, int id, float s) {
        for (int q = 0; q < m; q++) {
            if (ids[q] == id) {
                sh[q] += s;
                return m;
            }
        }
        ids[m] = id;
        sh[m] = s;
        return m + 1;
    }

    /** Region {@code r}, and the points of regions layered over it within {@code under} fine pixels of it. */
    private static boolean[] stacked(int[] fine, int fw, int fh, int r, int under) {
        boolean[] own = new boolean[fine.length];
        boolean any = false;
        for (int i = 0; i < fine.length; i++) {
            own[i] = fine[i] == r;
            any |= own[i];
        }
        if (!any || under <= 0) {
            return own;
        }
        // distance to the region, by the same exact transform, at one fine pixel a texel
        Field d = MaskSdf.of(own, fw, fh, 1, 0);
        float[] dd = d.data();
        boolean[] mask = new boolean[fine.length];
        for (int i = 0; i < fine.length; i++) {
            mask[i] = own[i] || fine[i] > r && 0.5f - dd[i] <= under;
        }
        return mask;
    }

    /** {@code x0, y0, x1, y1} of what the mask covers, x1 and y1 past it; null for nothing. */
    private static int[] bounds(boolean[] mask, int w, int h) {
        int x0 = w, y0 = h, x1 = -1, y1 = -1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (mask[y * w + x]) {
                    x0 = Math.min(x0, x);
                    x1 = Math.max(x1, x);
                    y0 = Math.min(y0, y);
                    y1 = Math.max(y1, y);
                }
            }
        }
        return x1 < 0 ? null : new int[]{x0, y0, x1 + 1, y1 + 1};
    }

    // ------------------------------------------------------------------------------------------------ paint

    /**
     * The pixels whose colour is the region's own: those in it whose eight neighbours are in it too, or in a region of
     * a colour within {@value #SOFT} of its own, so no pixel a real edge runs through counts, whatever its share says,
     * and the soft boundaries between a gradient's regions keep every pixel. In a region too thin to have any, those
     * at least {@value #PURE} in it.
     */
    private static boolean[] known(int[] label, int[] la, float[] wa, float[][] centre, int w, int h, int region) {
        boolean[] in = new boolean[w * h];
        boolean any = false;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                boolean all = label[y * w + x] == region;
                for (int dy = -1; dy <= 1 && all; dy++) {
                    for (int dx = -1; dx <= 1 && all; dx++) {
                        int nx = Math.clamp(x + dx, 0, w - 1), ny = Math.clamp(y + dy, 0, h - 1);
                        int l = label[ny * w + nx];
                        all = l == region || l != CLEAR && near(centre[l], centre[region]);
                    }
                }
                in[y * w + x] = all;
                any |= all;
            }
        }
        if (!any) {
            for (int i = 0; i < in.length; i++) {
                in[i] = la[i] == region && wa[i] >= PURE;
            }
        }
        return in;
    }

    private static boolean near(float[] a, float[] b) {
        double s = 0;
        for (int c = 0; c < 4; c++) {
            s += (a[c] - b[c]) * (a[c] - b[c]);
        }
        return s < SOFT * SOFT;
    }

    /**
     * The region's colour texture over its stencil's box (texels {@code tx0..tx1} and the margin), at a texel a pixel:
     * pixels wholly in the region as they are, every other one carried in from its known neighbours, ring by ring.
     */
    private static Assembly.Texture texture(Raster raster, boolean[] inRegion, int tx0, int ty0,
                                            int tx1, int ty1, Options o, int texelsPerPixel) {
        int w = raster.width(), h = raster.height();
        // the stencil's box in pixels, with its margin, and a pixel more for reading between
        int px0 = Math.max(0, Math.floorDiv(tx0 - o.spread(), texelsPerPixel) - 1);
        int py0 = Math.max(0, Math.floorDiv(ty0 - o.spread(), texelsPerPixel) - 1);
        int px1 = Math.min(w, Math.ceilDiv(tx1 + o.spread(), texelsPerPixel) + 1);
        int py1 = Math.min(h, Math.ceilDiv(ty1 + o.spread(), texelsPerPixel) + 1);
        int tw = px1 - px0, th = py1 - py0;
        float[] c = new float[tw * th * 4];
        boolean[] known = new boolean[tw * th];
        int unknown = 0;
        for (int y = 0; y < th; y++) {
            for (int x = 0; x < tw; x++) {
                int i = (py0 + y) * w + px0 + x, t = y * tw + x;
                if (inRegion[i]) {
                    known[t] = true;
                    for (int q = 0; q < 4; q++) {
                        c[t * 4 + q] = straight(raster.rgba(), i, q);
                    }
                } else {
                    unknown++;
                }
            }
        }
        boolean[] next = known.clone();
        while (unknown > 0) {
            int filled = 0;
            for (int y = 0; y < th; y++) {
                for (int x = 0; x < tw; x++) {
                    int t = y * tw + x;
                    if (known[t]) {
                        continue;
                    }
                    float[] s = new float[4];
                    int m = 0;
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            int nx = x + dx, ny = y + dy;
                            if (nx >= 0 && ny >= 0 && nx < tw && ny < th && known[ny * tw + nx]) {
                                m++;
                                for (int q = 0; q < 4; q++) {
                                    s[q] += c[(ny * tw + nx) * 4 + q];
                                }
                            }
                        }
                    }
                    if (m > 0) {
                        for (int q = 0; q < 4; q++) {
                            c[t * 4 + q] = s[q] / m;
                        }
                        next[t] = true;
                        filled++;
                    }
                }
            }
            if (filled == 0) {
                break;   // nothing known to carry: cannot happen for a region with a pure pixel in its box
            }
            System.arraycopy(next, 0, known, 0, known.length);
            unknown -= filled;
        }
        byte[] rgba = new byte[tw * th * 4];
        for (int i = 0; i < rgba.length; i++) {
            rgba[i] = SvgToSdg.byte8(c[i]);
        }
        return new Assembly.Texture(tw, th, rgba, px0, py0, 1);
    }
}
