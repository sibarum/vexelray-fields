package dev.vexelray.fields.sdg;

import java.util.List;

/**
 * A drawing as signed distance graphics: layers painted in order on a canvas {@code width × height} units, each a
 * {@link Stencil} saying where it is (a distance field, its edge where the distance is zero) and a {@link Paint} saying
 * what colour it is there. The edge comes from the field and stays sharp at any size; the colour comes from the paint,
 * which is one colour or a texture smooth enough to magnify. All texels are in {@link #pages}, RGBA8.
 *
 * <p>The format is in {@code docs/sdg.md}; {@link SdgFile} reads and writes it. This module holds the file alone and
 * depends on nothing. In {@code vexelray-fields-sdg}, {@code SdgRender} draws it on the CPU as a shader does, and
 * {@code SvgToSdg.convert} and {@code Trace.trace} make one.
 *
 * @param width  the canvas across, in units
 * @param height the canvas down, in units; y runs down
 * @param pages  the texels every stencil and sampled paint reads
 * @param layers back to front
 */
public record Sdg(double width, double height, List<Page> pages, List<Layer> layers) {

    /** What the stack reads today: the major version a reader must know, and the minor it was written at. */
    public static final int MAJOR = 1, MINOR = 1;

    public Sdg {
        if (!(width > 0) || !(height > 0)) {
            throw new IllegalArgumentException("a canvas " + width + " × " + height);
        }
        pages = List.copyOf(pages);
        layers = List.copyOf(layers);
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
    public record Layer(String name, Stencil stencil, Paint paint, float opacity) {

        /** Whether a reader knows how to draw it; one that does not skips it, and draws the rest. */
        public boolean known() {
            return stencil.known() && !(paint instanceof Paint.Unknown);
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
