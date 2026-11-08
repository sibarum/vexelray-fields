package dev.vexelray.fields.sdg;

import dev.vexelray.fields.art.Raster;
import dev.vexelray.fields.art.Svg;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** SDG 1.2's groups: drawn as SVG draws them, kept by both file forms, and refused past their limit. */
class SdgGroupsTest {

    /** Two overlapping squares, red under blue, in a group at half opacity. */
    static final String FADED = """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100">
              <g id="pair" opacity="0.5">
                <rect x="10" y="10" width="50" height="50" fill="#ff0000"/>
                <rect x="40" y="40" width="50" height="50" fill="#0000ff"/>
              </g>
            </svg>""";

    /** A green square clipped to a circle. */
    static final String CLIPPED = """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100">
              <defs><clipPath id="round"><circle cx="50" cy="50" r="30"/></clipPath></defs>
              <rect x="10" y="10" width="80" height="80" fill="#00a000" clip-path="url(#round)"/>
            </svg>""";

    /** An orange square under a luminance mask: white on the left half, black on the right. */
    static final String MASKED = """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100">
              <defs><mask id="half"><rect x="0" y="0" width="50" height="100" fill="#ffffff"/>
                <rect x="50" y="0" width="50" height="100" fill="#000000"/></mask></defs>
              <rect x="10" y="10" width="80" height="80" fill="#ff8000" mask="url(#half)"/>
            </svg>""";

    static Sdg sdg(String svg) {
        return SvgToSdg.convert(Svg.parse(svg), SvgToSdg.Options.across(Svg.parse(svg), 128));
    }

    @Test
    void aGroupOpacityIsLaidOverOnceSoTheOverlapShowsOnlyTheTop() {
        Sdg sdg = sdg(FADED);
        assertEquals(1, sdg.layers().size());
        assertTrue(sdg.layers().getFirst().isGroup());
        assertEquals("pair", sdg.layers().getFirst().name());
        byte[] px = SdgRender.render(sdg, 100, 100).rgba();
        int overlap = (50 * 100 + 50) * 4;
        // blue at half, not blue over red at half each
        assertEquals(0, px[overlap] & 0xFF, 2, "no red under the blue");
        assertEquals(255, px[overlap + 2] & 0xFF, 2);
        assertEquals(128, px[overlap + 3] & 0xFF, 2);
    }

    @Test
    void eachDrawsAsTheSvgDoes() {
        for (String svg : List.of(FADED, CLIPPED, MASKED)) {
            Raster want = Svg.parse(svg).render(100, 100, 4);
            Raster got = SdgRender.render(sdg(svg), 100, 100);
            int worst = 0, past = 0;
            for (int i = 0; i < want.rgba().length; i += 4) {
                int wa = want.rgba()[i + 3] & 0xFF, ga = got.rgba()[i + 3] & 0xFF;
                int d = Math.abs(wa - ga);
                for (int c = 0; c < 3 && wa > 32 && ga > 32; c++) {
                    d = Math.max(d, Math.abs((want.rgba()[i + c] & 0xFF) - (got.rgba()[i + c] & 0xFF)));
                }
                worst = Math.max(worst, d);
                past += d > 40 ? 1 : 0;
            }
            // edges are antialiased differently (a field's half-pixel cut against 16 samples a pixel): a few pixels
            // along them may differ, nothing inside
            assertTrue(past < 60, "pixels far off: " + past + ", worst " + worst + "\n" + svg);
        }
    }

    @Test
    void theClipAndTheMaskCutWhereTheySay() {
        byte[] clipped = SdgRender.render(sdg(CLIPPED), 100, 100).rgba();
        assertEquals(255, clipped[(50 * 100 + 50) * 4 + 3] & 0xFF, "inside the circle");
        assertEquals(0, clipped[(15 * 100 + 15) * 4 + 3] & 0xFF, "in the square's corner, outside the circle");
        byte[] masked = SdgRender.render(sdg(MASKED), 100, 100).rgba();
        assertEquals(255, masked[(50 * 100 + 25) * 4 + 3] & 0xFF, "under white");
        assertEquals(0, masked[(50 * 100 + 75) * 4 + 3] & 0xFF, "under black");
    }

    @Test
    void bothFormsKeepGroupsClipsAndMasks(@TempDir Path dir) throws IOException {
        for (String svg : List.of(FADED, CLIPPED, MASKED)) {
            Sdg sdg = sdg(svg);
            byte[] want = SdgRender.render(sdg, 64, 64).rgba();
            for (String name : List.of("a.sdg", "a.sdgb")) {
                Path f = dir.resolve(name);
                SdgFile.write(sdg, f);
                Sdg back = SdgFile.read(f);
                assertEquals(shape(sdg.layers()), shape(back.layers()), name);
                assertTrue(java.util.Arrays.equals(want, SdgRender.render(back, 64, 64).rgba()), name + " draws the same");
            }
        }
    }

    @Test
    void groupsNestSixteenDeepAndNoDeeper() {
        Sdg.Layer leaf = sdg(FADED).layers().getFirst().group().layers().getFirst();
        Sdg base = sdg(FADED);
        Sdg.Layer l = leaf;
        for (int d = 0; d < Sdg.Group.MAX_DEPTH; d++) {
            l = Sdg.Layer.group("g" + d, 1f, new Sdg.Group(List.of(l), null, null));
        }
        Sdg ok = new Sdg(100, 100, base.pages(), List.of(l));
        SdgFile.read(SdgFile.bytes(ok));
        Sdg.Layer deeper = Sdg.Layer.group("too", 1f, new Sdg.Group(List.of(l), null, null));
        Sdg tooDeep = new Sdg(100, 100, base.pages(), List.of(deeper));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> SdgFile.read(SdgFile.bytes(tooDeep)));
        assertTrue(e.getMessage().contains("16 deep"), e.getMessage());
    }

    /** Names and kinds, groups opened: what both forms must keep. */
    private static String shape(List<Sdg.Layer> layers) {
        StringBuilder s = new StringBuilder();
        for (Sdg.Layer l : layers) {
            s.append(l.name()).append(':').append(l.opacity());
            if (l.isGroup()) {
                Sdg.Group g = l.group();
                s.append("{clip=").append(g.clip() != null).append(" mask=")
                        .append(g.mask() == null ? "-" : g.mask().kind() + shape(g.mask().layers())).append(' ')
                        .append(shape(g.layers())).append('}');
            } else {
                s.append('[').append(l.stencil().kind()).append(']');
            }
            s.append(';');
        }
        return s.toString();
    }
}
