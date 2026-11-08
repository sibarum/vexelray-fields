package dev.vexelray.fields.sdg;

import dev.vexelray.fields.sdg.future.FutureDrawing;
import dev.vexelray.fields.sdg.future.FutureDrawingIntrabuf;
import dev.vexelray.fields.sdg.future.FutureLayer;
import dev.vexelray.fields.sdg.future.FutureLinear;
import dev.vexelray.fields.sdg.future.FuturePaint;
import dev.vexelray.fields.sdg.future.FutureStencil;
import dev.vexelray.fields.sdg.past.PastDrawing;
import dev.vexelray.fields.sdg.past.PastDrawingIntrabuf;
import dev.vexelray.fields.sdg.past.PastLayer;
import dev.vexelray.fields.sdg.past.PastPaint;
import dev.vexelray.fields.sdg.past.PastSolid;
import dev.vexelray.fields.sdg.schema.DrawingIntrabuf;
import dev.vexelray.fields.sdg.schema.Page;
import dev.vexelray.fields.sdg.schema.Solid;
import dev.vexelray.fields.sdg.schema.Stencil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SdgFileTest {

    /** Two pages, a distance page and a colour one, and layers of every kind there is, one with an awkward name. */
    static Sdg drawing() {
        Sdg.Page fields = page(40, 24, 1), colours = page(16, 16, 2);
        return new Sdg(96.5, 64, List.of(fields, colours), List.of(
                new Sdg.Layer("back", new Sdg.Stencil(Sdg.Stencil.SDF, 0, 2, 0, 0, 20, 24, -4, -4, 0.75, 8),
                        new Sdg.Paint.Sampled(1, 0, 0, 16, 16, -4, -4, 1.0 / 3), 1),
                new Sdg.Layer("sun & \"moon\" <1>\n\tétoile ★",
                        new Sdg.Stencil(Sdg.Stencil.MSDF, 0, 0, 20, 2, 20, 20, 52.125, 1e-7, 1, 8),
                        new Sdg.Paint.Solid(0xF6C445FF, "accent"), 0.5f),
                new Sdg.Layer("", new Sdg.Stencil(Sdg.Stencil.SDF, 0, 3, 0, 0, 40, 24, 0, 0, 2, 4.5),
                        new Sdg.Paint.Solid(0x00000080), 0.1f)));
    }

    /** Texels from a seed, every byte value turning up, nothing a filter would find too easy. */
    static Sdg.Page page(int w, int h, int seed) {
        byte[] rgba = new byte[w * h * 4];
        long s = seed * 0x9E3779B97F4A7C15L;
        for (int i = 0; i < rgba.length; i++) {
            s = s * 6364136223846793005L + 1442695040888963407L;
            rgba[i] = (byte) (i % 3 == 0 ? (s >>> 56) : (i / 4) % 256);
        }
        return new Sdg.Page(w, h, rgba);
    }

    static void assertSameDrawing(Sdg a, Sdg b) {
        assertEquals(a.width(), b.width());
        assertEquals(a.height(), b.height());
        assertEquals(a.layers(), b.layers());
        assertEquals(a.pages().size(), b.pages().size());
        for (int i = 0; i < a.pages().size(); i++) {
            assertEquals(a.pages().get(i).width(), b.pages().get(i).width());
            assertEquals(a.pages().get(i).height(), b.pages().get(i).height());
            assertArrayEquals(a.pages().get(i).rgba(), b.pages().get(i).rgba(), "page " + i);
        }
    }

    @Test
    void bothFormsGiveBackTheDrawing() {
        Sdg sdg = drawing();
        String xml = SdgFile.xml(sdg);
        assertSameDrawing(sdg, SdgFile.read(xml.getBytes(StandardCharsets.UTF_8)));
        byte[] compiled = SdgFile.bytes(sdg);
        assertSameDrawing(sdg, SdgFile.read(compiled));
        // compiled from the XML and back, nothing lost either way
        assertEquals(xml, SdgFile.xml(SdgFile.read(SdgFile.bytes(SdgFile.read(xml.getBytes(StandardCharsets.UTF_8))))));
        assertTrue(xml.contains("role=\"accent\""), xml);
        assertTrue(xml.contains("<sdg xmlns=\"https://sibarum.github.io/ns/sdg\" version=\"1.1\""), xml);
    }

    /** One page of one texel and two solid layers, the second in a role. */
    static Sdg twoLayers() {
        Sdg.Stencil stencil = new Sdg.Stencil(Sdg.Stencil.SDF, 0, 0, 0, 0, 1, 1, 0, 0, 1, 4);
        return new Sdg(1, 1, List.of(new Sdg.Page(1, 1, new byte[]{(byte) 255, 0, 0, (byte) 255})), List.of(
                new Sdg.Layer("plain", stencil, new Sdg.Paint.Solid(0x336699FF), 1f),
                new Sdg.Layer("themed", stencil, new Sdg.Paint.Solid(0x101010FF).as("foreground"), 0.5f)));
    }

    @Test
    void aRoleReadsBack() {
        for (Sdg back : new Sdg[]{SdgFile.read(SdgFile.bytes(twoLayers())),
                SdgFile.read(SdgFile.xml(twoLayers()).getBytes(StandardCharsets.UTF_8))}) {
            assertEquals(twoLayers().layers(), back.layers());
            assertEquals("", ((Sdg.Paint.Solid) back.layers().get(0).paint()).role());
            assertEquals("foreground", ((Sdg.Paint.Solid) back.layers().get(1).paint()).role());
        }
    }

    /** A 1.0 writer's solid paint had no role: such a file reads, with none, in either form. */
    @Test
    void aSolidFromBeforeRolesHasNone() {
        Sdg.Layer plain = twoLayers().layers().get(0);
        Sdg.Stencil s = plain.stencil();
        PastDrawing old = new PastDrawing(1, 1, List.of(new Page(Png.encode(twoLayers().pages().get(0)))),
                List.of(new PastLayer(plain.name(), plain.opacity(), new Stencil(s.kind(), s.page(), s.channel(),
                        s.x(), s.y(), s.width(), s.height(), s.originX(), s.originY(), s.scale(), (float) s.range()),
                        new PastPaint("solid", new PastSolid(0x336699FF), null))));
        byte[] payload = PastDrawingIntrabuf.toBytes(old);
        byte[] file = Arrays.copyOf(SdgFile.SIGNATURE, SdgFile.SIGNATURE.length + payload.length);
        System.arraycopy(payload, 0, file, SdgFile.SIGNATURE.length, payload.length);
        assertEquals(List.of(plain), SdgFile.read(file).layers());

        String xml = SdgFile.xml(new Sdg(1, 1, twoLayers().pages(), List.of(plain))).replace("1.1", "1.0");
        assertFalse(xml.contains("role"), xml);
        assertEquals(List.of(plain), SdgFile.read(xml.getBytes(StandardCharsets.UTF_8)).layers());
    }

    @Test
    void theFilesVersionIsSdgsVersion() {
        assertEquals(Sdg.MAJOR, DrawingIntrabuf.MAJOR);
        assertEquals(Sdg.MINOR, DrawingIntrabuf.MINOR);
    }

    @Test
    void pagesCanLieBesideTheDrawing(@TempDir Path dir) throws IOException {
        Sdg sdg = drawing();
        Path file = dir.resolve("badge.sdg");
        SdgFile.writeWithPagesBeside(sdg, file);
        assertTrue(Files.exists(dir.resolve("badge-p0.png")));
        assertTrue(Files.exists(dir.resolve("badge-p1.png")));
        String xml = Files.readString(file);
        assertTrue(xml.contains("href=\"badge-p0.png\""), xml);
        // a picture any reader opens
        assertEquals(40, ImageIO.read(dir.resolve("badge-p0.png").toFile()).getWidth());
        assertSameDrawing(sdg, SdgFile.read(file));
        // without its path there is nowhere to look for them
        assertThrows(IllegalArgumentException.class, () -> SdgFile.read(Files.readAllBytes(file)));

        Path compiled = dir.resolve("badge.sdgb");
        SdgFile.write(sdg, compiled);
        assertTrue(startsWith(Files.readAllBytes(compiled), SdgFile.SIGNATURE));
        assertSameDrawing(sdg, SdgFile.read(compiled));
    }

    /** As a person might write one: short colours, things left at their defaults, things from elsewhere. */
    @Test
    void xmlSkipsWhatItDoesNotKnow() throws IOException {
        String png = Base64.getEncoder().encodeToString(Png.encode(page(8, 8, 3)));
        String xml = """
                <?xml version="1.0"?>
                <!-- a drawing by hand -->
                <sdg xmlns="https://sibarum.github.io/ns/sdg" xmlns:ink="https://example.com/ink"
                     version="1.7" width="8" height="8" ink:tool="pen" dpi="96">
                  <ink:notes>made at the kitchen table</ink:notes>
                  <title>A thing a later version will have</title>
                  <page id="only" href="data:image/png;base64,%s"/>
                  <layer name="plain">
                    <stencil kind="sdf" page="only" x="0" y="0" width="8" height="8" range="4" glow="2"/>
                    <paint kind="solid" colour="#fc0"/>
                  </layer>
                  <layer name="later paint">
                    <stencil kind="msdf" page="only" x="0" y="0" width="8" height="8" range="4"/>
                    <paint kind="linear" from="0 0" to="8 8"><stop offset="0" colour="#fff"/></paint>
                  </layer>
                  <layer name="later stencil" opacity="0.25">
                    <stencil kind="mtsdf" page="only" x="2" y="2" width="4" height="4" origin="1, 1" range="4"/>
                    <paint kind="solid" colour="#11223344"/>
                    <filter kind="blur"/>
                  </layer>
                  <group opacity="0.5"/>
                </sdg>
                """.formatted(png);
        Sdg sdg = SdgFile.read(xml.getBytes(StandardCharsets.UTF_8));
        assertEquals(3, sdg.layers().size());
        Sdg.Layer plain = sdg.layers().get(0), paint = sdg.layers().get(1), stencil = sdg.layers().get(2);
        assertTrue(plain.known());
        assertEquals(new Sdg.Paint.Solid(0xFFCC00FF), plain.paint());
        assertEquals(1f, plain.opacity());
        assertEquals(0, plain.stencil().originX());
        assertEquals(1, plain.stencil().scale());
        assertFalse(paint.known());
        assertEquals(new Sdg.Paint.Unknown("linear"), paint.paint());
        assertFalse(stencil.known());
        assertEquals(1, stencil.stencil().originY());
        assertEquals(0.25f, stencil.opacity());
        assertEquals(new Sdg.Paint.Solid(0x11223344), stencil.paint());
    }

    /** A file from a later writer: fields on the end of several messages, and a paint of a kind not yet invented. */
    @Test
    void aCompiledFileFromALaterWriterReads() {
        FutureStencil s = new FutureStencil("sdf", 0, 1, 0, 0, 8, 8, 0, 0, 1, 4, 0.5f);
        FutureDrawing later = new FutureDrawing(8, 8, List.of(new Page(Png.encode(page(8, 8, 4)))), List.of(
                new FutureLayer("old", 1, s, new FuturePaint("solid", new Solid(0x123456FF, "foreground"), null,
                        null), "multiply"),
                new FutureLayer("new", 0.5f, s, new FuturePaint("linear", null, null,
                        new FutureLinear(0, 0, 8, 8, List.of(0xFFFFFFFF, 0x000000FF))), null)),
                "someone");
        byte[] payload = FutureDrawingIntrabuf.toBytes(later);
        byte[] file = Arrays.copyOf(SdgFile.SIGNATURE, SdgFile.SIGNATURE.length + payload.length);
        System.arraycopy(payload, 0, file, SdgFile.SIGNATURE.length, payload.length);

        Sdg sdg = SdgFile.read(file);
        assertEquals(2, sdg.layers().size());
        Sdg.Layer old = sdg.layers().get(0), fresh = sdg.layers().get(1);
        assertTrue(old.known());
        assertEquals(new Sdg.Paint.Solid(0x123456FF, "foreground"), old.paint());
        assertEquals(new Sdg.Stencil("sdf", 0, 1, 0, 0, 8, 8, 0, 0, 1, 4), old.stencil());
        assertFalse(fresh.known());
        assertEquals(new Sdg.Paint.Unknown("linear"), fresh.paint());
    }

    @Test
    void aNewerMajorIsRefusedWhole() {
        String xml = SdgFile.xml(drawing()).replace("version=\"1.1\"", "version=\"2.0\"");
        assertThrows(IllegalArgumentException.class, () -> SdgFile.read(xml.getBytes(StandardCharsets.UTF_8)));

        byte[] compiled = SdgFile.bytes(drawing());
        // the header: a magic byte, flags, four of schema id, then the major as a varint
        assertEquals(1, compiled[SdgFile.SIGNATURE.length + 6]);
        compiled[SdgFile.SIGNATURE.length + 6] = 2;
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> SdgFile.read(compiled));
        assertTrue(e.getMessage().contains("NEWER_MAJOR"), e.getMessage());
    }

    /** XML is the form a drawing comes from anywhere in: what a hostile one tries is refused, and said why. */
    @Test
    void anXmlFileIsReadAsFromAStranger(@TempDir Path dir) throws IOException {
        String entity = """
                <?xml version="1.0"?>
                <!DOCTYPE sdg [<!ENTITY x SYSTEM "file:///etc/passwd">]>
                <sdg xmlns="https://sibarum.github.io/ns/sdg" version="1.1" width="1" height="1">&x;</sdg>
                """;
        assertThrows(IllegalArgumentException.class, () -> SdgFile.read(entity.getBytes(StandardCharsets.UTF_8)));

        Files.createDirectories(dir.resolve("art"));
        Files.write(dir.resolve("secret.png"), Png.encode(page(2, 2, 5)));
        for (String href : List.of("../secret.png", "file:///" + dir.resolve("secret.png").toString().replace('\\',
                '/'), "/secret.png", "http://example.com/p.png", "data:text/plain;base64,aGk=")) {
            Path file = dir.resolve("art/a.sdg");
            Files.writeString(file, """
                    <sdg xmlns="https://sibarum.github.io/ns/sdg" version="1.1" width="1" height="1">
                      <page id="p" href="%s"/>
                    </sdg>
                    """.formatted(href));
            assertThrows(IllegalArgumentException.class, () -> SdgFile.read(file), href);
        }

        // a rectangle past its page
        String past = SdgFile.xml(drawing()).replace("x=\"20\" y=\"2\"", "x=\"30\" y=\"2\"");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> SdgFile.read(past.getBytes(StandardCharsets.UTF_8)));
        assertTrue(e.getMessage().contains("past its page"), e.getMessage());
    }

    /** A page edited and saved by an image editor comes back in whatever colour type it chose. */
    @Test
    void aPageReadsAsAnyEditorSavesIt() throws IOException {
        BufferedImage rgb = new BufferedImage(5, 3, BufferedImage.TYPE_INT_RGB);
        BufferedImage grey = new BufferedImage(5, 3, BufferedImage.TYPE_BYTE_GRAY);
        byte[] r = {0, (byte) 200, 10}, g = {0, 100, 20}, b = {0, 50, 30}, a = {0, (byte) 255, (byte) 128};
        BufferedImage indexed = new BufferedImage(5, 3, BufferedImage.TYPE_BYTE_INDEXED,
                new IndexColorModel(8, 3, r, g, b, a));
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 5; x++) {
                rgb.setRGB(x, y, 0xFF000000 | (x * 50) << 16 | (y * 80) << 8 | 7);
                grey.getRaster().setSample(x, y, 0, x * 60 + y);
                indexed.getRaster().setSample(x, y, 0, (x + y) % 3);
            }
        }
        Sdg.Page p = Png.decode(png(rgb));
        assertEquals(List.of(50, 80, 7, 255), texel(p, 1, 1));
        p = Png.decode(png(grey));
        int v = grey.getRaster().getSample(4, 2, 0);
        assertEquals(List.of(v, v, v, 255), texel(p, 4, 2));
        p = Png.decode(png(indexed));
        assertEquals(List.of(0, 0, 0, 0), texel(p, 0, 0));
        assertEquals(List.of(10, 20, 30, 128), texel(p, 1, 1));
    }

    private static List<Integer> texel(Sdg.Page p, int x, int y) {
        int i = (y * p.width() + x) * 4;
        byte[] t = p.rgba();
        return List.of(t[i] & 0xFF, t[i + 1] & 0xFF, t[i + 2] & 0xFF, t[i + 3] & 0xFF);
    }

    private static byte[] png(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private static boolean startsWith(byte[] b, byte[] prefix) {
        return b.length >= prefix.length && Arrays.equals(b, 0, prefix.length, prefix, 0, prefix.length);
    }
}
