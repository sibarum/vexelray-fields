package dev.vexelray.fields.convert;

import dev.vexelray.fields.shape.Shape;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A mask's boundary traced back into a shape: the exact route from anything sampled to a field that wants outlines,
 * an MSDF's corners above all.
 *
 * <p><b>Cracks, their midpoints, runs, then lines refitted.</b> Every side between an inside sample and an outside one
 * is a crack, directed so the inside is on its right; at a corner where two inside samples touch only diagonally the
 * trace turns left, so they stay one outline, pinched there, and a sharp tip sampled as a diagonal run of single
 * samples is not broken off into specks. A staircase of cracks runs through the midpoints of its steps, which stray
 * from the edge it stands for by less than a sample whatever its slope. Douglas–Peucker splits the midpoints into runs
 * that each stay within a sample plus {@code flatness} of a line: a corner ends a run, a staircase does not. Each run is
 * then refitted, the line through it by least squares, which averages its steps out; and each corner is put where the
 * lines either side of it meet, which gives back the point the cracks cut off. (Every midpoint a vertex would make each
 * step a corner, which an MSDF's edge colouring cannot keep apart.) Holes come out wound against their outlines, so the
 * nonzero fill is the mask's.
 *
 * <p>So a straight edge comes back within a fraction of a sample, and a corner sharp: exact to the oversample, as
 * conversions.md has it. A curve comes back as lines within {@code flatness} of it.
 */
final class MaskTrace {

    private static final int[] DX = {1, 0, -1, 0};   // east, south, west, north: clockwise with y down
    private static final int[] DY = {0, 1, 0, -1};

    private MaskTrace() {
    }

    static Shape trace(Mask m, double flatness) {
        if (!(flatness >= 0)) {
            throw new IllegalArgumentException("a flatness of " + flatness);
        }
        return outline(m, flatness, false);
    }

    /**
     * The mask's own samples as squares: its boundary exactly, a vertex at every corner the cracks turn and none
     * between, as pixel art is drawn. Holes and the left turn where samples touch diagonally are as {@link #trace}'s.
     */
    static Shape cells(Mask m) {
        return outline(m, 0, true);
    }

    private static Shape outline(Mask m, double flatness, boolean cells) {
        int w = m.width(), h = m.height(), vw = w + 1;
        int[] out = new int[vw * (h + 1) * 2];
        Arrays.fill(out, -1);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!m.at(x, y)) {
                    continue;
                }
                if (!m.at(x, y - 1)) {
                    add(out, y * vw + x, 0);
                }
                if (!m.at(x + 1, y)) {
                    add(out, y * vw + x + 1, 1);
                }
                if (!m.at(x, y + 1)) {
                    add(out, (y + 1) * vw + x + 1, 2);
                }
                if (!m.at(x - 1, y)) {
                    add(out, (y + 1) * vw + x, 3);
                }
            }
        }
        boolean[] used = new boolean[out.length];
        Shape.Builder b = Shape.builder();
        boolean any = false;
        double ups = m.unitsPerSample();
        for (int start = 0; start < out.length; start++) {
            if (out[start] < 0 || used[start]) {
                continue;
            }
            List<int[]> cracks = new ArrayList<>();
            int slot = start;
            while (true) {
                used[slot] = true;
                int v = slot / 2, d = out[slot];
                int x = v % vw, y = v / vw;
                cracks.add(new int[]{x, y, d});
                int next = (y + DY[d]) * vw + x + DX[d];
                int chosen = -1;
                for (int turn : new int[]{3, 0, 1}) {
                    int want = (d + turn) & 3;
                    for (int k = 0; k < 2 && chosen < 0; k++) {
                        int s = next * 2 + k;
                        if (out[s] == want && (!used[s] || s == start)) {
                            chosen = s;
                        }
                    }
                    if (chosen >= 0) {
                        break;
                    }
                }
                if (chosen < 0 || chosen == start) {
                    break;
                }
                slot = chosen;
            }
            List<double[]> pts = new ArrayList<>();
            int n = cracks.size();
            for (int i = 0; i < n; i++) {
                int[] c = cracks.get(i);
                if (!cells) {
                    pts.add(new double[]{c[0] + 0.5 * DX[c[2]], c[1] + 0.5 * DY[c[2]]});
                } else if (c[2] != cracks.get((i + n - 1) % n)[2]) {
                    pts.add(new double[]{c[0], c[1]});   // a corner: the crack before ran another way
                }
            }
            double[][] kept = cells ? pts.toArray(new double[0][]) : refit(pts, runs(pts, 1 + flatness));
            if (kept.length < 3) {
                continue;
            }
            b.moveTo(m.originX() + kept[0][0] * ups, m.originY() + kept[0][1] * ups);
            for (int i = 1; i < kept.length; i++) {
                b.lineTo(m.originX() + kept[i][0] * ups, m.originY() + kept[i][1] * ups);
            }
            b.close();
            any = true;
        }
        return any ? b.build() : new Shape(List.of());
    }

    private static void add(int[] out, int vertex, int dir) {
        int s = vertex * 2;
        out[out[s] < 0 ? s : s + 1] = dir;
    }

    /**
     * Where the closed polyline's runs start: the indices Douglas–Peucker keeps, from its two far ends, so every point
     * is within {@code tol} of the chord of its run. Ascending.
     */
    static int[] runs(List<double[]> pts, double tol) {
        int n = pts.size();
        if (n < 4) {
            int[] all = new int[n];
            Arrays.setAll(all, i -> i);
            return all;
        }
        int far = 0;
        double best = -1;
        double[] p0 = pts.getFirst();
        for (int i = 1; i < n; i++) {
            double dx = pts.get(i)[0] - p0[0], dy = pts.get(i)[1] - p0[1];
            double d = dx * dx + dy * dy;
            if (d > best) {
                best = d;
                far = i;
            }
        }
        boolean[] keep = new boolean[n + 1];
        keep[0] = keep[far] = keep[n] = true;
        // the closed run 0..far..n, where n stands for 0 again
        int[] stack = {0, far, far, n};
        int top = 4;
        int[] st = Arrays.copyOf(stack, 2 * n + 8);
        while (top > 0) {
            int j = st[--top], i = st[--top];
            if (j - i < 2) {
                continue;
            }
            double[] a = pts.get(i % n), c = pts.get(j % n);
            double ex = c[0] - a[0], ey = c[1] - a[1];
            double len = Math.hypot(ex, ey);
            int worst = -1;
            double worstD = tol;
            for (int k = i + 1; k < j; k++) {
                double[] p = pts.get(k);
                double d = len < 1e-12 ? Math.hypot(p[0] - a[0], p[1] - a[1])
                        : Math.abs((p[0] - a[0]) * ey - (p[1] - a[1]) * ex) / len;
                if (d > worstD) {
                    worstD = d;
                    worst = k;
                }
            }
            if (worst >= 0) {
                keep[worst] = true;
                if (top + 4 > st.length) {
                    st = Arrays.copyOf(st, st.length * 2);
                }
                st[top++] = i;
                st[top++] = worst;
                st[top++] = worst;
                st[top++] = j;
            }
        }
        int count = 0;
        for (int i = 0; i < n; i++) {
            count += keep[i] ? 1 : 0;
        }
        int[] starts = new int[count];
        for (int i = 0, k = 0; i < n; i++) {
            if (keep[i]) {
                starts[k++] = i;
            }
        }
        return starts;
    }

    /**
     * The polygon of the runs from {@code starts}, each run's line refitted through all its points by least squares,
     * and each vertex where a run's line meets the next's. Where two lines are nearly parallel, or meet further from the
     * run's end than {@link #REACH} samples (a shallow turn, where a meeting point is ill-conditioned), the vertex
     * stays at the run's end instead.
     */
    static double[][] refit(List<double[]> pts, int[] starts) {
        int n = pts.size(), k = starts.length;
        if (k < 3) {
            double[][] out = new double[k][];
            for (int i = 0; i < k; i++) {
                out[i] = pts.get(starts[i]);
            }
            return out;
        }
        double[][] line = new double[k][];   // a point on it and its unit direction
        for (int r = 0; r < k; r++) {
            int a = starts[r], b = r + 1 < k ? starts[r + 1] : starts[0] + n;   // the run, both ends in
            if (b - a >= 4) {
                // its ends are where the cracks cut a corner, off both lines: fitted from between them
                a++;
                b--;
            }
            double cx = 0, cy = 0;
            int m = b - a + 1;
            for (int i = a; i <= b; i++) {
                double[] p = pts.get(i % n);
                cx += p[0];
                cy += p[1];
            }
            cx /= m;
            cy /= m;
            double sxx = 0, sxy = 0, syy = 0;
            for (int i = a; i <= b; i++) {
                double[] p = pts.get(i % n);
                double dx = p[0] - cx, dy = p[1] - cy;
                sxx += dx * dx;
                sxy += dx * dy;
                syy += dy * dy;
            }
            double angle = 0.5 * Math.atan2(2 * sxy, sxx - syy);   // the principal axis
            line[r] = new double[]{cx, cy, Math.cos(angle), Math.sin(angle)};
        }
        double[][] out = new double[k][];
        for (int r = 0; r < k; r++) {
            double[] u = line[(r + k - 1) % k], v = line[r];
            double[] end = pts.get(starts[r]);
            double cross = u[2] * v[3] - u[3] * v[2];
            double[] at = end;
            if (Math.abs(cross) > 1e-3) {
                double t = ((v[0] - u[0]) * v[3] - (v[1] - u[1]) * v[2]) / cross;
                double x = u[0] + t * u[2], y = u[1] + t * u[3];
                if (Math.hypot(x - end[0], y - end[1]) <= REACH) {
                    at = new double[]{x, y};
                }
            }
            out[r] = at;
        }
        return out;
    }

    /** How far a refitted corner may move from the run's end: a sharp corner's cut, a few samples, and no further. */
    static final double REACH = 4;
}
