package dev.vexelray.fields.atlas;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/**
 * An atlas's pixels as a run time loads them, with nothing but {@code java.util.zip}: {@code channels} bytes per
 * texel, top row first, deflated. The layout {@code vexelray-text}'s {@code AtlasPixels} reads and the stack's atlas
 * bakes write, byte for byte, so an atlas made here loads there. Four channels keep the original layout:
 *
 * <pre>
 *   4 bytes   magic "VXPX"                      4 bytes   magic "VXPC"
 *   int32     width, big-endian                 int32     width, big-endian
 *   int32     height, big-endian                int32     height, big-endian
 *   rest      a zlib stream of                  int32     channels, 1 or 4, big-endian
 *             width · height · 4 bytes,         rest      a zlib stream of width · height · channels bytes,
 *             R G B A per pixel                           R first, rows top to bottom
 * </pre>
 *
 * @param width    texels across
 * @param height   texels down
 * @param channels 1 (an {@code R8} image) or 4 ({@code R8G8B8A8})
 * @param texels   {@code width * height * channels} bytes
 */
public record AtlasPixels(int width, int height, int channels, byte[] texels) {

    /** "VXPX": four channels, no channel count. */
    public static final int MAGIC = 0x56585058;
    /** "VXPC": a channel count after the height. */
    public static final int MAGIC_CHANNELS = 0x56585043;

    public AtlasPixels {
        if (channels != 1 && channels != 4) {
            throw new IllegalArgumentException("atlas pixels are 1 or 4 channels, not " + channels);
        }
        if (width < 1 || height < 1 || texels.length != (long) width * height * channels) {
            throw new IllegalArgumentException("an atlas of " + width + " x " + height + " at " + channels
                    + " channels needs " + (long) width * height * channels + " bytes, got " + texels.length);
        }
    }

    /** RGBA8 texels as atlas pixels. */
    public static AtlasPixels rgba(int width, int height, byte[] rgba) {
        return new AtlasPixels(width, height, 4, rgba);
    }

    /** The file's bytes: header, then the texels at the best compression. */
    public byte[] bytes() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            write(bytes);
        } catch (IOException e) {
            throw new IllegalStateException(e);   // a byte array does not fail
        }
        return bytes.toByteArray();
    }

    /** Writes the file to {@code out}, and does not close it. */
    public void write(OutputStream out) throws IOException {
        DataOutputStream header = new DataOutputStream(out);
        header.writeInt(channels == 4 ? MAGIC : MAGIC_CHANNELS);
        header.writeInt(width);
        header.writeInt(height);
        if (channels != 4) {
            header.writeInt(channels);
        }
        header.flush();
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        try {
            DeflaterOutputStream z = new DeflaterOutputStream(out, deflater, 1 << 16);
            z.write(texels);
            z.finish();
            z.flush();
        } finally {
            deflater.end();
        }
    }

    /**
     * Writes the file at {@code path} if its bytes differ from what is there, beside it first and moved into place,
     * so a bake that changed nothing leaves the file's time alone and one that dies half-way never leaves it cut short.
     * Whether it wrote.
     */
    public boolean write(Path path) throws IOException {
        return writeIfChanged(path, bytes());
    }

    /** {@code bytes} at {@code path}, as {@link #write(Path)} writes. Whether it wrote. */
    public static boolean writeIfChanged(Path path, byte[] bytes) throws IOException {
        if (Files.exists(path) && Arrays.equals(Files.readAllBytes(path), bytes)) {
            return false;
        }
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path part = path.resolveSibling(path.getFileName() + ".part");
        Files.write(part, bytes);
        Files.move(part, path, StandardCopyOption.REPLACE_EXISTING);
        return true;
    }

    /** Reads either layout from {@code in}, and does not close it. */
    public static AtlasPixels read(InputStream in) throws IOException {
        DataInputStream header = new DataInputStream(in);
        int magic = header.readInt();
        if (magic != MAGIC && magic != MAGIC_CHANNELS) {
            throw new IOException(String.format("not atlas pixels: magic %08x, expected %08x or %08x", magic, MAGIC,
                    MAGIC_CHANNELS));
        }
        int width = header.readInt();
        int height = header.readInt();
        int channels = magic == MAGIC ? 4 : header.readInt();
        if (channels != 1 && channels != 4) {
            throw new IOException("atlas pixels of " + channels + " channels; this reads 1 or 4");
        }
        if (width < 1 || height < 1 || (long) width * height * channels > Integer.MAX_VALUE - 8) {
            throw new IOException("atlas pixels of an impossible size, " + width + " x " + height);
        }
        byte[] texels = new byte[width * height * channels];
        InflaterInputStream body = new InflaterInputStream(in);
        int at = 0;
        while (at < texels.length) {
            int n = body.read(texels, at, texels.length - at);
            if (n < 0) {
                throw new EOFException("atlas pixels end after " + at + " of " + texels.length + " bytes");
            }
            at += n;
        }
        return new AtlasPixels(width, height, channels, texels);
    }
}
