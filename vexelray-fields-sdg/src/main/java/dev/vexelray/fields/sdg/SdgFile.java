package dev.vexelray.fields.sdg;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * The {@code .sdg} file: eight bytes of signature, then chunks, each a four-letter tag, a length and that many bytes,
 * little-endian throughout. {@code docs/sdg.md} is the specification; this is its reference.
 *
 * <p><b>Files outlive the readers that read them, both ways.</b> A reader skips a chunk whose tag it does not know, and
 * the bytes past the end of a chunk it does know; a writer adds to the end of a chunk and never reorders it. A kind of
 * stencil or paint is a name, so a layer of a kind a reader does not know is read as not {@link Sdg.Layer#known()} and
 * skipped when drawn, and the rest of the drawing draws. A file whose major version is newer than the reader's is
 * refused whole.
 */
public final class SdgFile {

    /** {@code \x89SDG\r\n\x1A\n}: as PNG's, so a 7-bit channel or a rewrite of line endings is caught. */
    static final byte[] SIGNATURE = {(byte) 0x89, 'S', 'D', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    private static final String HEAD = "SDGH", PAGE = "PAGE", LAYER = "LAYR", END = "SEND";

    /** How a page's bytes are filtered before they are deflated. */
    private static final int RAW = 0, SUB = 1;

    private SdgFile() {
    }

    public static void write(Sdg sdg, Path file) throws IOException {
        Files.write(file, bytes(sdg));
    }

    public static Sdg read(Path file) throws IOException {
        return read(Files.readAllBytes(file));
    }

    public static byte[] bytes(Sdg sdg) {
        Out out = new Out();
        out.bytes(SIGNATURE);

        Out head = new Out();
        head.u16(Sdg.MAJOR);
        head.u16(Sdg.MINOR);
        head.f64(sdg.width());
        head.f64(sdg.height());
        head.u32(sdg.pages().size());
        head.u32(sdg.layers().size());
        out.chunk(HEAD, head);

        for (Sdg.Page p : sdg.pages()) {
            Out page = new Out();
            page.u16(p.width());
            page.u16(p.height());
            byte[] raw = deflate(p.rgba());
            byte[] sub = deflate(sub(p));
            boolean useSub = sub.length < raw.length;
            page.u8(useSub ? SUB : RAW);
            page.bytes(useSub ? sub : raw);
            out.chunk(PAGE, page);
        }

        for (Sdg.Layer l : sdg.layers()) {
            Out layer = new Out();
            layer.string(l.name());
            layer.f32(l.opacity());
            Sdg.Stencil s = l.stencil();
            Out stencil = new Out();
            stencil.string(s.kind());
            stencil.u16(s.page());
            stencil.u8(s.channel());
            stencil.u16(s.x());
            stencil.u16(s.y());
            stencil.u16(s.width());
            stencil.u16(s.height());
            stencil.f64(s.originX());
            stencil.f64(s.originY());
            stencil.f64(s.scale());
            stencil.f32((float) s.range());
            layer.block(stencil);
            Out paint = new Out();
            switch (l.paint()) {
                case Sdg.Paint.Solid solid -> {
                    paint.string("solid");
                    paint.u32(solid.rgba());
                }
                case Sdg.Paint.Sampled t -> {
                    paint.string("sampled");
                    paint.u16(t.page());
                    paint.u16(t.x());
                    paint.u16(t.y());
                    paint.u16(t.width());
                    paint.u16(t.height());
                    paint.f64(t.originX());
                    paint.f64(t.originY());
                    paint.f64(t.scale());
                }
                case Sdg.Paint.Unknown u -> throw new IllegalArgumentException(
                        "a paint of kind " + u.kind() + " has nothing to write");
            }
            layer.block(paint);
            out.chunk(LAYER, layer);
        }
        out.chunk(END, new Out());
        return out.toByteArray();
    }

    public static Sdg read(byte[] file) {
        if (file.length < SIGNATURE.length || !Arrays.equals(Arrays.copyOf(file, SIGNATURE.length), SIGNATURE)) {
            throw new IllegalArgumentException("not an SDG file: its signature is wrong");
        }
        ByteBuffer in = ByteBuffer.wrap(file, SIGNATURE.length, file.length - SIGNATURE.length)
                .slice().order(ByteOrder.LITTLE_ENDIAN);
        double width = 0, height = 0;
        boolean headed = false, ended = false;
        List<Sdg.Page> pages = new ArrayList<>();
        List<Sdg.Layer> layers = new ArrayList<>();
        while (in.remaining() >= 8 && !ended) {
            byte[] tag = new byte[4];
            in.get(tag);
            long length = Integer.toUnsignedLong(in.getInt());
            if (length > in.remaining()) {
                throw new IllegalArgumentException("a chunk runs past the end of the file");
            }
            ByteBuffer c = in.slice(in.position(), (int) length).order(ByteOrder.LITTLE_ENDIAN);
            in.position(in.position() + (int) length);
            String t = new String(tag, StandardCharsets.US_ASCII);
            switch (t) {
                case HEAD -> {
                    int major = u16(c);
                    u16(c);   // minor: what a newer writer may have added, past what this reads, is skipped
                    if (major != Sdg.MAJOR) {
                        throw new IllegalArgumentException("an SDG file of version " + major + ", this reads "
                                + Sdg.MAJOR);
                    }
                    width = c.getDouble();
                    height = c.getDouble();
                    headed = true;
                }
                case PAGE -> {
                    int w = u16(c), h = u16(c), filter = c.get() & 0xFF;
                    byte[] packed = new byte[c.remaining()];
                    c.get(packed);
                    byte[] rgba = inflate(packed, w * h * 4);
                    if (filter == SUB) {
                        unsub(rgba, w, h);
                    } else if (filter != RAW) {
                        throw new IllegalArgumentException("a page filtered by " + filter);
                    }
                    pages.add(new Sdg.Page(w, h, rgba));
                }
                case LAYER -> layers.add(layer(c));
                case END -> ended = true;
                default -> {
                    // a chunk this reader does not know: skipped
                }
            }
        }
        if (!headed || !ended) {
            throw new IllegalArgumentException("an SDG file " + (headed ? "cut short" : "with no header"));
        }
        for (Sdg.Layer l : layers) {
            if (l.stencil().page() >= pages.size()
                    || l.paint() instanceof Sdg.Paint.Sampled s && s.page() >= pages.size()) {
                throw new IllegalArgumentException("layer " + l.name() + " reads a page the file does not have");
            }
        }
        return new Sdg(width, height, pages, layers);
    }

    private static Sdg.Layer layer(ByteBuffer c) {
        String name = string(c);
        float opacity = c.getFloat();
        ByteBuffer s = block(c);
        String kind = string(s);
        // a stencil of a kind this does not know is still placed by the same fields, which every kind begins with
        Sdg.Stencil stencil = new Sdg.Stencil(kind, u16(s), s.get() & 0xFF, u16(s), u16(s), u16(s), u16(s),
                s.getDouble(), s.getDouble(), s.getDouble(), s.getFloat());
        ByteBuffer p = block(c);
        String paintKind = string(p);
        Sdg.Paint paint = switch (paintKind) {
            case "solid" -> new Sdg.Paint.Solid(p.getInt());
            case "sampled" -> new Sdg.Paint.Sampled(u16(p), u16(p), u16(p), u16(p), u16(p), p.getDouble(),
                    p.getDouble(), p.getDouble());
            default -> new Sdg.Paint.Unknown(paintKind);
        };
        return new Sdg.Layer(name, stencil, paint, opacity);
    }

    // ------------------------------------------------------------------------------------------------- the bytes

    /** Each byte less the one four before it in its row: smooth fields and colours become runs of small numbers. */
    private static byte[] sub(Sdg.Page p) {
        byte[] in = p.rgba(), out = new byte[in.length];
        int row = p.width() * 4;
        for (int i = 0; i < in.length; i++) {
            out[i] = (byte) (i % row < 4 ? in[i] : in[i] - in[i - 4]);
        }
        return out;
    }

    private static void unsub(byte[] b, int w, int h) {
        int row = w * 4;
        for (int i = 0; i < b.length; i++) {
            if (i % row >= 4) {
                b[i] = (byte) (b[i] + b[i - 4]);
            }
        }
    }

    private static byte[] deflate(byte[] data) {
        Deflater d = new Deflater(Deflater.BEST_COMPRESSION);
        try {
            d.setInput(data);
            d.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream(data.length / 4 + 64);
            byte[] buf = new byte[1 << 16];
            while (!d.finished()) {
                out.write(buf, 0, d.deflate(buf));
            }
            return out.toByteArray();
        } finally {
            d.end();
        }
    }

    private static byte[] inflate(byte[] data, int size) {
        Inflater f = new Inflater();
        try {
            f.setInput(data);
            byte[] out = new byte[size];
            int n = 0;
            while (n < size && !f.finished()) {
                int k = f.inflate(out, n, size - n);
                if (k == 0 && (f.needsInput() || f.needsDictionary())) {
                    break;
                }
                n += k;
            }
            if (n != size) {
                throw new IllegalArgumentException("a page of " + n + " bytes, where " + size + " were promised");
            }
            return out;
        } catch (DataFormatException e) {
            throw new IllegalArgumentException("a page whose bytes do not inflate: " + e.getMessage(), e);
        } finally {
            f.end();
        }
    }

    private static int u16(ByteBuffer c) {
        return c.getShort() & 0xFFFF;
    }

    /** A length and that many bytes, as a buffer of their own, so what a newer writer adds to one is skipped. */
    private static ByteBuffer block(ByteBuffer c) {
        int n = c.getInt();
        if (n < 0 || n > c.remaining()) {
            throw new IllegalArgumentException("a block runs past the end of its layer");
        }
        ByteBuffer b = c.slice(c.position(), n).order(ByteOrder.LITTLE_ENDIAN);
        c.position(c.position() + n);
        return b;
    }

    private static String string(ByteBuffer c) {
        byte[] b = new byte[u16(c)];
        c.get(b);
        return new String(b, StandardCharsets.UTF_8);
    }

    private static final class Out extends ByteArrayOutputStream {

        void u8(int v) {
            write(v);
        }

        void u16(int v) {
            if (v < 0 || v > 0xFFFF) {
                throw new IllegalArgumentException(v + " does not fit in 16 bits");
            }
            write(v);
            write(v >>> 8);
        }

        void u32(int v) {
            write(v);
            write(v >>> 8);
            write(v >>> 16);
            write(v >>> 24);
        }

        void f32(float v) {
            u32(Float.floatToIntBits(v));
        }

        void f64(double v) {
            long b = Double.doubleToLongBits(v);
            u32((int) b);
            u32((int) (b >>> 32));
        }

        void bytes(byte[] b) {
            write(b, 0, b.length);
        }

        void string(String s) {
            byte[] b = s.getBytes(StandardCharsets.UTF_8);
            u16(b.length);
            bytes(b);
        }

        void block(Out body) {
            u32(body.size());
            try {
                body.writeTo(this);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        void chunk(String tag, Out body) {
            bytes(tag.getBytes(StandardCharsets.US_ASCII));
            u32(body.size());
            try {
                body.writeTo(this);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
