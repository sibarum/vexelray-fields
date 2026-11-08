package dev.vexelray.fields.sdg;

import dev.vexelray.fields.Encoding;
import dev.vexelray.fields.Field;
import dev.vexelray.fields.FieldKind;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Layers being made, their fields still floats and their colour textures loose, packed into pages at the end. An SDF
 * takes one channel, so four share a page's area; an MSDF takes red, green and blue of a page of MSDFs; a colour texture
 * takes all four of a page of colour. Each kind is packed in shelves, tallest first, on pages no wider or taller than
 * {@value #MAX} (what every Vulkan device takes). A rectangle is read only within itself, so none needs a gutter.
 */
final class Assembly {

    static final int MAX = 4096;

    /** A layer's field, in texels, placed on the canvas by {@code originX, originY, scale}. */
    record Stencil(Field field, double originX, double originY, double scale, double range) {
    }

    /** A colour texture, straight RGBA8, placed as a stencil is. */
    record Texture(int width, int height, byte[] rgba, double originX, double originY, double scale) {
    }

    private record Pending(String name, Stencil stencil, Sdg.Paint solid, Texture texture, float opacity) {
    }

    /**
     * Where a packed stencil goes in the drawing: a {@link Shape} is the layer of pending item {@code index}; a
     * {@link Group} is a group of nodes, its clip the stencil of pending item {@code clip} (or -1 for none) and its mask
     * nodes of {@code maskKind} (or null for none).
     */
    sealed interface Node permits Shape, Group {
    }

    record Shape(int index) implements Node {
    }

    record Group(String name, float opacity, int clip, String maskKind, List<Node> mask, List<Node> children)
            implements Node {
    }

    private final double width, height;
    private final List<Pending> layers = new ArrayList<>();
    private final List<Node> root = new ArrayList<>();

    Assembly(double width, double height) {
        this.width = width;
        this.height = height;
    }

    void add(String name, Stencil stencil, Sdg.Paint.Solid solid, float opacity) {
        root.add(new Shape(pend(name, stencil, solid, null, opacity)));
    }

    void add(String name, Stencil stencil, Texture texture, float opacity) {
        root.add(new Shape(pend(name, stencil, null, texture, opacity)));
    }

    /** A stencil to pack, placed nowhere yet: its index, for a {@link Node}. Neither paint, for a clip. */
    int pend(String name, Stencil stencil, Sdg.Paint.Solid solid, Texture texture, float opacity) {
        layers.add(new Pending(name, stencil, solid, texture, opacity));
        return layers.size() - 1;
    }

    /** Every layer added, in order, packed. */
    Sdg build() {
        return build(root);
    }

    /** Every pending stencil packed, and the drawing laid out as {@code nodes} say. */
    Sdg build(List<Node> nodes) {
        List<Sdg.Page> pages = new ArrayList<>();
        int n = layers.size();
        Spot[] stencilAt = new Spot[n], textureAt = new Spot[n];

        List<Integer> sdf = new ArrayList<>(), msdf = new ArrayList<>(), colour = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Pending p = layers.get(i);
            (p.stencil.field().kind() == FieldKind.SDF ? sdf : msdf).add(i);
            if (p.texture != null) {
                colour.add(i);
            }
        }
        // SDFs: bins of one channel, four bins to a page
        List<Bin> bins = pack(sdf, i -> layers.get(i).stencil.field().width(),
                i -> layers.get(i).stencil.field().height(), 4, stencilAt);
        for (int b = 0; b < bins.size(); b += 4) {
            int pw = 0, ph = 0;
            for (int k = b; k < Math.min(b + 4, bins.size()); k++) {
                pw = Math.max(pw, bins.get(k).width);
                ph = Math.max(ph, bins.get(k).used);
            }
            byte[] rgba = new byte[pw * ph * 4];
            for (int k = b; k < Math.min(b + 4, bins.size()); k++) {
                for (int i : bins.get(k).items) {
                    Spot s = stencilAt[i];
                    stencilAt[i] = new Spot(pages.size(), k - b, s.x, s.y);
                    Stencil st = layers.get(i).stencil;
                    Field f = st.field();
                    for (int y = 0; y < f.height(); y++) {
                        for (int x = 0; x < f.width(); x++) {
                            rgba[((s.y + y) * pw + s.x + x) * 4 + (k - b)] = Encoding.encode(f.get(x, y, 0), st.range());
                        }
                    }
                }
            }
            pages.add(new Sdg.Page(pw, ph, rgba));
        }
        // MSDFs: a page each bin, red, green and blue; alpha left opaque
        for (Bin bin : pack(msdf, i -> layers.get(i).stencil.field().width(),
                i -> layers.get(i).stencil.field().height(), 1, stencilAt)) {
            byte[] rgba = new byte[bin.width * bin.used * 4];
            for (int i : bin.items) {
                Spot s = stencilAt[i];
                stencilAt[i] = new Spot(pages.size(), 0, s.x, s.y);
                Stencil st = layers.get(i).stencil;
                Field f = st.field();
                for (int y = 0; y < f.height(); y++) {
                    for (int x = 0; x < f.width(); x++) {
                        int o = ((s.y + y) * bin.width + s.x + x) * 4;
                        for (int c = 0; c < 3; c++) {
                            rgba[o + c] = Encoding.encode(f.get(x, y, c), st.range());
                        }
                        rgba[o + 3] = (byte) 255;
                    }
                }
            }
            pages.add(new Sdg.Page(bin.width, bin.used, rgba));
        }
        // colour: a page each bin
        for (Bin bin : pack(colour, i -> layers.get(i).texture.width(), i -> layers.get(i).texture.height(), 1,
                textureAt)) {
            byte[] rgba = new byte[bin.width * bin.used * 4];
            for (int i : bin.items) {
                Spot s = textureAt[i];
                textureAt[i] = new Spot(pages.size(), 0, s.x, s.y);
                Texture t = layers.get(i).texture;
                for (int y = 0; y < t.height(); y++) {
                    System.arraycopy(t.rgba(), y * t.width() * 4, rgba, ((s.y + y) * bin.width + s.x) * 4,
                            t.width() * 4);
                }
            }
            pages.add(new Sdg.Page(bin.width, bin.used, rgba));
        }

        Sdg.Stencil[] placed = new Sdg.Stencil[n];
        Sdg.Layer[] shaped = new Sdg.Layer[n];
        for (int i = 0; i < n; i++) {
            Pending p = layers.get(i);
            Field f = p.stencil.field();
            Spot s = stencilAt[i];
            Sdg.Stencil stencil = new Sdg.Stencil(f.kind() == FieldKind.SDF ? Sdg.Stencil.SDF : Sdg.Stencil.MSDF,
                    s.page, s.channel, s.x, s.y, f.width(), f.height(), p.stencil.originX(), p.stencil.originY(),
                    p.stencil.scale(), p.stencil.range());
            placed[i] = stencil;
            Sdg.Paint paint = p.solid;
            if (p.texture != null) {
                Spot t = textureAt[i];
                Texture tx = p.texture;
                paint = new Sdg.Paint.Sampled(t.page, t.x, t.y, tx.width(), tx.height(), tx.originX(), tx.originY(),
                        tx.scale());
            }
            shaped[i] = paint == null ? null : new Sdg.Layer(p.name, stencil, paint, p.opacity);
        }
        return new Sdg(width, height, pages, laid(nodes, placed, shaped));
    }

    private static List<Sdg.Layer> laid(List<Node> nodes, Sdg.Stencil[] placed, Sdg.Layer[] shaped) {
        List<Sdg.Layer> out = new ArrayList<>();
        for (Node node : nodes) {
            switch (node) {
                case Shape s -> out.add(shaped[s.index()]);
                case Group g -> out.add(Sdg.Layer.group(g.name(), g.opacity(), new Sdg.Group(
                        laid(g.children(), placed, shaped), g.clip() < 0 ? null : placed[g.clip()],
                        g.maskKind() == null ? null : new Sdg.Mask(g.maskKind(), laid(g.mask(), placed, shaped)))));
            }
        }
        return out;
    }

    private record Spot(int page, int channel, int x, int y) {
    }

    private static final class Bin {
        final int width;
        final List<Integer> items = new ArrayList<>();
        int shelfY, shelfH, cursor, used;

        Bin(int width) {
            this.width = width;
        }
    }

    /**
     * {@code items} in shelves, tallest first, into bins all {@code W} wide: {@code W} the side of a square holding
     * their area over {@code share} bins (a fifth to spare), at least the widest, and a multiple of four. Each item's
     * place in its bin goes into {@code at}, its page and channel left for the caller.
     */
    private static List<Bin> pack(List<Integer> items, java.util.function.IntUnaryOperator w,
                                  java.util.function.IntUnaryOperator h, int share, Spot[] at) {
        List<Bin> bins = new ArrayList<>();
        if (items.isEmpty()) {
            return bins;
        }
        long area = 0;
        int widest = 0;
        for (int i : items) {
            area += (long) w.applyAsInt(i) * h.applyAsInt(i);
            widest = Math.max(widest, w.applyAsInt(i));
            if (w.applyAsInt(i) > MAX || h.applyAsInt(i) > MAX) {
                throw new IllegalArgumentException("a layer " + w.applyAsInt(i) + " × " + h.applyAsInt(i)
                        + " texels, past a page's " + MAX);
            }
        }
        int side = (int) Math.ceil(Math.sqrt(area * 1.2 / share));
        int bw = Math.min(MAX, Math.max(widest, (side + 3) / 4 * 4));
        List<Integer> sorted = new ArrayList<>(items);
        sorted.sort(Comparator.comparingInt((Integer i) -> -h.applyAsInt(i)).thenComparingInt(i -> i));
        Bin bin = new Bin(bw);
        bins.add(bin);
        for (int i : sorted) {
            int iw = w.applyAsInt(i), ih = h.applyAsInt(i);
            if (bin.cursor + iw > bw) {
                bin.shelfY += bin.shelfH;
                bin.shelfH = 0;
                bin.cursor = 0;
            }
            if (bin.shelfY + ih > MAX) {
                bin = new Bin(bw);
                bins.add(bin);
            }
            at[i] = new Spot(-1, -1, bin.cursor, bin.shelfY);
            bin.items.add(i);
            bin.cursor += iw;
            bin.shelfH = Math.max(bin.shelfH, ih);
            bin.used = Math.max(bin.used, bin.shelfY + ih);
        }
        return bins;
    }
}
