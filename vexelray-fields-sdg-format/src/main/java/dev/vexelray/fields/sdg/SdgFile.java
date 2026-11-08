package dev.vexelray.fields.sdg;

import dev.vexelray.fields.sdg.schema.Drawing;
import dev.vexelray.fields.sdg.schema.DrawingIntrabuf;
import sibarum.intrabuf.IncompatiblePayloadException;
import sibarum.intrabuf.IntrabufException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * SDG's two forms, {@code docs/sdg.md} §4. {@code read} takes either, by its first bytes.
 *
 * <ul>
 * <li><b>{@code .sdg}, XML</b>: to read, to write by hand, to diff, and to give to anyone. Its pages are PNGs, in the
 * file as data URIs or in files beside it. This is the form a drawing is exchanged in, and a reader of it expects a
 * file from anyone: it is parsed with no DTD or entity, and nothing it names is fetched but a page beside it.</li>
 * <li><b>{@code .sdgb}, compiled</b>: {@link #SIGNATURE} and one intrabuf payload of
 * {@link dev.vexelray.fields.sdg.schema.Drawing}, for the stack to load. Intrabuf trusts its payloads, so this is
 * for files the application built or shipped itself; one from elsewhere comes as XML and is compiled.</li>
 * </ul>
 *
 * <p><b>Files outlive the readers that read them, both ways.</b> A reader skips what it does not know: an element or
 * attribute (XML), a field (intrabuf, every message FULL), and a layer of a stencil or paint kind it does not know,
 * read as not {@link Sdg.Layer#known()} and skipped when drawn. A file whose major version is newer than the reader's
 * is refused whole. Every drawing either form gives back has been checked: each rectangle lies in its page.
 */
public final class SdgFile {

    /** {@code \x89SDG\r\n\x1A\n} before a compiled file's payload: as PNG's, catching 7-bit and line-ending damage. */
    public static final byte[] SIGNATURE = {(byte) 0x89, 'S', 'D', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    /** The XML form's extension, and the compiled form's. */
    public static final String XML = "sdg", COMPILED = "sdgb";

    private SdgFile() {
    }

    // ------------------------------------------------------------------------------------------------- writing

    /** {@code sdg} compiled: the signature and one intrabuf payload. */
    public static byte[] bytes(Sdg sdg) {
        byte[] payload = DrawingIntrabuf.toBytes(SchemaMapping.toSchema(sdg));
        byte[] out = Arrays.copyOf(SIGNATURE, SIGNATURE.length + payload.length);
        System.arraycopy(payload, 0, out, SIGNATURE.length, payload.length);
        return out;
    }

    /** {@code sdg} as XML, its pages in it as data URIs. */
    public static String xml(Sdg sdg) {
        return SdgXml.write(sdg, SdgXml::inline);
    }

    /**
     * Writes {@code sdg} to {@code file}: compiled if its name ends {@code .sdgb}, otherwise XML with its pages in
     * it. Written whole to a file beside it, then moved over it, so a reader never sees half a file.
     */
    public static void write(Sdg sdg, Path file) throws IOException {
        boolean compiled = file.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith("." + COMPILED);
        replace(file, compiled ? bytes(sdg) : xml(sdg).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Writes {@code sdg} as XML to {@code file}, each page a PNG beside it named for it ({@code badge.sdg},
     * {@code badge-p0.png}, ...), so the pages can be looked at, and the XML is short.
     */
    public static void writeWithPagesBeside(Sdg sdg, Path file) throws IOException {
        Path dir = file.toAbsolutePath().getParent();
        String name = file.getFileName().toString();
        String stem = name.contains(".") ? name.substring(0, name.lastIndexOf('.')) : name;
        try {
            String xml = SdgXml.write(sdg, (i, png) -> {
                String page = stem + "-" + SdgXml.id(i) + ".png";
                try {
                    replace(dir.resolve(page), png);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                return escapeHref(page);
            });
            replace(file, xml.getBytes(StandardCharsets.UTF_8));
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    // ------------------------------------------------------------------------------------------------- reading

    /** Reads either form; an XML file's pages beside it are read from beside it. */
    public static Sdg read(Path file) throws IOException {
        Path dir = file.toAbsolutePath().getParent();
        try {
            return read(Files.readAllBytes(file), path -> {
                Path page = dir.resolve(path).normalize();
                if (!page.startsWith(dir)) {
                    throw new IllegalArgumentException("a page at " + path + ", above the drawing");
                }
                try {
                    return Files.readAllBytes(page);
                } catch (NoSuchFileException e) {
                    throw new IllegalArgumentException("a page at " + path + ", and there is no such file");
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    /** Reads either form. An XML drawing's pages must be in it: there is nowhere to look for a page beside it. */
    public static Sdg read(byte[] file) {
        return read(file, null);
    }

    private static Sdg read(byte[] file, java.util.function.Function<String, byte[]> pages) {
        if (startsWith(file, SIGNATURE)) {
            Drawing d;
            try {
                d = DrawingIntrabuf.CODEC.decode(file, SIGNATURE.length, file.length - SIGNATURE.length);
            } catch (IncompatiblePayloadException e) {
                throw new IllegalArgumentException("an SDG file this cannot read (" + e.reason() + "): "
                        + e.getMessage(), e);
            } catch (IntrabufException e) {
                throw new IllegalArgumentException("a damaged SDG file: " + e.getMessage(), e);
            }
            return checked(SchemaMapping.fromSchema(d));
        }
        int at = 0;
        if (file.length >= 3 && (file[0] & 0xFF) == 0xEF && (file[1] & 0xFF) == 0xBB && (file[2] & 0xFF) == 0xBF) {
            at = 3;
        }
        while (at < file.length && (file[at] == ' ' || file[at] == '\t' || file[at] == '\r' || file[at] == '\n')) {
            at++;
        }
        if (at < file.length && file[at] == '<') {
            return checked(SdgXml.read(file, pages));
        }
        throw new IllegalArgumentException("not an SDG file: neither XML nor a compiled drawing");
    }

    // ------------------------------------------------------------------------------------------------- checks

    /** {@code sdg}, once every rectangle has been found to lie in its page, and every number to be one. */
    static Sdg checked(Sdg sdg) {
        if (!Double.isFinite(sdg.width()) || !Double.isFinite(sdg.height())) {
            throw new IllegalArgumentException("a canvas " + sdg.width() + " × " + sdg.height());
        }
        for (Sdg.Layer l : sdg.layers()) {
            String n = "layer \"" + l.name() + "\"";
            if (!(l.opacity() >= 0 && l.opacity() <= 1)) {
                throw new IllegalArgumentException(n + " of opacity " + l.opacity() + ", outside 0 to 1");
            }
            Sdg.Stencil s = l.stencil();
            rect(sdg, n + "'s stencil", s.page(), s.x(), s.y(), s.width(), s.height(), s.originX(), s.originY(),
                    s.scale());
            if (s.channel() < 0 || s.channel() > 3) {
                throw new IllegalArgumentException(n + "'s stencil reads channel " + s.channel());
            }
            if (!(s.range() > 0) || !Double.isFinite(s.range())) {
                throw new IllegalArgumentException(n + "'s stencil has a range of " + s.range());
            }
            if (l.paint() instanceof Sdg.Paint.Sampled p) {
                rect(sdg, n + "'s paint", p.page(), p.x(), p.y(), p.width(), p.height(), p.originX(), p.originY(),
                        p.scale());
            }
        }
        return sdg;
    }

    private static void rect(Sdg sdg, String what, int page, int x, int y, int w, int h, double ox, double oy,
                             double scale) {
        if (page < 0 || page >= sdg.pages().size()) {
            throw new IllegalArgumentException(what + " reads page " + page + ", and there are "
                    + sdg.pages().size());
        }
        Sdg.Page p = sdg.pages().get(page);
        if (x < 0 || y < 0 || w <= 0 || h <= 0 || (long) x + w > p.width() || (long) y + h > p.height()) {
            throw new IllegalArgumentException(what + " is " + w + " × " + h + " at (" + x + ", " + y
                    + "), past its page's " + p.width() + " × " + p.height());
        }
        if (!Double.isFinite(ox) || !Double.isFinite(oy) || !(scale > 0) || !Double.isFinite(scale)) {
            throw new IllegalArgumentException(what + " is placed at (" + ox + ", " + oy + ") at a scale of "
                    + scale);
        }
    }

    // ------------------------------------------------------------------------------------------------- bytes

    private static boolean startsWith(byte[] b, byte[] prefix) {
        return b.length >= prefix.length && Arrays.equals(b, 0, prefix.length, prefix, 0, prefix.length);
    }

    private static String escapeHref(String name) {
        try {
            return new java.net.URI(null, null, name, null).getRawPath().replace("&", "&amp;").replace("\"",
                    "&quot;").replace("<", "&lt;");
        } catch (java.net.URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static void replace(Path file, byte[] bytes) throws IOException {
        Path dir = file.toAbsolutePath().getParent();
        Path temp = Files.createTempFile(dir, file.getFileName().toString(), ".part");
        try {
            Files.write(temp, bytes);
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    /** {@link Sdg} to and from the intrabuf schema's records. */
    static final class SchemaMapping {

        private SchemaMapping() {
        }

        static Drawing toSchema(Sdg sdg) {
            List<dev.vexelray.fields.sdg.schema.Page> pages = new ArrayList<>();
            for (Sdg.Page p : sdg.pages()) {
                pages.add(new dev.vexelray.fields.sdg.schema.Page(Png.encode(p)));
            }
            List<dev.vexelray.fields.sdg.schema.Layer> layers = new ArrayList<>();
            for (Sdg.Layer l : sdg.layers()) {
                Sdg.Stencil s = l.stencil();
                var stencil = new dev.vexelray.fields.sdg.schema.Stencil(s.kind(), s.page(), s.channel(), s.x(),
                        s.y(), s.width(), s.height(), s.originX(), s.originY(), s.scale(), (float) s.range());
                var paint = switch (l.paint()) {
                    case Sdg.Paint.Solid c -> new dev.vexelray.fields.sdg.schema.Paint("solid",
                            new dev.vexelray.fields.sdg.schema.Solid(c.rgba(), c.role().isEmpty() ? null : c.role()),
                            null);
                    case Sdg.Paint.Sampled p -> new dev.vexelray.fields.sdg.schema.Paint("sampled", null,
                            new dev.vexelray.fields.sdg.schema.Sampled(p.page(), p.x(), p.y(), p.width(),
                                    p.height(), p.originX(), p.originY(), p.scale()));
                    case Sdg.Paint.Unknown u -> throw new IllegalArgumentException(
                            "a paint of kind " + u.kind() + " has nothing to write");
                };
                layers.add(new dev.vexelray.fields.sdg.schema.Layer(l.name(), l.opacity(), stencil, paint));
            }
            return new Drawing(sdg.width(), sdg.height(), pages, layers);
        }

        static Sdg fromSchema(Drawing d) {
            List<Sdg.Page> pages = new ArrayList<>();
            for (var p : d.pages()) {
                pages.add(Png.decode(p.png()));
            }
            List<Sdg.Layer> layers = new ArrayList<>();
            for (var l : d.layers()) {
                var s = l.stencil();
                Sdg.Stencil stencil = new Sdg.Stencil(s.kind(), s.page(), s.channel(), s.x(), s.y(), s.width(),
                        s.height(), s.originX(), s.originY(), s.scale(), s.range());
                var p = l.paint();
                Sdg.Paint paint = switch (p.kind()) {
                    case "solid" -> {
                        if (p.solid() == null) {
                            throw new IllegalArgumentException("layer \"" + l.name() + "\": a solid paint with no colour");
                        }
                        yield new Sdg.Paint.Solid(p.solid().rgba(), p.solid().role());
                    }
                    case "sampled" -> {
                        var t = p.sampled();
                        if (t == null) {
                            throw new IllegalArgumentException("layer \"" + l.name()
                                    + "\": a sampled paint with no texture");
                        }
                        yield new Sdg.Paint.Sampled(t.page(), t.x(), t.y(), t.width(), t.height(), t.originX(),
                                t.originY(), t.scale());
                    }
                    default -> new Sdg.Paint.Unknown(p.kind());
                };
                layers.add(new Sdg.Layer(l.name(), stencil, paint, l.opacity()));
            }
            return new Sdg(d.width(), d.height(), pages, layers);
        }
    }
}
