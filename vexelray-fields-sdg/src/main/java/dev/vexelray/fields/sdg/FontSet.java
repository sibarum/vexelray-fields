package dev.vexelray.fields.sdg;

import java.awt.Font;
import java.awt.FontFormatException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The fonts text is set in when an SVG becomes an SDG: TrueType and OpenType faces, each known by its family, weight
 * and slope, and one of them the default, for text in a generic family ({@code sans-serif}) or a family there is no face
 * of. {@link #EMPTY} has none: its text is set in the JDK's own {@code SansSerif}, which differs from one JDK to the
 * next, so a drawing that must come out the same everywhere names its fonts.
 *
 * <p>A face's family, style and license come from its {@code name} table, and whether it may be embedded from its
 * {@code OS/2} table's {@code fsType}: a face whose license restricts embedding, or forbids subsetting (an SDG holds
 * only the glyphs it uses), is refused unless {@link #allowRestricted} says otherwise.
 */
public final class FontSet {

    /** A face: the font, its family, its weight (100 to 900), whether it slopes, and its license. */
    public record Face(Font font, String family, String style, int weight, boolean italic, String license) {
    }

    /** No fonts: everything is set in the JDK's {@code SansSerif}. */
    public static final FontSet EMPTY = new FontSet(List.of(), false);

    private static final java.util.Set<String> GENERIC = java.util.Set.of("serif", "sans-serif", "monospace",
            "cursive", "fantasy", "system-ui", "ui-sans-serif", "ui-serif", "ui-monospace", "ui-rounded", "math",
            "emoji", "fangsong");

    private final List<Face> faces;
    private final boolean allowRestricted;

    private FontSet(List<Face> faces, boolean allowRestricted) {
        this.faces = List.copyOf(faces);
        this.allowRestricted = allowRestricted;
    }

    /** Faces read from TrueType or OpenType files; the first is the default. */
    public static FontSet of(Path... files) throws IOException {
        FontSet set = EMPTY;
        for (Path f : files) {
            set = set.with(Files.readAllBytes(f));
        }
        return set;
    }

    /** This set and a face read from a TrueType or OpenType file's bytes. */
    public FontSet with(byte[] file) {
        Font font;
        try {
            font = Font.createFont(Font.TRUETYPE_FONT, new ByteArrayInputStream(file));
        } catch (FontFormatException | IOException e) {
            throw new IllegalArgumentException("not a TrueType or OpenType font: " + e.getMessage(), e);
        }
        Tables t = Tables.read(file);
        String family = t.name(16, t.name(1, font.getFamily(Locale.ROOT)));
        String style = t.name(17, t.name(2, "Regular"));
        if (!allowRestricted && (t.fsType & 0x0302) != 0) {
            throw new IllegalArgumentException("the font " + family + " " + style + " may not be embedded as glyphs"
                    + " (its fsType is 0x" + Integer.toHexString(t.fsType) + ": "
                    + ((t.fsType & 0x0002) != 0 ? "restricted license" : (t.fsType & 0x0100) != 0
                    ? "no subsetting" : "bitmaps only") + "); FontSet.allowRestricted() to embed it anyway");
        }
        String copyright = t.name(0, ""), license = t.name(13, "");
        String notice = copyright.isEmpty() ? license : license.isEmpty() ? copyright : copyright + " " + license;
        int weight = t.weight > 0 ? t.weight : weightOf(style);
        boolean italic = t.italic || style.toLowerCase(Locale.ROOT).matches(".*(italic|oblique).*");
        List<Face> more = new ArrayList<>(faces);
        more.add(new Face(font, family, style, weight, italic, notice));
        return new FontSet(more, allowRestricted);
    }

    /** This set, taking faces whose license restricts embedding: for fonts one has the right to embed regardless. */
    public FontSet allowRestricted() {
        return new FontSet(faces, true);
    }

    public List<Face> faces() {
        return faces;
    }

    /**
     * The face for CSS's {@code font-family} list {@code families}, {@code weight} and {@code italic}: the first family
     * there is a face of, its face nearest in weight, sloped as asked if one is; a generic family takes the default. Null
     * when there is nothing to take: no family matched and the set is empty, or {@link #EMPTY}.
     *
     * @param missed told the families asked for that there was no face of, before the default was taken
     */
    Face pick(String families, int weight, boolean italic, java.util.function.Consumer<String> missed) {
        for (String raw : families.split(",")) {
            String name = raw.strip();
            if (name.length() >= 2 && (name.startsWith("'") && name.endsWith("'")
                    || name.startsWith("\"") && name.endsWith("\""))) {
                name = name.substring(1, name.length() - 1);
            }
            if (GENERIC.contains(name.toLowerCase(Locale.ROOT))) {
                return nearest(faces.isEmpty() ? List.of() : List.of(faces.getFirst().family), weight, italic);
            }
            Face f = nearest(List.of(name), weight, italic);
            if (f != null) {
                return f;
            }
        }
        if (!faces.isEmpty()) {
            missed.accept(families);
            return nearest(List.of(faces.getFirst().family), weight, italic);
        }
        return null;
    }

    private Face nearest(List<String> family, int weight, boolean italic) {
        Face best = null;
        int bestScore = Integer.MAX_VALUE;
        for (Face f : faces) {
            if (family.isEmpty() || !f.family.equalsIgnoreCase(family.getFirst())) {
                continue;
            }
            int score = Math.abs(f.weight - weight) + (f.italic == italic ? 0 : 10_000);
            if (score < bestScore) {
                best = f;
                bestScore = score;
            }
        }
        return best;
    }

    private static int weightOf(String style) {
        String s = style.toLowerCase(Locale.ROOT).replace(" ", "").replace("-", "");
        String[][] names = {{"extralight", "200"}, {"ultralight", "200"}, {"semibold", "600"}, {"demibold", "600"},
                {"extrabold", "800"}, {"ultrabold", "800"}, {"thin", "100"}, {"light", "300"}, {"medium", "500"},
                {"black", "900"}, {"heavy", "900"}, {"bold", "700"}};
        for (String[] n : names) {
            if (s.contains(n[0])) {
                return Integer.parseInt(n[1]);
            }
        }
        return 400;
    }

    /** What a font file's {@code name} and {@code OS/2} tables say about it. */
    private static final class Tables {
        final java.util.Map<Integer, String> names = new java.util.HashMap<>();
        int fsType, weight;
        boolean italic;

        String name(int id, String fallback) {
            String v = names.get(id);
            return v == null || v.isBlank() ? fallback : v.strip();
        }

        static Tables read(byte[] f) {
            Tables t = new Tables();
            try {
                int base = 0;
                if (u32(f, 0) == 0x74746366) {   // 'ttcf': a collection, its first font
                    base = (int) u32(f, 12);
                }
                int tables = u16(f, base + 4);
                for (int i = 0; i < tables; i++) {
                    int rec = base + 12 + i * 16;
                    long tag = u32(f, rec);
                    int off = (int) u32(f, rec + 8);
                    if (tag == 0x4F532F32) {   // 'OS/2'
                        t.weight = u16(f, off + 4);
                        t.fsType = u16(f, off + 8);
                        t.italic = (u16(f, off + 62) & 1) != 0;
                    } else if (tag == 0x6E616D65) {   // 'name'
                        int count = u16(f, off + 2), strings = off + u16(f, off + 4);
                        for (int r = 0; r < count; r++) {
                            int nr = off + 6 + r * 12;
                            int platform = u16(f, nr), encoding = u16(f, nr + 2), language = u16(f, nr + 4);
                            int id = u16(f, nr + 6), len = u16(f, nr + 8), at = strings + u16(f, nr + 10);
                            boolean unicode = platform == 0 || platform == 3 && (encoding == 1 || encoding == 10)
                                    && (language == 0x409 || !t.names.containsKey(id));
                            if (unicode) {
                                t.names.put(id, new String(f, at, len, StandardCharsets.UTF_16BE));
                            } else if (platform == 1 && encoding == 0 && !t.names.containsKey(id)) {
                                t.names.put(id, new String(f, at, len, StandardCharsets.ISO_8859_1));
                            }
                        }
                    }
                }
            } catch (ArrayIndexOutOfBoundsException | StringIndexOutOfBoundsException e) {
                throw new IllegalArgumentException("a font file whose tables are cut short", e);
            }
            return t;
        }

        private static int u16(byte[] b, int at) {
            return (b[at] & 0xFF) << 8 | b[at + 1] & 0xFF;
        }

        private static long u32(byte[] b, int at) {
            return (long) u16(b, at) << 16 | u16(b, at + 2);
        }
    }
}
