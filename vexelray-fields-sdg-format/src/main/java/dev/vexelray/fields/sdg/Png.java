package dev.vexelray.fields.sdg;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Pages as PNG, both ways, with nothing but {@code java.util.zip}, so this module needs no {@code java.desktop}.
 *
 * <p>Writes 8-bit RGBA, each row filtered by whichever of PNG's five filters leaves the smallest sum of magnitudes
 * (the usual heuristic), deflated at the best compression. Reads 8-bit greyscale, grey and alpha, RGB, RGBA and
 * palette images, so a page saved from an image editor reads; refuses other depths and interlacing by name. A page's
 * bytes are taken as they are stored: gamma, colour profiles and the like are not applied, half of a page being
 * distances.
 */
final class Png {

    private static final byte[] SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    /** The most bytes a page may unpack to: what one Java array holds, less a margin. */
    private static final long MAX_BYTES = Integer.MAX_VALUE - 64;

    private Png() {
    }

    static byte[] encode(Sdg.Page page) {
        int w = page.width(), h = page.height(), stride = w * 4;
        byte[] rgba = page.rgba();
        byte[] filtered = new byte[h * (stride + 1)];
        byte[] row = new byte[stride], best = new byte[stride];
        for (int y = 0; y < h; y++) {
            int at = y * stride;
            long bestSum = Long.MAX_VALUE;
            int bestType = 0;
            for (int type = 0; type <= 4; type++) {
                long sum = 0;
                for (int i = 0; i < stride; i++) {
                    int x = rgba[at + i] & 0xFF;
                    int a = i >= 4 ? rgba[at + i - 4] & 0xFF : 0;
                    int b = y > 0 ? rgba[at - stride + i] & 0xFF : 0;
                    int c = y > 0 && i >= 4 ? rgba[at - stride + i - 4] & 0xFF : 0;
                    int v = switch (type) {
                        case 0 -> x;
                        case 1 -> x - a;
                        case 2 -> x - b;
                        case 3 -> x - ((a + b) >>> 1);
                        default -> x - paeth(a, b, c);
                    };
                    row[i] = (byte) v;
                    sum += Math.abs((byte) v);
                }
                if (sum < bestSum) {
                    bestSum = sum;
                    bestType = type;
                    System.arraycopy(row, 0, best, 0, stride);
                }
            }
            filtered[y * (stride + 1)] = (byte) bestType;
            System.arraycopy(best, 0, filtered, y * (stride + 1) + 1, stride);
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(SIGNATURE);
        ByteBuffer ihdr = ByteBuffer.allocate(13);
        ihdr.putInt(w).putInt(h).put((byte) 8).put((byte) 6).put((byte) 0).put((byte) 0).put((byte) 0);
        chunk(out, "IHDR", ihdr.array());
        chunk(out, "IDAT", deflate(filtered));
        chunk(out, "IEND", new byte[0]);
        return out.toByteArray();
    }

    static Sdg.Page decode(byte[] png) {
        if (png.length < SIGNATURE.length || !Arrays.equals(png, 0, SIGNATURE.length, SIGNATURE, 0, SIGNATURE.length)) {
            throw new IllegalArgumentException("a page that is not a PNG");
        }
        ByteBuffer in = ByteBuffer.wrap(png, SIGNATURE.length, png.length - SIGNATURE.length).slice();
        int w = 0, h = 0, type = -1;
        byte[] palette = null, trns = null;
        ByteArrayOutputStream idat = new ByteArrayOutputStream();
        boolean ended = false;
        while (!ended) {
            if (in.remaining() < 12) {
                throw new IllegalArgumentException("a PNG page cut short");
            }
            int length = in.getInt();
            if (length < 0 || length > in.remaining() - 8) {
                throw new IllegalArgumentException("a PNG chunk runs past the end of its page");
            }
            byte[] tagAndData = new byte[4 + length];
            in.get(tagAndData);
            CRC32 crc = new CRC32();
            crc.update(tagAndData);
            if ((int) crc.getValue() != in.getInt()) {
                throw new IllegalArgumentException("a PNG page whose bytes are damaged (a chunk's CRC is wrong)");
            }
            String tag = new String(tagAndData, 0, 4, StandardCharsets.US_ASCII);
            ByteBuffer data = ByteBuffer.wrap(tagAndData, 4, length);
            switch (tag) {
                case "IHDR" -> {
                    w = data.getInt();
                    h = data.getInt();
                    int depth = data.get() & 0xFF;
                    type = data.get() & 0xFF;
                    int compression = data.get(), filter = data.get(), interlace = data.get();
                    if (w <= 0 || h <= 0 || w > 65535 || h > 65535) {
                        throw new IllegalArgumentException("a page " + w + " × " + h + ", past 65535 × 65535");
                    }
                    if (depth != 8 || compression != 0 || filter != 0 || interlace != 0
                            || !(type == 0 || type == 2 || type == 3 || type == 4 || type == 6)) {
                        throw new IllegalArgumentException("a PNG page of " + depth + " bits, colour type " + type
                                + (interlace != 0 ? ", interlaced" : "")
                                + ": a page is 8 bits a channel and not interlaced");
                    }
                }
                case "PLTE" -> palette = Arrays.copyOfRange(tagAndData, 4, 4 + length);
                case "tRNS" -> trns = Arrays.copyOfRange(tagAndData, 4, 4 + length);
                case "IDAT" -> idat.write(tagAndData, 4, length);
                case "IEND" -> ended = true;
                default -> {
                    if (Character.isUpperCase(tag.charAt(0))) {
                        throw new IllegalArgumentException("a PNG page with a critical chunk " + tag
                                + " this does not know");
                    }
                }
            }
        }
        if (type < 0) {
            throw new IllegalArgumentException("a PNG page with no header");
        }
        if (type == 3 && palette == null) {
            throw new IllegalArgumentException("a palette PNG page with no palette");
        }
        int channels = switch (type) {
            case 0, 3 -> 1;
            case 4 -> 2;
            case 2 -> 3;
            default -> 4;
        };
        if ((long) w * h * 4 > MAX_BYTES) {
            throw new IllegalArgumentException("a page " + w + " × " + h + ", too large to hold");
        }
        int stride = w * channels;
        byte[] raw = inflate(idat.toByteArray(), (long) h * (stride + 1));
        unfilter(raw, stride, channels, h);

        byte[] rgba = new byte[w * h * 4];
        for (int y = 0; y < h; y++) {
            int from = y * (stride + 1) + 1;
            for (int x = 0; x < w; x++) {
                int s = from + x * channels, d = (y * w + x) * 4;
                switch (type) {
                    case 0 -> {
                        byte g = raw[s];
                        rgba[d] = rgba[d + 1] = rgba[d + 2] = g;
                        rgba[d + 3] = trns != null && trns.length >= 2 && (g & 0xFF) == (trns[1] & 0xFF)
                                && trns[0] == 0 ? 0 : (byte) 255;
                    }
                    case 4 -> {
                        rgba[d] = rgba[d + 1] = rgba[d + 2] = raw[s];
                        rgba[d + 3] = raw[s + 1];
                    }
                    case 2 -> {
                        rgba[d] = raw[s];
                        rgba[d + 1] = raw[s + 1];
                        rgba[d + 2] = raw[s + 2];
                        boolean key = trns != null && trns.length >= 6 && trns[0] == 0 && trns[2] == 0 && trns[4] == 0
                                && raw[s] == trns[1] && raw[s + 1] == trns[3] && raw[s + 2] == trns[5];
                        rgba[d + 3] = key ? 0 : (byte) 255;
                    }
                    case 3 -> {
                        int i = raw[s] & 0xFF;
                        if (i * 3 + 2 >= palette.length) {
                            throw new IllegalArgumentException("a PNG page reads past its palette");
                        }
                        rgba[d] = palette[i * 3];
                        rgba[d + 1] = palette[i * 3 + 1];
                        rgba[d + 2] = palette[i * 3 + 2];
                        rgba[d + 3] = trns != null && i < trns.length ? trns[i] : (byte) 255;
                    }
                    default -> System.arraycopy(raw, s, rgba, d, 4);
                }
            }
        }
        return new Sdg.Page(w, h, rgba);
    }

    private static void unfilter(byte[] raw, int stride, int bpp, int h) {
        for (int y = 0; y < h; y++) {
            int at = y * (stride + 1);
            int type = raw[at] & 0xFF;
            int prev = at - (stride + 1);
            for (int i = 0; i < stride; i++) {
                int p = at + 1 + i;
                int a = i >= bpp ? raw[p - bpp] & 0xFF : 0;
                int b = y > 0 ? raw[prev + 1 + i] & 0xFF : 0;
                int c = y > 0 && i >= bpp ? raw[prev + 1 + i - bpp] & 0xFF : 0;
                int add = switch (type) {
                    case 0 -> 0;
                    case 1 -> a;
                    case 2 -> b;
                    case 3 -> (a + b) >>> 1;
                    case 4 -> paeth(a, b, c);
                    default -> throw new IllegalArgumentException("a PNG row filtered by " + type);
                };
                raw[p] = (byte) (raw[p] + add);
            }
        }
    }

    private static int paeth(int a, int b, int c) {
        int p = a + b - c, pa = Math.abs(p - a), pb = Math.abs(p - b), pc = Math.abs(p - c);
        return pa <= pb && pa <= pc ? a : pb <= pc ? b : c;
    }

    private static void chunk(ByteArrayOutputStream out, String tag, byte[] data) {
        byte[] t = tag.getBytes(StandardCharsets.US_ASCII);
        CRC32 crc = new CRC32();
        crc.update(t);
        crc.update(data);
        out.writeBytes(ByteBuffer.allocate(4).putInt(data.length).array());
        out.writeBytes(t);
        out.writeBytes(data);
        out.writeBytes(ByteBuffer.allocate(4).putInt((int) crc.getValue()).array());
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

    private static byte[] inflate(byte[] data, long size) {
        if (size > MAX_BYTES) {
            throw new IllegalArgumentException("a page too large to hold");
        }
        Inflater f = new Inflater();
        try {
            f.setInput(data);
            byte[] out = new byte[(int) size];
            int n = 0;
            while (n < size && !f.finished()) {
                int k = f.inflate(out, n, (int) size - n);
                if (k == 0 && (f.needsInput() || f.needsDictionary())) {
                    break;
                }
                n += k;
            }
            if (n != size) {
                throw new IllegalArgumentException("a PNG page of " + n + " bytes, where " + size + " were promised");
            }
            return out;
        } catch (DataFormatException e) {
            throw new IllegalArgumentException("a PNG page whose bytes do not inflate: " + e.getMessage(), e);
        } finally {
            f.end();
        }
    }
}
