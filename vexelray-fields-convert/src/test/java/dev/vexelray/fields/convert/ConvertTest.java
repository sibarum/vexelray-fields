package dev.vexelray.fields.convert;

import dev.vexelray.fields.Coverage;
import dev.vexelray.fields.Field;
import dev.vexelray.fields.FieldKind;
import dev.vexelray.fields.Framing;
import dev.vexelray.fields.art.Channel;
import dev.vexelray.fields.art.Raster;
import dev.vexelray.fields.art.Svg;
import dev.vexelray.fields.atlas.ArtPair;
import dev.vexelray.fields.msdf.Msdf;
import dev.vexelray.fields.sdf.ShapeSdf;
import dev.vexelray.fields.sdg.Sdg;
import dev.vexelray.fields.sdg.SdgRender;
import dev.vexelray.fields.shape.PathData;
import dev.vexelray.fields.shape.Shape;
import dev.vexelray.fields.shape.Vec2;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConvertTest {

    static final String STAR = "M50 0 L61 35 L98 35 L68 57 L79 91 L50 70 L21 91 L32 57 L2 35 L39 35 Z";

    /** A badge: a blue rounded square, a white star on it, and a yellow dot named "sun". */
    static final String BADGE = """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100">
              <rect x="4" y="4" width="92" height="92" rx="18" fill="#2d6cdf"/>
              <path d="M50 18 L58 42 L84 42 L63 57 L71 82 L50 67 L29 82 L37 57 L16 42 L42 42 Z" fill="#ffffff"/>
              <circle id="sun" cx="78" cy="22" r="10" fill="#ffcc33"/>
            </svg>""";

    static Sdg badge() {
        return Convert.svgToSdg(64).apply(Svg.parse(BADGE));
    }

    // ------------------------------------------------------------------ routes name their hops

    @Test
    void aRouteNamesEveryKindItPassesThroughAndJoinsWhatEachHopKeeps() {
        Conversion<Sdg, Field> exact = Convert.sdgToMtsdf(32, 4, 8, 0.25, Set.of());
        assertEquals("SDG → Mask → Shape → MTSDF", exact.name());
        assertEquals(3, exact.hops().size());
        assertEquals("LOSSY (paints)", exact.fidelity().toString());

        Conversion<Sdg, Field> fast = Convert.sdgToMtsdfFast(32, 4, 8, Set.of());
        assertEquals("SDG → Mask → SDF → MTSDF", fast.name());
        assertEquals("LOSSY (paints; corners: round at magnification)", fast.fidelity().toString());

        Conversion<Field, Sdg> painted = Convert.fieldToSdg(FieldKind.SDF, 8, new Sdg.Paint.Solid(0xFFFFFFFF));
        assertEquals("SYNTHESISING (a paint)", painted.fidelity().toString());
    }

    @Test
    void twoHopsThatOnlyShareAJavaTypeDoNotJoin() {
        // Both are Field → ..., but an SDF is not an MSDF.
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> Convert.widen(FieldKind.MSDF).then(Convert.sdfToCoverage(8, 8)));
        assertTrue(e.getMessage().contains("it makes MSDF and that takes SDF"), e.getMessage());
    }

    @Test
    void aBranchedRouteNamesBothBranchesAndItsJoin() {
        Conversion<Sdg, ArtPair> pair = Convert.sdgToArtPair(32, 0.25f, new int[]{24}, null);
        assertEquals("(SDG → Mask → Shape → MTSDF) beside (SDG → Raster sizes) → Art pair", pair.name());
        assertEquals(5, pair.hops().size());
    }

    @Test
    void whatTheSourceDoesNotHoldIsRequiredNotGuessed() {
        assertThrows(IllegalArgumentException.class, () -> Convert.rasterToMask(Channel.AUTO, 0.5f, false, 1));
        assertThrows(IllegalArgumentException.class, () -> Convert.sdfToProbability(0));
        assertThrows(IllegalArgumentException.class, () -> Convert.svgToShape().apply(Svg.parse(BADGE)));
    }

    // ------------------------------------------------------------------ the mask tracer

    @Test
    void aSquareMaskTracesToItsFourCorners() {
        int w = 16;
        boolean[] in = new boolean[w * w];
        for (int y = 4; y < 12; y++) {
            for (int x = 4; x < 12; x++) {
                in[y * w + x] = true;
            }
        }
        Shape s = Convert.maskToShape(0.25).apply(new Mask(w, w, in, 1, 0, 0, 1));
        assertEquals(1, s.contours().size());
        // The cracks cut each corner by half a sample; refitted, the four sides meet at the square's own corners.
        assertEquals(4, s.contours().getFirst().edges().size());
        for (var e : s.contours().getFirst().edges()) {
            Vec2 p = e.point(0);
            assertEquals(Math.round(p.x()), p.x(), 1e-6);
            assertEquals(Math.round(p.y()), p.y(), 1e-6);
        }
        assertTrue(s.filled(new Vec2(8, 8)));
        assertFalse(s.filled(new Vec2(3, 8)));
        assertEquals(4, s.bounds().xMin(), 1e-6);
        assertEquals(12, s.bounds().xMax(), 1e-6);
    }

    @Test
    void pixelArtComesBackAsItsOwnSquares() {
        // an L of three samples, and a sample touching it only at a corner
        String[] rows = {"#..", "##.", "..#"};
        boolean[] in = new boolean[9];
        for (int i = 0; i < 9; i++) {
            in[i] = rows[i / 3].charAt(i % 3) == '#';
        }
        Shape s = Convert.maskToCells().apply(new Mask(3, 3, in, 1, 0, 0, 1));
        java.util.Set<String> corners = new java.util.TreeSet<>();
        int edges = 0;
        for (var c : s.contours()) {
            for (var e : c.edges()) {
                Vec2 p = e.point(0);
                corners.add((int) p.x() + "," + (int) p.y());
                assertEquals(Math.round(p.x()), p.x(), 0);
                assertEquals(Math.round(p.y()), p.y(), 0);
                edges++;
            }
        }
        // the L's six corners and the square's four, one of which it shares: joined there, never split
        assertEquals(10, edges);
        assertEquals(java.util.Set.of("0,0", "1,0", "1,1", "2,1", "2,2", "0,2", "3,2", "3,3", "2,3"), corners);
        assertTrue(s.filled(new Vec2(0.5, 1.5)));
        assertTrue(s.filled(new Vec2(2.5, 2.5)));
        assertFalse(s.filled(new Vec2(1.5, 0.5)));
    }

    @Test
    void aLayerTakesItsRoleByNameAndAMissingNameIsRefused() {
        Sdg badge = badge();
        Sdg roled = badge.withRoles(java.util.Map.of("sun", "accent"));
        assertEquals("accent", ((Sdg.Paint.Solid) roled.layers().stream().filter(l -> l.name().equals("sun"))
                .findFirst().orElseThrow().paint()).role());
        assertThrows(IllegalArgumentException.class, () -> badge.withRoles(java.util.Map.of("moon", "ink")));
    }

    @Test
    void aRingTracesToAnOutlineAndAHoleThatTheFillLeavesEmpty() {
        int w = 64;
        boolean[] in = new boolean[w * w];
        for (int y = 0; y < w; y++) {
            for (int x = 0; x < w; x++) {
                double r = Math.hypot(x + 0.5 - 32, y + 0.5 - 32);
                in[y * w + x] = r < 28 && r > 14;
            }
        }
        Shape s = Convert.maskToShape(0.25).apply(new Mask(w, w, in, 1, 0, 0, 1));
        assertEquals(2, s.contours().size());
        assertFalse(s.filled(new Vec2(32, 32)), "the hole");
        assertTrue(s.filled(new Vec2(32, 11)), "the ring");
        assertFalse(s.filled(new Vec2(2, 2)), "outside");
    }

    @Test
    void theTracedRouteKeepsCornersTheWidenedOneRounds() {
        Shape star = PathData.parse(STAR);
        int field = 32, spread = 4, side = field + 2 * spread, os = 16;
        Framing framing = Framing.fit(star.bounds(), field, field, 2, false);   // no tip on the mask's border
        // The truth: the star's own MSDF, on the same texels as the mask's.
        Framing shifted = new Framing(framing.scale(), framing.originX() - spread / framing.scale(),
                framing.originY() - spread / framing.scale(), false);
        Field truth = Msdf.generate(star, shifted, side, side);

        boolean[] in = new boolean[field * os * field * os];
        for (int y = 0; y < field * os; y++) {
            for (int x = 0; x < field * os; x++) {
                in[y * field * os + x] = star.filled(framing.toShape((x + 0.5) / os, (y + 0.5) / os));
            }
        }
        Mask m = new Mask(field * os, field * os, in, os, 0, 0, 1);
        Field traced = Convert.maskToShape(0.25).then(Convert.shapeToMsdf(Convert.margin(spread), side, side)).apply(m);
        Field widened = Convert.maskToSdf(spread).then(Convert.widen(FieldKind.MSDF)).apply(m);

        // Drawn at 16× the field, where an SDF's corners round off.
        int px = side * 16;
        float[] t = Coverage.render(truth, px, px);
        float[] a = Coverage.render(traced, px, px);
        float[] b = Coverage.render(widened, px, px);
        // Only near the star's ten corners, within a texel and a half: along its straight edges the two agree.
        double ea = 0, eb = 0;
        for (var c : star.contours().getFirst().edges()) {
            Vec2 tip = shifted.toTexels(c.point(0));
            for (int y = 0; y < px; y++) {
                for (int x = 0; x < px; x++) {
                    if (Math.hypot((x + 0.5) / 16 - tip.x(), (y + 0.5) / 16 - tip.y()) < 1.5) {
                        int i = y * px + x;
                        ea += Math.abs(a[i] - t[i]);
                        eb += Math.abs(b[i] - t[i]);
                    }
                }
            }
        }
        assertTrue(ea < eb * 0.15, "traced " + ea + " against widened " + eb);
    }

    // ------------------------------------------------------------------ probability

    @Test
    void phiAndItsInverseAgree() {
        assertEquals(0.5, Footprint.phi(0), 1e-7);
        assertEquals(0.9750021, Footprint.phi(1.96), 1e-6);
        for (double x = -6; x <= 6; x += 0.37) {
            assertEquals(x, Footprint.inverse(Footprint.phi(x)), 1e-5 * Math.max(1, Math.abs(x)), "x " + x);
        }
    }

    @Test
    void aDistanceComesBackFromItsProbabilityWithinTheBand() {
        Shape star = PathData.parse(STAR);
        Field sdf = ShapeSdf.generate(star, Framing.fit(star.bounds(), 32, 32, 4, false), 32, 32);
        double sigma = 1.5, band = 1e-4;
        Field back = Convert.sdfToProbability(sigma).then(Convert.probabilityToSdf(band)).apply(sdf);
        double reach = sigma * Footprint.inverse(1 - band);
        for (int i = 0; i < sdf.data().length; i++) {
            float d = sdf.data()[i];
            float expected = (float) Math.clamp(d, -reach, reach);
            assertEquals(expected, back.data()[i], 2e-3, "texel " + i);
        }
    }

    // ------------------------------------------------------------------ SDG

    @Test
    void aFieldPaintedIsAnSdgThatDrawsAsTheFieldDoes() {
        Shape star = PathData.parse(STAR);
        Field sdf = ShapeSdf.generate(star, Framing.fit(star.bounds(), 48, 48, 4, false), 48, 48);
        Sdg sdg = Convert.fieldToSdg(FieldKind.SDF, 8, new Sdg.Paint.Solid(0xFF0000FF)).apply(sdf);
        Raster r = SdgRender.render(sdg, 96, 96);
        float[] cover = Coverage.render(sdf, 96, 96);
        for (int i = 0; i < cover.length; i++) {
            // the SDG's distances are bytes at a range of 8: a step of 8 / 255 texel, a sixteenth of a pixel here
            assertEquals(cover[i], (r.rgba()[i * 4 + 3] & 0xFF) / 255f, 0.035, "pixel " + i);
        }
        Field back = Convert.sdgLayerSdf(0).apply(sdg);
        for (int i = 0; i < sdf.data().length; i++) {
            assertEquals(Math.clamp(sdf.data()[i], -4, 4), back.data()[i], 8.0 / 255, "texel " + i);
        }
    }

    @Test
    void aMaskOfAnSdgIsWhereItIsOpaqueLessTheLayersLeftOut() {
        Sdg badge = badge();
        Mask all = Convert.sdgToMask(25, 25, 4, Set.of()).apply(badge);
        Mask lifted = Convert.sdgToMask(25, 25, 4, Set.of("sun")).apply(badge);
        assertEquals(100, all.width());
        assertTrue(all.at(50, 50));
        assertFalse(all.at(1, 1), "the rounded corner");
        int sunX = 78, sunY = 22;     // the sun lies past the square's corner radius, on the square
        assertTrue(all.at(sunX, sunY));
        // The sun sits on the square, so the union is unchanged where it is; what is lost is only its own outline.
        assertTrue(lifted.at(sunX, sunY));
    }

    @Test
    void anArtPairFromAnSdgHasItsColourAsTheSdgDrawsAndRecordsItsLift() {
        Sdg badge = badge();
        ArtPair pair = Convert.sdgToArtPair(48, 0.25f, new int[]{32, 64}, "sun").apply(badge);
        assertEquals(48, pair.field());
        assertEquals(12, pair.spread());
        assertEquals(2, pair.atlases().size());
        ArtPair.Atlas a = pair.atlases().get(1);
        byte[] drawn = SdgRender.render(badge, 64, 64).rgba();
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 64; x++) {
                int s = (y * 64 + x) * 4, o = ((a.colourY() + y) * a.width() + a.colourX() + x) * 4;
                int alpha = drawn[s + 3] & 0xFF;
                assertEquals(alpha, a.rgba()[o + 3] & 0xFF);
                assertEquals(Math.round((drawn[s] & 0xFF) * alpha / 255f), a.rgba()[o] & 0xFF, 1);
            }
        }
        assertEquals(0.78f, pair.lift().x(), 0.01f);
        assertEquals(0.22f, pair.lift().y(), 0.01f);
        assertEquals(0.10f, pair.lift().radius(), 0.01f);
    }

    // ------------------------------------------------------------------ icons

    @Test
    void anIconIsAnIcoOfAPngPerSizeSmallestFirst() {
        Sdg big = badge();
        Sdg small = Convert.svgToSdg(32).apply(Svg.parse(
                "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 16 16\"><rect width=\"16\" height=\"16\" "
                        + "fill=\"#ff0000\"/></svg>"));
        Icon icon = Convert.sdgToIcon(new int[]{256, 16, 48, 24}, List.of(new Convert.Small(24, small))).apply(big);
        assertEquals(List.of(16, 24, 48, 256), icon.sizes().stream().map(Raster::width).toList());
        // 16 and 24 come from the small drawing: all red.
        assertEquals(0xFF, icon.sizes().getFirst().rgba()[0] & 0xFF);
        assertEquals(0, icon.sizes().getFirst().rgba()[2] & 0xFF);

        ByteBuffer b = ByteBuffer.wrap(icon.ico()).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(0, b.getShort(0));
        assertEquals(1, b.getShort(2));
        assertEquals(4, b.getShort(4));
        int[] sides = {16, 24, 48, 0};
        for (int i = 0; i < 4; i++) {
            int e = 6 + 16 * i;
            assertEquals(sides[i], b.get(e) & 0xFF);
            int offset = b.getInt(e + 12);
            assertEquals((byte) 0x89, b.get(offset));
            assertEquals('P', b.get(offset + 1));
        }
        assertThrows(IllegalArgumentException.class, () -> new Icon(List.of(new Raster(16, 16, new byte[1024]),
                new Raster(16, 16, new byte[1024]))));
    }

    @Test
    void aDrawingThatCompositesIsAGroupAndAnIcon() {
        Svg faded = Svg.parse("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 16 16\"><g opacity=\"0.5\">"
                + "<rect width=\"16\" height=\"16\" fill=\"#ff0000\"/></g></svg>");
        assertTrue(Convert.svgToSdg(32).apply(faded).layers().getFirst().isGroup(), "a group since SDG 1.2");
        Icon icon = Convert.svgToIcon(new int[]{16, 32}, List.of()).apply(faded);
        assertEquals("SVG → Raster sizes → Icon", Convert.svgToIcon(new int[]{16}, List.of()).name());
        assertEquals(128, icon.sizes().getFirst().rgba()[3] & 0xFF, 1);
    }

    // ------------------------------------------------------------------ two more routes

    @Test
    void anMsdfDrawnBackAndRetracedIsItsSdf() {
        Shape star = PathData.parse(STAR);
        Framing f = Framing.fit(star.bounds(), 32, 32, 4, false);
        Field msdf = Msdf.generate(star, f, 32, 32);
        Field sdf = Convert.msdfToSdf(32, 32, 8, 0).apply(msdf);
        Field truth = ShapeSdf.generate(star, f, 32, 32);
        for (int i = 0; i < sdf.data().length; i++) {
            if (Math.abs(truth.data()[i]) < 3) {
                assertEquals(truth.data()[i], sdf.data()[i], 0.35, "texel " + i);
            }
        }
    }

    @Test
    void aRasterTracedIntoAnMsdfDrawsItsMask() {
        int side = 32 * 8;
        byte[] rgba = new byte[side * side * 4];
        for (int y = 0; y < side; y++) {
            for (int x = 0; x < side; x++) {
                boolean in = Math.hypot(x + 0.5 - 128, y + 0.5 - 128) < 90;
                rgba[(y * side + x) * 4 + 3] = (byte) (in ? 255 : 0);
            }
        }
        Field msdf = Convert.rasterToMsdf(Channel.ALPHA, 0.5f, false, 8, 0.25, 32, 32, 2)
                .apply(new Raster(side, side, rgba));
        assertEquals(36, msdf.width());
        float[] c = Coverage.render(msdf, 36, 36);
        assertEquals(1f, c[18 * 36 + 18], 1e-3);
        assertEquals(0f, c[0], 1e-3);
    }
}
