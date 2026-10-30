package dev.vexelray.fields.atlas;

import dev.vexelray.fields.Encoding;
import dev.vexelray.fields.Field;
import dev.vexelray.fields.FieldKind;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Fields packed into one texture on grids of square cells, as {@code vexelray-gui}'s icon sets are baked: each grid's
 * cells fill whole rows in the order they are given, and the grids are stacked, the first at the top. The icon set's
 * atlas is two grids, large art over small ({@link Grid#SUITE_LARGE}, {@link Grid#SUITE_SMALL}).
 *
 * <p>A field is written as {@code vexelray-canvas}'s {@code DistanceField} lays one out, each distance as
 * {@code 0.5 + d / (2 · spread)} of its grid, RGBA8, an SDF's distance in every colour channel and alpha opaque.
 * A texel no cell writes reads as far outside, so a cell's margin and the unused end of a row draw nothing.
 *
 * @param rgba   the atlas, {@code width * height} RGBA8 texels, top row first
 * @param width  texels across
 * @param height texels down
 * @param grids  the grids, top to bottom
 * @param tops   the first row of each grid's cells, in texels from the top
 * @param cells  per entry, per grid: the entry's cell on that grid, counted along the rows; -1 where it has none
 */
public record GridAtlas(byte[] rgba, int width, int height, List<Grid> grids, int[] tops, int[][] cells) {

    /** {@code maxImageDimension2D}'s required minimum in the Vulkan spec: every device takes an atlas this size. */
    public static final int VULKAN_GUARANTEED_DIMENSION = 4096;

    /**
     * One grid: shapes {@code inner} texels square with {@code spread} of margin round each, as far as the distance
     * runs on. The icon sets use a quarter of the shape: as far as a glow or a shadow reaches.
     */
    public record Grid(int inner, int spread) {

        /** The suite's large icons: 64 texels and 16 round. */
        public static final Grid SUITE_LARGE = new Grid(64, 16);
        /** The suite's small icons: 48 texels and 12 round. */
        public static final Grid SUITE_SMALL = new Grid(48, 12);

        public Grid {
            if (inner <= 0 || spread < 0) {
                throw new IllegalArgumentException("a grid of " + inner + " + " + spread + " texels");
            }
        }

        /** A cell's side: the shape's square and its margin each side. */
        public int cell() {
            return inner + 2 * spread;
        }

        /** The distance a byte reaches across, as the field is written: {@code 2 · spread}. */
        public double range() {
            return 2.0 * Math.max(1, spread);
        }
    }

    /**
     * One entry: a name, and its field on each grid, in the grids' order, null where it has none. A field is a cell
     * square, as {@code ArtField.of(art, kind, grid.inner(), grid.inner(), grid.spread())} makes it.
     */
    public record Entry(String name, List<Field> fields) {

        public Entry {
            fields = java.util.Collections.unmodifiableList(new ArrayList<>(fields));
        }

        public static Entry of(String name, Field... fields) {
            return new Entry(name, Arrays.asList(fields));
        }
    }

    /** The first row of grid {@code g}'s cells. */
    public int top(int g) {
        return tops[g];
    }

    /** Cells across a row of grid {@code g}. */
    public int columns(int g) {
        return width / grids.get(g).cell();
    }

    /** The top-left texel of {@code cell} on grid {@code g}: {@code {x, y}}. */
    public int[] origin(int g, int cell) {
        int side = grids.get(g).cell();
        return new int[]{(cell % columns(g)) * side, tops[g] + (cell / columns(g)) * side};
    }

    /** The atlas as {@link AtlasPixels}, to write or to upload. */
    public AtlasPixels pixels() {
        return AtlasPixels.rgba(width, height, rgba);
    }

    /**
     * {@code entries} packed on {@code grids}, {@code width} texels across, or, if it is null, as near square as fits
     * ({@link #width(List, int[])}). Every field must be the same kind, and a cell of its grid square.
     */
    public static GridAtlas bake(List<Entry> entries, List<Grid> grids, Integer width) {
        int n = grids.size();
        int[] counts = new int[n];
        FieldKind kind = null;
        for (Entry e : entries) {
            if (e.fields.size() != n) {
                throw new IllegalArgumentException("'" + e.name + "' has " + e.fields.size() + " fields for " + n
                        + " grids");
            }
            for (int g = 0; g < n; g++) {
                Field f = e.fields.get(g);
                if (f == null) {
                    continue;
                }
                int side = grids.get(g).cell();
                if (f.width() != side || f.height() != side) {
                    throw new IllegalArgumentException("'" + e.name + "' is " + f.width() + " x " + f.height()
                            + " in a cell of " + side);
                }
                if (kind != null && f.kind() != kind) {
                    throw new IllegalArgumentException("'" + e.name + "' is " + f.kind().key + " in an atlas of "
                            + kind.key);
                }
                kind = f.kind();
                counts[g]++;
            }
        }
        int w = width != null ? width : width(grids, counts);
        for (int g = 0; g < n; g++) {
            if (counts[g] > 0 && w < grids.get(g).cell()) {
                throw new IllegalArgumentException("an atlas " + w + " texels across has no room for a cell of "
                        + grids.get(g).cell());
            }
        }
        int[] tops = new int[n];
        int h = 0;
        for (int g = 0; g < n; g++) {
            tops[g] = h;
            h += rows(counts[g], grids.get(g), w) * grids.get(g).cell();
        }
        int height = Math.max(1, h);

        byte[] rgba = new byte[w * height * 4];
        // far outside: nought in every distance; alpha opaque, unless it is a distance too (an MTSDF's)
        byte outsideAlpha = kind == FieldKind.MTSDF ? 0 : (byte) 255;
        for (int i = 3; i < rgba.length; i += 4) {
            rgba[i] = outsideAlpha;
        }
        int[][] cells = new int[entries.size()][n];
        int[] next = new int[n];
        GridAtlas atlas = new GridAtlas(rgba, w, height, List.copyOf(grids), tops, cells);
        for (int i = 0; i < entries.size(); i++) {
            for (int g = 0; g < n; g++) {
                Field f = entries.get(i).fields.get(g);
                if (f == null) {
                    cells[i][g] = -1;
                    continue;
                }
                int c = next[g]++;
                cells[i][g] = c;
                int[] o = atlas.origin(g, c);
                blit(rgba, w, Encoding.rgba(f, grids.get(g).range()), f.width(), o[0], o[1]);
            }
        }
        return atlas;
    }

    /**
     * A width for {@code counts[g]} cells on each grid: of every row width up to
     * {@value #VULKAN_GUARANTEED_DIMENSION} that is a whole number of some grid's cells, the one whose atlas has the
     * shortest longest side, and of those the least texture. Square-ish for a large set; for a set of a few, as little
     * as holds them, rather than a row of empty cells.
     */
    public static int width(List<Grid> grids, int[] counts) {
        int least = 1;
        for (int g = 0; g < grids.size(); g++) {
            if (counts[g] > 0) {
                least = Math.max(least, grids.get(g).cell());
            }
        }
        int best = least;
        long bestSide = Long.MAX_VALUE;
        long bestArea = Long.MAX_VALUE;
        for (int w = least; w <= VULKAN_GUARANTEED_DIMENSION; w++) {
            boolean whole = false;
            for (Grid g : grids) {
                whole |= w % g.cell() == 0;
            }
            if (!whole) {
                continue;
            }
            long h = 0;
            for (int g = 0; g < grids.size(); g++) {
                h += (long) rows(counts[g], grids.get(g), w) * grids.get(g).cell();
            }
            long side = Math.max(w, h);
            long area = w * h;
            if (side < bestSide || side == bestSide && area < bestArea) {
                best = w;
                bestSide = side;
                bestArea = area;
            }
        }
        return best;
    }

    /** Rows of grid {@code g} that {@code n} cells take at width {@code w}; none for none, however narrow the row. */
    private static int rows(int n, Grid g, int w) {
        int per = w / g.cell();
        return n == 0 ? 0 : (n + per - 1) / per;
    }

    /** Whether the atlas fits {@value #VULKAN_GUARANTEED_DIMENSION} texels each way, as every Vulkan device takes. */
    public boolean fitsEveryDevice() {
        return width <= VULKAN_GUARANTEED_DIMENSION && height <= VULKAN_GUARANTEED_DIMENSION;
    }

    private static void blit(byte[] atlas, int width, byte[] cell, int side, int x0, int y0) {
        for (int y = 0; y < side; y++) {
            System.arraycopy(cell, y * side * 4, atlas, ((y0 + y) * width + x0) * 4, side * 4);
        }
    }
}
