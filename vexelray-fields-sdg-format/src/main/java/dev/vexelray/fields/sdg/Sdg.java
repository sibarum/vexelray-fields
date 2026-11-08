package dev.vexelray.fields.sdg;

import java.util.List;

/**
 * A drawing as signed distance graphics: layers painted in order on a canvas {@code width × height} units, each a
 * {@link Stencil} saying where it is (a distance field, its edge where the distance is zero) and a {@link Paint} saying
 * what colour it is there. The edge comes from the field and stays sharp at any size; the colour comes from the paint,
 * which is one colour or a texture smooth enough to magnify. All texels are in {@link #pages}, RGBA8.
 *
 * <p>The format is in {@code docs/sdg.md}; {@link SdgFile} reads and writes it, as XML ({@code .sdg}) or compiled
 * ({@code .sdgb}). This module holds the file alone and depends on nothing but {@code intrabuf-runtime}. In {@code vexelray-fields-sdg}, {@code SdgRender} draws it on the CPU as a shader does, and
 * {@code SvgToSdg.convert} and {@code Trace.trace} make one.
 *
 * @param width  the canvas across, in units
 * @param height the canvas down, in units; y runs down
 * @param pages  the texels every stencil and sampled paint reads
 * @param layers back to front
 */
public record Sdg(double width, double height, List<Page> pages, List<Layer> layers) {

    /** What the stack reads today: the major version a reader must know, and the minor it was written at. */
    public static final int MAJOR = 1, MINOR = 2;

    public Sdg {
        if (!(width > 0) || !(height > 0)) {
            throw new IllegalArgumentException("a canvas " + width + " × " + height);
        }
        pages = List.copyOf(pages);
        layers = List.copyOf(layers);
    }

    /**
     * This drawing with each layer named in {@code roles} painted in the theme role it maps to ({@code "face"} to
     * {@code "ink"}), its colour kept for a reader with no such role: how a drawing made from an SVG, whose layers are
     * named after its elements' ids, follows a theme. Only a solid paint takes a role; a name no layer has, or a layer
     * whose paint is not solid, is refused, so a role never goes quietly unapplied.
     */
    public Sdg withRoles(java.util.Map<String, String> roles) {
        java.util.Set<String> found = new java.util.HashSet<>();
        List<Layer> out = roled(layers, roles, found);
        for (String name : roles.keySet()) {
            if (!found.contains(name)) {
                throw new IllegalArgumentException("no layer named " + name + " to give the role " + roles.get(name));
            }
        }
        return new Sdg(width, height, pages, out);
    }

    /** {@code layers} with their roles, into groups and their masks as well; the names given one added to {@code found}. */
    private static List<Layer> roled(List<Layer> layers, java.util.Map<String, String> roles,
                                     java.util.Set<String> found) {
        java.util.List<Layer> out = new java.util.ArrayList<>();
        for (Layer l : layers) {
            if (l.isGroup()) {
                Group g = l.group();
                Mask m = g.mask() == null ? null : new Mask(g.mask().kind(), roled(g.mask().layers(), roles, found));
                out.add(Layer.group(l.name(), l.opacity(), new Group(roled(g.layers(), roles, found), g.clip(), m)));
                continue;
            }
            String role = roles.get(l.name());
            if (role == null) {
                out.add(l);
                continue;
            }
            if (!(l.paint() instanceof Paint.Solid s)) {
                throw new IllegalArgumentException("the layer " + l.name() + " is not painted one colour, so takes no role");
            }
            found.add(l.name());
            out.add(new Layer(l.name(), l.stencil(), s.as(role), l.opacity()));
        }
        return out;
    }

    /** RGBA8 texels, top row first. What each channel holds is up to the stencils and paints that point into it. */
    public record Page(int width, int height, byte[] rgba) {

        public Page {
            if (width <= 0 || height <= 0 || width > 65535 || height > 65535 || rgba.length != width * height * 4) {
                throw new IllegalArgumentException("a page " + width + " × " + height + " of " + rgba.length
                        + " bytes");
            }
        }
    }

    /**
     * One layer: its stencil's coverage, times its paint's alpha, times {@code opacity}, of its paint's colour, over
     * what is under it.
     */
    public record Layer(String name, Stencil stencil, Paint paint, float opacity, Group group) {

        /** A shape: a stencil and a paint. */
        public Layer(String name, Stencil stencil, Paint paint, float opacity) {
            this(name, stencil, paint, opacity, null);
        }

        /**
         * A group: {@code group}'s layers composited apart, then laid over what is under them once, at {@code opacity},
         * cut by its clip and multiplied by its mask (since 1.2, {@code docs/sdg.md} §2). Its stencil is of the kind
         * {@value Stencil#GROUP}, which a reader of 1.1 does not know, and so skips, drawing the rest.
         */
        public static Layer group(String name, float opacity, Group group) {
            return new Layer(name, Stencil.GROUP_STENCIL, new Paint.Unknown(Stencil.GROUP), opacity, group);
        }

        /** Whether it is a group rather than a shape. */
        public boolean isGroup() {
            return group != null;
        }

        /**
         * Whether a reader knows how to draw it as a shape; one that does not skips it, and draws the rest. A group is
         * not a shape: a reader that draws groups asks {@link #isGroup()} first.
         */
        public boolean known() {
            return group == null && stencil.known() && !(paint instanceof Paint.Unknown);
        }
    }

    /**
     * A group's contents: its {@code layers}, back to front, shapes and groups alike; a {@code clip}, a stencil whose
     * coverage cuts the composite, or null for none; and a {@code mask}, or null for none.
     */
    public record Group(List<Layer> layers, Stencil clip, Mask mask) {

        /** How deep groups may nest in a drawing a reader takes: past it the drawing is refused. */
        public static final int MAX_DEPTH = 16;

        public Group {
            layers = List.copyOf(layers);
        }
    }

    /**
     * A mask: {@code layers} drawn apart, the group multiplied by their {@link #LUMINANCE} (SVG's default: the Rec. 709
     * luma of their colour, times their alpha) or their {@link #ALPHA}.
     */
    public record Mask(String kind, List<Layer> layers) {

        public static final String LUMINANCE = "luminance", ALPHA = "alpha";

        public Mask {
            layers = List.copyOf(layers);
        }

        public boolean known() {
            return kind.equals(LUMINANCE) || kind.equals(ALPHA);
        }
    }

    /**
     * Where a layer is: a rectangle {@code width × height} at {@code (x, y)} of page {@code page}, holding signed
     * distances in texels, positive inside, as {@code 0.5 + d / range}. A canvas point {@code (cx, cy)} is texel
     * coordinate {@code ((cx - originX) · scale, (cy - originY) · scale)} of the rectangle, texel centres at halves;
     * past the rectangle is outside.
     *
     * @param kind    {@code "sdf"}, one distance in channel {@code channel}; or {@code "msdf"}, three in red, green
     *                and blue whose median is the distance ({@code channel} 0). Any other name is a kind this reader
     *                does not know.
     */
    public record Stencil(String kind, int page, int channel, int x, int y, int width, int height, double originX,
                          double originY, double scale, double range) {

        public static final String SDF = "sdf", MSDF = "msdf";

        /** The kind of a group layer's stencil: a place holder, holding nothing, that a reader of 1.1 skips. */
        public static final String GROUP = "group";

        /**
         * A group layer's stencil: one texel of page 0, so a reader of 1.1, which checks every stencil's rectangle
         * before it skips a kind it does not know, finds it in its page and reads the rest of the drawing.
         */
        public static final Stencil GROUP_STENCIL = new Stencil(GROUP, 0, 0, 0, 0, 1, 1, 0, 0, 1, 1);

        public boolean known() {
            return kind.equals(SDF) || kind.equals(MSDF);
        }
    }

    /** What colour a layer is where it covers. */
    public sealed interface Paint {

        /**
         * One colour, straight (not premultiplied) RGBA8 as {@code 0xRRGGBBAA}, and optionally a {@code role}: the name
         * of a colour of the reader's theme ({@code "foreground"}, {@code "accent"}, ...) to draw instead, so one
         * drawing follows a light and a dark theme. A reader with no colour of that name, or none at all, draws
         * {@code rgba}. Empty for none. Since 1.1.
         */
        record Solid(int rgba, String role) implements Paint {

            public Solid {
                if (role == null) {
                    role = "";
                }
            }

            /** A colour with no role: drawn as it is, in every theme. */
            public Solid(int rgba) {
                this(rgba, "");
            }

            public static Solid of(float r, float g, float b, float a) {
                return new Solid(byte8(r) << 24 | byte8(g) << 16 | byte8(b) << 8 | byte8(a));
            }

            /** This colour, drawn as the theme's {@code role} by a reader that has one. */
            public Solid as(String role) {
                return new Solid(rgba, role);
            }

            private static int byte8(float v) {
                return Math.round(Math.clamp(v, 0f, 1f) * 255f);
            }
        }

        /**
         * A colour texture, straight RGBA8, in a rectangle of a page, mapped onto the canvas as a {@link Stencil} is,
         * read between its texels and clamped at its edge. Its colours run on past the layer's edge, so a read near
         * the edge never takes in a colour from beyond it.
         */
        record Sampled(int page, int x, int y, int width, int height, double originX, double originY, double scale)
                implements Paint {
        }

        /** A paint of a kind this reader does not know. */
        record Unknown(String kind) implements Paint {
        }
    }
}
