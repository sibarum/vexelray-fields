package dev.vexelray.fields.art;

import dev.vexelray.fields.Field;
import dev.vexelray.fields.FieldKind;
import dev.vexelray.fields.Framing;
import dev.vexelray.fields.msdf.Msdf;
import dev.vexelray.fields.shape.PathData;
import dev.vexelray.fields.shape.Vec2;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArtFieldTest {

    static final String STAR = "M50 0 L61 35 L98 35 L68 57 L79 91 L50 70 L21 91 L32 57 L2 35 L39 35 Z";

    static Svg resource(String name) throws IOException {
        try (InputStream in = ArtFieldTest.class.getResourceAsStream(name)) {
            return Svg.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    /** A raster {@code w × h}, each pixel {@code rgba} where {@code in} says and {@code out} elsewhere. */
    static Raster raster(int w, int h, int in, int out, java.util.function.BiPredicate<Double, Double> shape) {
        byte[] px = new byte[w * h * 4];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int c = shape.test(x + 0.5, y + 0.5) ? in : out;
                int o = (y * w + x) * 4;
                px[o] = (byte) (c >>> 24);
                px[o + 1] = (byte) (c >>> 16);
                px[o + 2] = (byte) (c >>> 8);
                px[o + 3] = (byte) c;
            }
        }
        return new Raster(w, h, px);
    }

    /** The worst error of {@code f} against a disc of radius {@code r} texels round {@code c}, within {@code reach}. */
    static double worstAgainstDisc(Field f, int channel, Vec2 c, double r, double reach) {
        double worst = 0;
        for (int y = 0; y < f.height(); y++) {
            for (int x = 0; x < f.width(); x++) {
                double expected = r - new Vec2(x + 0.5, y + 0.5).minus(c).length();
                if (Math.abs(expected) < reach) {
                    worst = Math.max(worst, Math.abs(expected - f.get(x, y, channel)));
                }
            }
        }
        return worst;
    }

    @Test
    void anSvgOfFillsIsReadByItsOutlines() {
        Svg svg = Svg.parse("<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 100 100'>"
                + "<path d='" + STAR + "' fill='#123456'/></svg>");
        assertTrue(svg.outline().isPresent());
        Field art = ArtField.of(svg, FieldKind.MTSDF, 32, 32, 4);
        Framing framing = Framing.fit(svg.viewBox(), 40, 40, 4, false);
        Field direct = Msdf.generateMtsdf(PathData.parse(STAR), framing, 40, 40);
        assertArrayEquals(direct.data(), art.data());
    }

    @Test
    void overlappingFillsAreTheirUnionWithNoSeam() {
        // two squares overlapping in the middle, wound opposite ways: a union, not a hole where they meet, and the
        // edges hidden inside it no part of the distance, as an outline's would be
        Svg svg = Svg.parse("<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 30 20'>"
                + "<path d='M0 0 H20 V20 H0 Z'/><path d='M10 0 V20 H30 V0 Z'/></svg>");
        assertFalse(svg.outline().isPresent());
        Field f = ArtField.of(svg, FieldKind.SDF, 30, 20, 2);
        // texel (17, 12) is (15.5, 10.5) in the drawing: 9.5 from the union's nearest edge
        assertEquals(9.5, f.get(17, 12, 0), 0.1);
        // apart, as they are drawn, the outlines are read
        assertTrue(Svg.parse("<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 30 20'>"
                + "<path d='M0 0 H10 V20 H0 Z'/><path d='M12 0 V20 H30 V0 Z'/></svg>").outline().isPresent());
    }

    @Test
    void aStrokedRingIsItsDistance() {
        // a ring of radius 10, 4 wide, in a 32-unit drawing at one texel a unit: the field is 2 - |r - 10|
        Svg svg = Svg.parse("<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 32 32'>"
                + "<circle cx='16' cy='16' r='10' fill='none' stroke='black' stroke-width='4'/></svg>");
        assertFalse(svg.outline().isPresent());
        Field f = ArtField.of(svg, FieldKind.SDF, 32, 32, 3);
        double worst = 0;
        for (int y = 0; y < f.height(); y++) {
            for (int x = 0; x < f.width(); x++) {
                double r = new Vec2(x + 0.5 - 3, y + 0.5 - 3).minus(new Vec2(16, 16)).length();
                double expected = 2 - Math.abs(r - 10);
                // off the ring's middle, where averaging fine pixels down blunts the ridge by a quarter texel
                if (Math.abs(expected) < 1.5) {
                    worst = Math.max(worst, Math.abs(expected - f.get(x, y, 0)));
                }
            }
        }
        assertTrue(worst < 0.15, "worst error " + worst + " texels");
    }

    @Test
    void capsJoinsAndDashesAreSvgs() {
        Raster butt = Svg.parse("<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 20 10'>"
                + "<path d='M5 5 H15' stroke='#000' stroke-width='4'/></svg>").render(20, 10);
        Raster round = Svg.parse("<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 20 10'>"
                + "<path d='M5 5 H15' stroke='#000' stroke-width='4' stroke-linecap='round'/></svg>").render(20, 10);
        boolean[] b = butt.mask(Channel.ALPHA, false, 20, 10);
        boolean[] r = round.mask(Channel.ALPHA, false, 20, 10);
        assertTrue(b[5 * 20 + 5] && b[5 * 20 + 14] && b[3 * 20 + 10] && !b[2 * 20 + 10]);
        assertFalse(b[5 * 20 + 4]);   // a butt ends where the line does
        assertTrue(r[5 * 20 + 3]);    // a round cap reaches on by the half-width

        // a miter at a right angle reaches the corner; a bevel cuts it off
        String corner = "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 20 20'><path d='M2 10 H10 V18' "
                + "fill='none' stroke='#000' stroke-width='4' stroke-linejoin='%s'/></svg>";
        boolean[] miter = Svg.parse(corner.formatted("miter")).render(20, 20).mask(Channel.ALPHA, false, 20, 20);
        boolean[] bevel = Svg.parse(corner.formatted("bevel")).render(20, 20).mask(Channel.ALPHA, false, 20, 20);
        assertTrue(miter[8 * 20 + 11]);
        assertFalse(bevel[8 * 20 + 11]);

        // dashes of 2 on, 2 off along 16: four dashes, the pixels between them clear
        boolean[] dashed = Svg.parse("<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 20 4'>"
                + "<path d='M2 2 H18' stroke='#000' stroke-width='2' stroke-dasharray='2'/></svg>")
                .render(20, 4).mask(Channel.ALPHA, false, 20, 4);
        int runs = 0;
        for (int x = 0; x < 20; x++) {
            if (dashed[2 * 20 + x] && (x == 0 || !dashed[2 * 20 + x - 1])) {
                runs++;
            }
        }
        assertEquals(4, runs);
    }

    @Test
    void anIconMaskComesOutAsItIsDrawn() throws IOException {
        // a disc of radius 24 round (32, 32) with a check knocked out of it, drawn as a mask over currentColor
        Svg svg = resource("accept-mask.svg");
        assertFalse(svg.outline().isPresent());
        Field f = ArtField.of(svg, FieldKind.SDF, 64, 64, 8);
        assertTrue(f.get(8 + 28, 8 + 48, 0) > 2, "inside the disc, below the check");
        assertTrue(f.get(8 + 28, 8 + 40, 0) < 0, "on the check, knocked out");
        assertTrue(f.get(8 + 4, 8 + 4, 0) < -10, "outside the disc");
        assertTrue(worstAgainstDisc(f, 0, new Vec2(40, 40), 24, 1.5) < 2.0);

        // dashes, nested transforms and a rotation about a point: the axis, dashed 6 on and 5 off in pixels, turned to
        // lie across at y 32 and run from x 58 leftward, so its dashes are 52..58, 41..47, 30..36, 19..25 and 8..14
        Field mirror = ArtField.of(resource("mirror-mask.svg"), FieldKind.SDF, 64, 64, 8);
        assertEquals(1.5, mirror.get(8 + 32, 8 + 32, 0), 0.2, "(32.5, 32.5), in the dash 30..36 by 30..34");
        assertEquals(-1.5, mirror.get(8 + 15, 8 + 32, 0), 0.2, "(15.5, 32.5), between the dashes 8..14 and 19..25");
    }

    @Test
    void aMasksMultiChannelFieldIsItsDistanceInEveryChannel() throws IOException {
        Svg svg = resource("accept-mask.svg");
        Field sdf = ArtField.of(svg, FieldKind.SDF, 32, 32, 4);
        Field mtsdf = ArtField.of(svg, FieldKind.MTSDF, 32, 32, 4);
        assertEquals(FieldKind.MTSDF, mtsdf.kind());
        for (int y = 0; y < sdf.height(); y++) {
            for (int x = 0; x < sdf.width(); x++) {
                for (int c = 0; c < 4; c++) {
                    assertEquals(sdf.get(x, y, 0), mtsdf.get(x, y, c));
                }
                assertEquals(sdf.get(x, y, 0), mtsdf.distance(x, y));
            }
        }
    }

    @Test
    void aRastersChannelsEachMakeTheirShape() {
        // a disc of radius 8 in a 24-pixel square, drawn three ways
        java.util.function.BiPredicate<Double, Double> disc = (x, y) -> Math.hypot(x - 12, y - 12) < 8;
        Raster blackOnWhite = raster(24, 24, 0x000000FF, 0xFFFFFFFF, disc);
        Raster alphaMask = raster(24, 24, 0xFF8800FF, 0xFF880000, disc);
        Raster inRed = raster(24, 24, 0xFF0000FF, 0x00FF00FF, disc);
        // drawn at 2 texels a pixel: the disc is radius 16 round (24, 24), plus the margin
        Vec2 centre = new Vec2(24 + 4, 24 + 4);
        Field[] fields = {
                ArtField.of(blackOnWhite, Channel.AUTO, false, FieldKind.SDF, 48, 48, 4),
                ArtField.of(alphaMask, Channel.AUTO, false, FieldKind.SDF, 48, 48, 4),
                ArtField.of(inRed, Channel.RED, false, FieldKind.SDF, 48, 48, 4),
                ArtField.of(inRed, Channel.GREEN, true, FieldKind.SDF, 48, 48, 4),
        };
        for (Field f : fields) {
            assertEquals(56, f.width());
            // a disc in pixels is a staircase; read between them it is round again, to within half a pixel
            assertTrue(worstAgainstDisc(f, 0, centre, 16, 4) < 1.0, "worst " + worstAgainstDisc(f, 0, centre, 16, 4));
        }
        Field inverted = ArtField.of(alphaMask, Channel.ALPHA, true, FieldKind.SDF, 48, 48, 4);
        assertTrue(inverted.get(28, 28, 0) < 0);
        assertTrue(inverted.get(6, 6, 0) > 0);
    }

    @Test
    void aRasterIsFittedWithItsAspectKept() {
        // a 40 × 20 raster all shape, into a square: a band across the middle, half the height
        Raster wide = raster(40, 20, 0x000000FF, 0x000000FF, (x, y) -> true);
        boolean[] m = wide.mask(Channel.ALPHA, false, 40, 40);
        assertFalse(m[5 * 40 + 20]);
        assertTrue(m[15 * 40 + 20]);
        assertTrue(m[24 * 40 + 20]);
        assertFalse(m[35 * 40 + 20]);
    }

    @Test
    void whatIsNotDrawnIsRefused() {
        String[] refused = {
                "<image href='a.png' width='4' height='4'/>",
                "<pattern id='p'/><rect width='4' height='4' fill='url(#p)'/>",
                "<rect width='4' height='4' fill='url(#nothing)'/>",
                "<rect width='4' height='4' filter='url(#f)'/>",
                "<style>rect { fill: red }</style>",
        };
        for (String body : refused) {
            Svg svg = Svg.parse("<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 8 8'>" + body + "</svg>");
            assertThrows(IllegalArgumentException.class, () -> svg.render(8, 8), body);
        }
    }

    @Test
    void aGradientCoversItsShapeAndTextIsLeftOut() {
        Svg svg = Svg.parse("<svg xmlns='http://www.w3.org/2000/svg' xmlns:x='http://www.w3.org/1999/xlink' "
                + "viewBox='0 0 8 8'><defs><linearGradient id='s'><stop stop-color='#f00'/>"
                + "<stop offset='1' style='stop-color: #00f'/></linearGradient>"
                + "<linearGradient id='g' x:href='#s'/></defs>"
                + "<rect x='2' y='2' width='4' height='4' fill='url(#g)'/><text x='0' y='8'>A</text></svg>");
        assertTrue(svg.outline().isPresent(), "opaque stops: still an outline");
        Raster r = svg.render(8, 8);
        int o = (3 * 8 + 3) * 4;
        assertEquals(128, r.rgba()[o] & 0xFF);   // the stops' mean, half red and half blue
        assertEquals(128, r.rgba()[o + 2] & 0xFF);
        assertEquals(255, r.rgba()[o + 3] & 0xFF);
        assertEquals(0, r.rgba()[(7 * 8) * 4 + 3]);   // where the text would be, nothing
    }

    @Test
    void anEditorsOwnElementsArePassedOver() {
        Svg svg = Svg.parse("<svg xmlns='http://www.w3.org/2000/svg' xmlns:i='urn:editor' viewBox='0 0 8 8'>"
                + "<i:guide/><rect x='2' y='2' width='4' height='4' style='fill: #000; stroke: none'/></svg>");
        assertTrue(svg.outline().isPresent());
        assertEquals(1, svg.outline().get().contours().size());
    }
}
