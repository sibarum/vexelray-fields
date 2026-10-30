package dev.vexelray.fields.atlas;

import dev.vexelray.fields.Field;
import dev.vexelray.fields.FieldKind;
import dev.vexelray.fields.art.ArtField;
import dev.vexelray.fields.art.Channel;
import dev.vexelray.fields.art.Raster;
import dev.vexelray.fields.art.Svg;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A mark as two layers that line up, as {@code vexelray-gui}'s pair bake makes one: its <b>shape</b> as a distance
 * field, for effects that grow, glow and light it, and its <b>colour</b>, the drawing itself, for the frame where it
 * rests. Each atlas holds the one field and the colour at one size, side by side, so a shader reads both from the
 * texture it has bound. {@link #descriptor()} is the {@code .pair} file the run time reads beside them.
 *
 * <p><b>The field is in the MTSDF layout at two ranges.</b> Alpha is the true distance to the shape's edge over the
 * whole margin; red, green and blue are the same distance over {@value #NEAR} of that range, eight times as precise
 * where precision shows, at the edge. One byte cannot be both: a margin of sixty pixels in 255 steps is a step every
 * quarter pixel at one scale and every pixel at four. The three near channels are equal (the median of three equal
 * values is the value), so a multi-channel field drawn from outlines drops in later without a reader changing.
 *
 * <p><b>The colour is premultiplied</b>, so its bilinear read never darkens toward a transparent neighbour, and has a
 * texel of nothing round it, so a read at its edge never takes the field's. It is drawn at {@value #SAMPLES} ×
 * {@value #SAMPLES} samples a pixel.
 *
 * <p>A part of the drawing can be <b>lifted</b>: left out of the shape and kept in the colour, with its place
 * recorded, for a part the application draws itself on the way in and that lands where the drawing has it.
 *
 * @param field    the field's shape square, in texels
 * @param spread   the margin round it, in texels
 * @param atlases  one per size the colour is drawn at
 * @param lift     where the lifted part is, or null
 */
public record ArtPair(int field, int spread, List<Atlas> atlases, Lift lift) {

    /** The share of the far range the near channels span; {@code MtsdfStyle.BAKED} in vexelray-shadelets. */
    public static final float NEAR = 0.125f;

    /** Texels between the field's cell and the colour's, so neither is filtered into the other. */
    public static final int GUTTER = 2;

    /** Samples a side for each pixel of the colour. */
    public static final int SAMPLES = 4;

    /** The side a lifted part is measured at. */
    private static final int MEASURE = 512;

    /** One atlas: the field at {@code (0, 0)}, the colour square {@code size} across at {@code (colourX, colourY)}. */
    public record Atlas(int size, int width, int height, int colourX, int colourY, byte[] rgba) {

        public AtlasPixels pixels() {
            return AtlasPixels.rgba(width, height, rgba);
        }
    }

    /** Where a lifted part sits: its centre and radius, as shares of the drawing's square. */
    public record Lift(float x, float y, float radius) {
    }

    public ArtPair {
        atlases = List.copyOf(atlases);
    }

    /** The field cell's side: the shape's square and its margin each side. */
    public int fieldSide() {
        return field + 2 * spread;
    }

    /**
     * {@code svg} baked: its field {@code field} texels square with {@code margin} of that each side, and its colour
     * at each of {@code sizes}, with the top-level element {@code lift} names (or none, if null) kept out of the field.
     */
    public static ArtPair bake(Svg svg, int field, float margin, int[] sizes, String lift) {
        if (field <= 0 || margin < 0f || sizes.length == 0) {
            throw new IllegalArgumentException("a pair of a " + field + " texel field, " + margin + " margin, "
                    + sizes.length + " sizes");
        }
        int spread = Math.round(margin * field);
        Svg shape = svg;
        Lift lifted = null;
        if (lift != null && !lift.isBlank()) {
            lifted = measure(svg.only(lift.strip()));
            shape = svg.without(lift.strip());
        }

        Field d = ArtField.of(shape, Channel.ALPHA, false, FieldKind.SDF, field, field, spread);
        int fside = field + 2 * spread;
        byte[] cell = new byte[fside * fside * 4];
        float far = 2f * Math.max(1, spread);
        float near = far * NEAR;
        float[] data = d.data();
        for (int i = 0; i < data.length; i++) {
            byte n = unit(0.5f + data[i] / near);
            cell[i * 4] = n;
            cell[i * 4 + 1] = n;
            cell[i * 4 + 2] = n;
            cell[i * 4 + 3] = unit(0.5f + data[i] / far);
        }

        List<Atlas> atlases = new ArrayList<>();
        for (int size : sizes) {
            byte[] colour = premultiplied(svg.render(size, size, SAMPLES).rgba());
            int cx = fside + GUTTER + 1;
            int cy = 1;
            int w = cx + size + 1;
            int h = Math.max(fside, size + 2);
            byte[] rgba = new byte[w * h * 4];
            for (int y = 0; y < fside; y++) {
                System.arraycopy(cell, y * fside * 4, rgba, y * w * 4, fside * 4);
            }
            for (int y = 0; y < size; y++) {
                System.arraycopy(colour, y * size * 4, rgba, ((cy + y) * w + cx) * 4, size * 4);
            }
            atlases.add(new Atlas(size, w, h, cx, cy, rgba));
        }
        return new ArtPair(field, spread, atlases, lifted);
    }

    /**
     * The pair's description, which the run time reads beside its atlases: the field's square, margin and near share,
     * and per size its atlas's dimensions and where the colour square is in it; then what was lifted, if anything.
     */
    public String descriptor() {
        StringBuilder s = new StringBuilder();
        s.append("# An art pair: a field and a colour layer per size. Written by vexelray-fields-atlas.\n");
        s.append("format=1\n");
        s.append("field=").append(field).append('\n');
        s.append("spread=").append(spread).append('\n');
        s.append("near=").append(NEAR).append('\n');
        StringBuilder sizes = new StringBuilder();
        for (Atlas a : atlases) {
            sizes.append(sizes.isEmpty() ? "" : " ").append(a.size());
        }
        s.append("sizes=").append(sizes).append('\n');
        for (Atlas a : atlases) {
            s.append("atlas.").append(a.size()).append('=').append(a.width()).append(' ').append(a.height())
                    .append(' ').append(a.colourX()).append(' ').append(a.colourY()).append('\n');
        }
        if (lift != null) {
            s.append(String.format(Locale.ROOT, "lift=%.6f %.6f %.6f%n", lift.x(), lift.y(), lift.radius()));
        }
        return s.toString();
    }

    private static byte unit(float v) {
        return (byte) Math.round(Math.clamp(v, 0f, 1f) * 255f);
    }

    private static byte[] premultiplied(byte[] straight) {
        byte[] out = new byte[straight.length];
        for (int i = 0; i < straight.length; i += 4) {
            int a = straight[i + 3] & 0xFF;
            for (int c = 0; c < 3; c++) {
                out[i + c] = (byte) Math.round((straight[i + c] & 0xFF) * a / 255f);
            }
            out[i + 3] = (byte) a;
        }
        return out;
    }

    /** The centre and radius of what {@code part} covers, as shares of its square: the bounds of its alpha. */
    private static Lift measure(Svg part) {
        Raster r = part.render(MEASURE, MEASURE);
        byte[] rgba = r.rgba();
        int x0 = MEASURE, y0 = MEASURE, x1 = -1, y1 = -1;
        for (int y = 0; y < MEASURE; y++) {
            for (int x = 0; x < MEASURE; x++) {
                if ((rgba[(y * MEASURE + x) * 4 + 3] & 0xFF) >= 128) {
                    x0 = Math.min(x0, x);
                    y0 = Math.min(y0, y);
                    x1 = Math.max(x1, x);
                    y1 = Math.max(y1, y);
                }
            }
        }
        if (x1 < 0) {
            throw new IllegalArgumentException("the lifted part covers nothing");
        }
        float cx = (x0 + x1 + 1) / 2f / MEASURE;
        float cy = (y0 + y1 + 1) / 2f / MEASURE;
        float r2 = Math.max(x1 + 1 - x0, y1 + 1 - y0) / 2f / MEASURE;
        return new Lift(cx, cy, r2);
    }
}
