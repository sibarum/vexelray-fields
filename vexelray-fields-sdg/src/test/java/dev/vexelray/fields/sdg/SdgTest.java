package dev.vexelray.fields.sdg;

import dev.vexelray.fields.art.Raster;
import dev.vexelray.fields.art.Svg;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SdgTest {

    /** A small landscape badge: a gradient, flat fills, a star, a stroke, rounded corners. */
    static final String BADGE = """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100">
              <defs>
                <linearGradient id="sky" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0" stop-color="#2b5fd9"/><stop offset="1" stop-color="#9fd3ff"/>
                </linearGradient>
              </defs>
              <rect x="4" y="4" width="92" height="92" rx="14" fill="url(#sky)"/>
              <circle cx="68" cy="34" r="16" fill="#ffcc33"/>
              <path d="M4 74 L30 48 L50 66 L66 54 L96 80 L96 82 Q96 96 82 96 L18 96 Q4 96 4 82 Z" fill="#2f8f4e"/>
              <path d="M10 88 C30 70 60 98 90 80" fill="none" stroke="#173d22" stroke-width="3"
                    stroke-linecap="round"/>
              <polygon points="22,30 26,40 37,40 28,46 31,57 22,50 13,57 16,46 7,40 18,40" fill="#ffffff"/>
            </svg>
            """;

    static final int SMALL = 64, LARGE = 512;

    /** A traced drawing and one from an SVG, through both forms (the format's own tests are in its module). */
    @Test
    void aFileReadsBackAsItWasWritten() {
        Svg svg = Svg.parse(BADGE);
        for (Sdg sdg : new Sdg[]{Trace.trace(svg.render(SMALL, SMALL, 4), Trace.Options.DEFAULT),
                SvgToSdg.convert(svg, SvgToSdg.Options.across(svg, SMALL))}) {
            byte[] drawn = SdgRender.render(sdg, 128, 128).rgba();
            for (Sdg back : new Sdg[]{SdgFile.read(SdgFile.bytes(sdg)),
                    SdgFile.read(SdgFile.xml(sdg).getBytes(StandardCharsets.UTF_8))}) {
                assertEquals(sdg.width(), back.width());
                assertEquals(sdg.height(), back.height());
                assertEquals(sdg.layers(), back.layers());
                assertEquals(sdg.pages().size(), back.pages().size());
                for (int i = 0; i < sdg.pages().size(); i++) {
                    assertArrayEquals(sdg.pages().get(i).rgba(), back.pages().get(i).rgba());
                }
                assertArrayEquals(drawn, SdgRender.render(back, 128, 128).rgba());
            }
        }
    }

    @Test
    void anSvgKeptAsLayersDrawsAsTheSvgDoes() {
        Svg svg = Svg.parse(BADGE);
        Sdg sdg = SvgToSdg.convert(svg, SvgToSdg.Options.across(svg, SMALL));
        assertEquals(5, sdg.layers().size());   // the frame, the sun, the hills, the path's stroke, the star
        assertTrue(sdg.layers().stream().anyMatch(l -> l.paint() instanceof Sdg.Paint.Sampled), "the gradient");
        assertTrue(sdg.layers().stream().anyMatch(l -> l.stencil().kind().equals(Sdg.Stencil.MSDF)), "fills");
        assertTrue(sdg.layers().stream().anyMatch(l -> l.stencil().kind().equals(Sdg.Stencil.SDF)), "the stroke");
        Raster truth = svg.render(LARGE, LARGE, 4);
        Error e = Error.of(truth, SdgRender.render(sdg, LARGE, LARGE));
        assertTrue(e.edges < 12, e.toString());
    }

    @Test
    void groupOpacityIsRefusedRatherThanDrawnWrong() {
        Svg svg = Svg.parse("""
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 10 10">
                  <g opacity="0.5"><rect width="6" height="6"/><rect x="3" y="3" width="6" height="6"/></g>
                </svg>""");
        assertThrows(IllegalArgumentException.class, () -> SvgToSdg.convert(svg, new SvgToSdg.Options(4, 4)));
    }

    /**
     * The point of the format: a small picture blown up eight times. Bilinear filtering blurs every edge across eight
     * pixels; the traced drawing keeps them sharp, and is nearer the true drawing everywhere, and far nearer at edges.
     */
    @Test
    void aTracedPictureMagnifiesSharp() throws IOException {
        Svg svg = Svg.parse(BADGE);
        Raster small = svg.render(SMALL, SMALL, 4);
        Raster truth = svg.render(LARGE, LARGE, 4);

        Sdg traced = Trace.trace(small, Trace.Options.DEFAULT);
        Raster fromTrace = SdgRender.render(traced, LARGE, LARGE);
        Raster bilinear = bilinear(small, LARGE, LARGE);

        Error eb = Error.of(truth, bilinear), et = Error.of(truth, fromTrace);
        assertTrue(et.edges < eb.edges * 0.7, "traced " + et + " against bilinear " + eb);
        assertTrue(et.all < eb.all, "traced " + et + " against bilinear " + eb);

        // and no seam: wherever the truth is opaque a pixel of the small picture round about, between regions too,
        // so is the drawing. (Its outer edge may sit a little off, as any edge may; it is not see-through within.)
        int holes = 0, r = LARGE / SMALL;
        for (int y = r; y < LARGE - r; y++) {
            for (int x = r; x < LARGE - r; x++) {
                boolean solid = true;
                for (int dy = -r; dy <= r && solid; dy++) {
                    for (int dx = -r; dx <= r && solid; dx++) {
                        solid = (truth.rgba()[((y + dy) * LARGE + x + dx) * 4 + 3] & 0xFF) == 255;
                    }
                }
                if (solid && (fromTrace.rgba()[(y * LARGE + x) * 4 + 3] & 0xFF) < 255) {
                    holes++;
                }
            }
        }
        assertEquals(0, holes, "pixels see-through inside the drawing");
    }

    /** Prints the comparison the README quotes, and writes the pictures to {@code target/sdg}. */
    @Test
    void measure() throws IOException {
        Svg svg = Svg.parse(BADGE);
        Raster small = svg.render(SMALL, SMALL, 4);
        Raster truth = svg.render(LARGE, LARGE, 4);
        Path dir = Path.of("target", "sdg");
        Files.createDirectories(dir);

        Object[][] rows = {
                {"bilinear, the 64 px picture", bilinear(small, LARGE, LARGE), small.rgba().length},
                {"traced, 8 colours", Trace.trace(small, Trace.Options.DEFAULT), 0},
                {"traced, 8 colours, 2 texels a pixel", Trace.trace(small, Trace.Options.DEFAULT.withTexels(2)), 0},
                {"traced, 4 colours", Trace.trace(small, Trace.Options.DEFAULT.withColours(4)), 0},
                {"traced, 16 colours", Trace.trace(small, Trace.Options.DEFAULT.withColours(16)), 0},
                {"from the SVG, 64 texels", SvgToSdg.convert(svg, SvgToSdg.Options.across(svg, SMALL)), 0},
        };
        System.out.printf(Locale.ROOT, "%-40s %8s %8s %8s %7s %9s %9s%n", "at 8x", "all", "edges", "flat",
                "layers", ".sdgb", ".sdg");
        for (Object[] row : rows) {
            Raster drawn;
            String layers = "", compiled, xml = "";
            String name = ((String) row[0]).replaceAll("[^a-z0-9]+", "-");
            if (row[1] instanceof Sdg sdg) {
                drawn = SdgRender.render(sdg, LARGE, LARGE);
                layers = Integer.toString(sdg.layers().size());
                compiled = SdgFile.bytes(sdg).length + " B";
                xml = SdgFile.xml(sdg).getBytes(StandardCharsets.UTF_8).length + " B";
                SdgFile.writeWithPagesBeside(sdg, dir.resolve(name + ".sdg"));
            } else {
                drawn = (Raster) row[1];
                compiled = png(small).length + " B png";
            }
            Error e = Error.of(truth, drawn);
            System.out.printf(Locale.ROOT, "%-40s %8.2f %8.2f %8.2f %7s %9s %9s%n", row[0], e.all, e.edges, e.flat,
                    layers, compiled, xml);
            Files.write(dir.resolve(name + ".png"), png(drawn));
        }
        Files.write(dir.resolve("truth.png"), png(truth));
        Files.write(dir.resolve("small.png"), png(small));
    }

    // ------------------------------------------------------------------------------------------------ helpers

    /**
     * Mean absolute error per channel, of 255, each picture laid over white: over every pixel, over the pixels at an
     * edge of the truth (some channel differs from a neighbour's by more than 24), and over the rest.
     */
    record Error(double all, double edges, double flat) {

        static Error of(Raster truth, Raster drawn) {
            int w = truth.width(), h = truth.height();
            float[] t = overWhite(truth), d = overWhite(drawn);
            double all = 0, edges = 0, flat = 0;
            int ne = 0, nf = 0;
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int i = y * w + x;
                    double e = 0;
                    for (int c = 0; c < 3; c++) {
                        e += Math.abs(t[i * 3 + c] - d[i * 3 + c]);
                    }
                    e /= 3;
                    all += e;
                    if (edge(t, w, h, x, y)) {
                        edges += e;
                        ne++;
                    } else {
                        flat += e;
                        nf++;
                    }
                }
            }
            return new Error(all / (w * h), edges / Math.max(1, ne), flat / Math.max(1, nf));
        }

        private static boolean edge(float[] t, int w, int h, int x, int y) {
            int i = y * w + x;
            int[][] n = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] d : n) {
                int nx = x + d[0], ny = y + d[1];
                if (nx < 0 || ny < 0 || nx >= w || ny >= h) {
                    continue;
                }
                int j = ny * w + nx;
                for (int c = 0; c < 3; c++) {
                    if (Math.abs(t[i * 3 + c] - t[j * 3 + c]) > 24) {
                        return true;
                    }
                }
            }
            return false;
        }

        private static float[] overWhite(Raster r) {
            byte[] p = r.rgba();
            float[] out = new float[r.width() * r.height() * 3];
            for (int i = 0; i < r.width() * r.height(); i++) {
                float a = (p[i * 4 + 3] & 0xFF) / 255f;
                for (int c = 0; c < 3; c++) {
                    out[i * 3 + c] = (p[i * 4 + c] & 0xFF) * a + 255 * (1 - a);
                }
            }
            return out;
        }

        @Override
        public String toString() {
            return String.format(Locale.ROOT, "all %.2f, edges %.2f, flat %.2f", all, edges, flat);
        }
    }

    /** What a GPU does with the small picture: read between its pixels, premultiplied, clamped at its edge. */
    static Raster bilinear(Raster r, int width, int height) {
        int w = r.width(), h = r.height();
        byte[] in = r.rgba();
        byte[] out = new byte[width * height * 4];
        for (int y = 0; y < height; y++) {
            double sy = Math.clamp((y + 0.5) * h / height - 0.5, 0, h - 1);
            int y0 = (int) sy, y1 = Math.min(y0 + 1, h - 1);
            double ty = sy - y0;
            for (int x = 0; x < width; x++) {
                double sx = Math.clamp((x + 0.5) * w / width - 0.5, 0, w - 1);
                int x0 = (int) sx, x1 = Math.min(x0 + 1, w - 1);
                double tx = sx - x0;
                double[] acc = new double[4];
                int[][] at = {{x0, y0}, {x1, y0}, {x0, y1}, {x1, y1}};
                double[] wt = {(1 - tx) * (1 - ty), tx * (1 - ty), (1 - tx) * ty, tx * ty};
                for (int k = 0; k < 4; k++) {
                    int o = (at[k][1] * w + at[k][0]) * 4;
                    double a = (in[o + 3] & 0xFF) / 255.0;
                    for (int c = 0; c < 3; c++) {
                        acc[c] += wt[k] * (in[o + c] & 0xFF) * a;
                    }
                    acc[3] += wt[k] * a;
                }
                int o = (y * width + x) * 4;
                for (int c = 0; c < 3; c++) {
                    out[o + c] = (byte) Math.round(acc[3] > 0 ? acc[c] / acc[3] : 0);
                }
                out[o + 3] = (byte) Math.round(acc[3] * 255);
            }
        }
        return new Raster(width, height, out);
    }

    static byte[] png(Raster r) throws IOException {
        BufferedImage img = new BufferedImage(r.width(), r.height(), BufferedImage.TYPE_INT_ARGB);
        byte[] p = r.rgba();
        for (int i = 0; i < r.width() * r.height(); i++) {
            img.setRGB(i % r.width(), i / r.width(), (p[i * 4 + 3] & 0xFF) << 24 | (p[i * 4] & 0xFF) << 16
                    | (p[i * 4 + 1] & 0xFF) << 8 | (p[i * 4 + 2] & 0xFF));
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }
}
