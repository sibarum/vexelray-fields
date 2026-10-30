package dev.vexelray.fields.atlas;

import dev.vexelray.fields.Encoding;
import dev.vexelray.fields.Field;
import dev.vexelray.fields.FieldKind;
import dev.vexelray.fields.art.ArtField;
import dev.vexelray.fields.art.Svg;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtlasTest {

    static final GridAtlas.Grid LARGE = new GridAtlas.Grid(24, 4);   // cells of 32
    static final GridAtlas.Grid SMALL = new GridAtlas.Grid(12, 2);   // cells of 16

    /** A disc filling most of a grid's square, as a field of {@code kind}. */
    static Field disc(GridAtlas.Grid g, FieldKind kind) {
        Svg svg = Svg.parse("<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 10 10'>"
                + "<circle cx='5' cy='5' r='4'/></svg>");
        return ArtField.of(svg, kind, g.inner(), g.inner(), g.spread());
    }

    @Test
    void pixelsReadBackInEitherLayout() throws IOException {
        byte[] texels = new byte[5 * 3 * 4];
        new Random(1).nextBytes(texels);
        AtlasPixels four = AtlasPixels.rgba(5, 3, texels);
        byte[] file = four.bytes();
        assertEquals(AtlasPixels.MAGIC, ByteBuffer.wrap(file).getInt());
        AtlasPixels back = AtlasPixels.read(new ByteArrayInputStream(file));
        assertEquals(5, back.width());
        assertEquals(4, back.channels());
        assertArrayEquals(texels, back.texels());

        AtlasPixels one = new AtlasPixels(5, 3, 1, Arrays.copyOf(texels, 15));
        byte[] r8 = one.bytes();
        assertEquals(AtlasPixels.MAGIC_CHANNELS, ByteBuffer.wrap(r8).getInt());
        assertEquals(1, ByteBuffer.wrap(r8).getInt(12));
        assertArrayEquals(one.texels(), AtlasPixels.read(new ByteArrayInputStream(r8)).texels());
        assertThrows(IOException.class, () -> AtlasPixels.read(new ByteArrayInputStream(new byte[16])));
    }

    @Test
    void theWidthIsTheOneWithTheShortestLongestSide() {
        // three cells of 32: 32 x 96, 64 x 64 or 96 x 32; the square one
        assertEquals(64, GridAtlas.width(List.of(LARGE), new int[]{3}));
        // one cell: no row of empty ones
        assertEquals(32, GridAtlas.width(List.of(LARGE), new int[]{1}));
        // small cells alone may make the row narrower than a large one
        assertEquals(32, GridAtlas.width(List.of(LARGE, SMALL), new int[]{0, 4}));
    }

    @Test
    void cellsFillRowsInOrderAndTheGridsStack() {
        List<GridAtlas.Entry> entries = List.of(
                GridAtlas.Entry.of("a", disc(LARGE, FieldKind.SDF), disc(SMALL, FieldKind.SDF)),
                GridAtlas.Entry.of("b", disc(LARGE, FieldKind.SDF), null),
                GridAtlas.Entry.of("c", null, disc(SMALL, FieldKind.SDF)),
                GridAtlas.Entry.of("d", disc(LARGE, FieldKind.SDF), disc(SMALL, FieldKind.SDF)));
        GridAtlas atlas = GridAtlas.bake(entries, List.of(LARGE, SMALL), 64);
        // three large cells, two to a row: two rows of 32; then three small, four to a row: one row of 16
        assertEquals(64, atlas.width());
        assertEquals(64 + 16, atlas.height());
        assertEquals(64, atlas.top(1));
        assertArrayEquals(new int[]{0, 0}, atlas.cells()[0]);
        assertArrayEquals(new int[]{1, -1}, atlas.cells()[1]);
        assertArrayEquals(new int[]{-1, 1}, atlas.cells()[2]);
        assertArrayEquals(new int[]{2, 2}, atlas.cells()[3]);
        assertArrayEquals(new int[]{0, 32}, atlas.origin(0, 2));
        assertArrayEquals(new int[]{32, 64}, atlas.origin(1, 2));

        // a cell holds its field as the canvas lays one out; the end of an unfilled row reads far outside
        Field d = disc(LARGE, FieldKind.SDF);
        int centre = ((32 + 16) * 64 + 16) * 4;
        assertEquals(Encoding.encode(d.get(16, 16, 0), LARGE.range()), atlas.rgba()[centre]);
        int empty = ((32 + 16) * 64 + 48) * 4;
        assertEquals(0, atlas.rgba()[empty]);
        assertEquals((byte) 255, atlas.rgba()[empty + 3]);
        assertTrue(atlas.fitsEveryDevice());
    }

    @Test
    void anMtsdfAtlasReadsFarOutsideInAlphaToo() {
        GridAtlas atlas = GridAtlas.bake(List.of(GridAtlas.Entry.of("a", disc(LARGE, FieldKind.MTSDF))),
                List.of(LARGE), 64);
        assertEquals(0, atlas.rgba()[(16 * 64 + 48) * 4 + 3]);   // past the one cell
        assertTrue((atlas.rgba()[(16 * 64 + 16) * 4 + 3] & 0xFF) > 200);   // inside the disc
    }

    @Test
    void aFieldMustFitItsCellAndMatchTheOthers() {
        assertThrows(IllegalArgumentException.class, () -> GridAtlas.bake(
                List.of(GridAtlas.Entry.of("a", disc(SMALL, FieldKind.SDF))), List.of(LARGE), null));
        assertThrows(IllegalArgumentException.class, () -> GridAtlas.bake(List.of(
                GridAtlas.Entry.of("a", disc(LARGE, FieldKind.SDF)),
                GridAtlas.Entry.of("b", disc(LARGE, FieldKind.MSDF))), List.of(LARGE), null));
    }

    @Test
    void aPairHoldsItsFieldAtTwoRangesBesideItsColour() {
        Svg mark = Svg.parse("<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 20 20'>"
                + "<rect x='2' y='8' width='16' height='10' fill='#ff0000'/>"
                + "<circle id='dot' cx='10' cy='4' r='3' fill='#0000ff' fill-opacity='0.5'/></svg>");
        ArtPair pair = ArtPair.bake(mark, 40, 0.25f, new int[]{20, 30}, "dot");
        assertEquals(10, pair.spread());
        assertEquals(60, pair.fieldSide());
        ArtPair.Atlas a = pair.atlases().get(1);
        assertEquals(60 + ArtPair.GUTTER + 1, a.colourX());
        assertEquals(a.colourX() + 30 + 1, a.width());
        assertEquals(60, a.height());

        // the field: inside the rect, near at the edge and far beyond; the lifted dot left out of it
        int inside = (40 * 60 + 30) * 4;   // (30.5, 40.5): 4.5 inside the rect's top edge at y 26
        assertEquals((byte) 255, a.rgba()[inside]);
        int dot = (16 * 60 + 30) * 4;      // where the dot is drawn, (10, 4) at 2 texels a unit
        assertEquals(0, a.rgba()[dot]);
        assertTrue((a.rgba()[dot + 3] & 0xFF) < 128);

        // the colour, premultiplied: the dot half blue at half alpha
        int c = ((a.colourY() + 6) * a.width() + a.colourX() + 15) * 4;
        assertEquals(0, a.rgba()[c]);
        assertEquals(128, a.rgba()[c + 2] & 0xFF, 1);
        assertEquals(128, a.rgba()[c + 3] & 0xFF, 1);

        ArtPair.Lift lift = pair.lift();
        assertEquals(0.5, lift.x(), 0.01);
        assertEquals(0.2, lift.y(), 0.01);
        assertEquals(0.15, lift.radius(), 0.01);
        assertTrue(pair.descriptor().contains("atlas.30=" + a.width() + " 60 " + a.colourX() + " 1\n"));
        assertNull(ArtPair.bake(mark, 40, 0.25f, new int[]{20}, null).lift());
    }
}
