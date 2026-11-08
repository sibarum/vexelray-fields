package dev.vexelray.fields.sdg;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SdgFileTest {

    /** One page of one texel and two solid layers, the second in a role. */
    static Sdg twoLayers() {
        Sdg.Stencil stencil = new Sdg.Stencil(Sdg.Stencil.SDF, 0, 0, 0, 0, 1, 1, 0, 0, 1, 4);
        return new Sdg(1, 1, List.of(new Sdg.Page(1, 1, new byte[]{(byte) 255, 0, 0, (byte) 255})), List.of(
                new Sdg.Layer("plain", stencil, new Sdg.Paint.Solid(0x336699FF), 1f),
                new Sdg.Layer("themed", stencil, new Sdg.Paint.Solid(0x101010FF).as("foreground"), 0.5f)));
    }

    @Test
    void aRoleReadsBack() {
        Sdg back = SdgFile.read(SdgFile.bytes(twoLayers()));
        assertEquals(twoLayers().layers(), back.layers());
        assertEquals("", ((Sdg.Paint.Solid) back.layers().get(0).paint()).role());
        assertEquals("foreground", ((Sdg.Paint.Solid) back.layers().get(1).paint()).role());
    }

    /** A 1.0 writer ended a solid block at its colour: such a file reads, with no role. */
    @Test
    void aSolidFromBeforeRolesHasNone() {
        Sdg one = new Sdg(1, 1, twoLayers().pages(), List.of(twoLayers().layers().get(0)));
        byte[] file = SdgFile.bytes(one);
        // the solid block, as 1.1 writes it: u32 length, str "solid", u32 colour, str "" (two zero bytes)
        int at = indexOf(file, "solid".getBytes()) - 2 - 4;
        ByteBuffer b = ByteBuffer.wrap(file).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(2 + 5 + 4 + 2, b.getInt(at));
        byte[] old = new byte[file.length - 2];
        System.arraycopy(file, 0, old, 0, at + 4 + 2 + 5 + 4);
        System.arraycopy(file, at + 4 + 2 + 5 + 4 + 2, old, at + 4 + 2 + 5 + 4, file.length - (at + 4 + 2 + 5 + 4 + 2));
        ByteBuffer o = ByteBuffer.wrap(old).order(ByteOrder.LITTLE_ENDIAN);
        o.putInt(at, 2 + 5 + 4);
        // the layer chunk holding the block is two bytes shorter: its length is the u32 after LAYR
        int layr = indexOf(old, "LAYR".getBytes());
        o.putInt(layr + 4, o.getInt(layr + 4) - 2);
        assertEquals(one.layers(), SdgFile.read(old).layers());
    }

    private static int indexOf(byte[] in, byte[] what) {
        outer:
        for (int i = 0; i + what.length <= in.length; i++) {
            for (int j = 0; j < what.length; j++) {
                if (in[i + j] != what[j]) {
                    continue outer;
                }
            }
            return i;
        }
        throw new AssertionError("not found");
    }
}
