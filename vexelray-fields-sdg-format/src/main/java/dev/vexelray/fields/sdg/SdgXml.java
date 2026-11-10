package dev.vexelray.fields.sdg;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * The XML form, {@code .sdg}: {@code docs/sdg.md} §4.1.
 *
 * <p>Read as a file from anyone may be: no DTD, no entity, nothing fetched. A page's {@code href} is a {@code data:}
 * URI, or a plain relative path that the caller resolves (beside the file, and never above it); nothing else.
 */
final class SdgXml {

    static final String NS = "https://sibarum.github.io/ns/sdg";
    private static final String DATA = "data:image/png;base64,";

    private SdgXml() {
    }

    // ------------------------------------------------------------------------------------------------- writing

    /**
     * @param href what to write for page {@code i}'s {@code href}, given its PNG: a data URI, or the name of a file
     *             the caller writes it to
     */
    static String write(Sdg sdg, PageOut href) {
        StringBuilder s = new StringBuilder(1024);
        s.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        s.append("<sdg xmlns=\"").append(NS).append("\" version=\"").append(Sdg.MAJOR).append('.').append(Sdg.MINOR)
                .append("\" width=\"").append(num(sdg.width())).append("\" height=\"").append(num(sdg.height()))
                .append("\">\n");
        for (int i = 0; i < sdg.pages().size(); i++) {
            Sdg.Page p = sdg.pages().get(i);
            s.append("  <page id=\"").append(id(i)).append("\" width=\"").append(p.width()).append("\" height=\"")
                    .append(p.height()).append("\" href=\"").append(href.href(i, Png.encode(p))).append("\"/>\n");
        }
        for (int i = 0; i < sdg.fonts().size(); i++) {
            font(s, i, sdg.fonts().get(i));
        }
        layers(s, sdg.layers(), "  ");
        s.append("</sdg>\n");
        return s.toString();
    }

    /** A font (since 1.3): {@code <font>} holding its {@code <glyph>}s, in order, each a stencil's attributes or none. */
    private static void font(StringBuilder s, int index, Sdg.Font f) {
        s.append("  <font id=\"").append(fontId(index)).append("\" family=\"").append(escape(f.family()))
                .append("\" style=\"").append(escape(f.style())).append("\" ascender=\"").append(num(f.ascender()))
                .append("\" descender=\"").append(num(f.descender())).append("\" line-height=\"")
                .append(num(f.lineHeight())).append('"');
        if (!f.license().isEmpty()) {
            s.append(" license=\"").append(escape(f.license())).append('"');
        }
        s.append(">\n");
        for (Sdg.Glyph g : f.glyphs()) {
            s.append("    <glyph advance=\"").append(num(g.advance())).append('"');
            if (!g.codepoints().isEmpty()) {
                s.append(" codepoints=\"").append(escape(g.codepoints())).append('"');
            }
            if (g.field() != null) {
                stencilAttributes(s, g.field());
            }
            s.append("/>\n");
        }
        s.append("  </font>\n");
    }

    /** Text (since 1.3): {@code <text>} with its font, size, string and glyphs, holding its {@code <paint>}. */
    private static void text(StringBuilder s, Sdg.Layer l, String in) {
        Sdg.Text t = l.text();
        s.append(in).append("<text name=\"").append(escape(l.name())).append('"');
        if (l.opacity() != 1f) {
            s.append(" opacity=\"").append(num(l.opacity())).append('"');
        }
        s.append(" font=\"").append(fontId(t.font())).append("\" size=\"").append(num(t.size())).append('"');
        if (!t.string().isEmpty()) {
            s.append(" string=\"").append(escape(t.string())).append('"');
        }
        s.append(" glyphs=\"");
        for (int i = 0; i < t.glyphs().size(); i++) {
            Sdg.Placed p = t.glyphs().get(i);
            if (i > 0) {
                s.append("  ");
            }
            s.append(p.glyph()).append(' ').append(num(p.x())).append(' ').append(num(p.y()));
        }
        s.append("\">\n");
        paint(s, l.paint(), in + "  ");
        s.append(in).append("</text>\n");
    }

    static String fontId(int font) {
        return "f" + font;
    }

    /** {@code layers} at indent {@code in}: each a {@code <layer>}, or a {@code <group>} with its clip, mask and layers. */
    private static void layers(StringBuilder s, List<Sdg.Layer> layers, String in) {
        for (Sdg.Layer l : layers) {
            if (l.isGroup()) {
                group(s, l, in);
                continue;
            }
            if (l.isText()) {
                text(s, l, in);
                continue;
            }
            s.append(in).append("<layer name=\"").append(escape(l.name())).append('"');
            if (l.opacity() != 1f) {
                s.append(" opacity=\"").append(num(l.opacity())).append('"');
            }
            s.append(">\n");
            stencil(s, "stencil", l.stencil(), in + "  ");
            paint(s, l.paint(), in + "  ");
            s.append(in).append("</layer>\n");
        }
    }

    private static void paint(StringBuilder s, Sdg.Paint paint, String in) {
        switch (paint) {
            case Sdg.Paint.Solid c -> {
                s.append(in).append("<paint kind=\"solid\" colour=\"#").append(String.format(Locale.ROOT,
                        "%08x", c.rgba())).append('"');
                if (!c.role().isEmpty()) {
                    s.append(" role=\"").append(escape(c.role())).append('"');
                }
                s.append("/>\n");
            }
            case Sdg.Paint.Sampled p -> {
                s.append(in).append("<paint kind=\"sampled\" page=\"").append(id(p.page())).append('"');
                rect(s, p.x(), p.y(), p.width(), p.height(), p.originX(), p.originY(), p.scale());
                s.append("/>\n");
            }
            case Sdg.Paint.Unknown u -> throw new IllegalArgumentException(
                    "a paint of kind " + u.kind() + " has nothing to write");
        }
    }

    /** A group (since 1.2): {@code <group>} holding its {@code <clip>}, its {@code <mask>} and its layers, in order. */
    private static void group(StringBuilder s, Sdg.Layer l, String in) {
        s.append(in).append("<group name=\"").append(escape(l.name())).append('"');
        if (l.opacity() != 1f) {
            s.append(" opacity=\"").append(num(l.opacity())).append('"');
        }
        s.append(">\n");
        Sdg.Group g = l.group();
        if (g.clip() != null) {
            stencil(s, "clip", g.clip(), in + "  ");
        }
        if (g.mask() != null) {
            s.append(in).append("  <mask kind=\"").append(escape(g.mask().kind())).append("\">\n");
            layers(s, g.mask().layers(), in + "    ");
            s.append(in).append("  </mask>\n");
        }
        layers(s, g.layers(), in + "  ");
        s.append(in).append("</group>\n");
    }

    private static void stencil(StringBuilder s, String element, Sdg.Stencil t, String in) {
        s.append(in).append('<').append(element);
        stencilAttributes(s, t);
        s.append("/>\n");
    }

    private static void stencilAttributes(StringBuilder s, Sdg.Stencil t) {
        s.append(" kind=\"").append(escape(t.kind())).append("\" page=\"").append(id(t.page())).append('"');
        if (t.channel() != 0) {
            s.append(" channel=\"").append(t.channel()).append('"');
        }
        rect(s, t.x(), t.y(), t.width(), t.height(), t.originX(), t.originY(), t.scale());
        s.append(" range=\"").append(num((float) t.range())).append('"');
    }

    /** Where a page goes: its {@code href}. */
    interface PageOut {
        String href(int index, byte[] png);
    }

    /** Every page in the file, as a data URI, its base64 in lines of 100 so an editor shows it. */
    static String inline(int index, byte[] png) {
        String b = Base64.getEncoder().encodeToString(png);
        StringBuilder s = new StringBuilder(DATA.length() + b.length() + b.length() / 50);
        s.append(DATA);
        for (int i = 0; i < b.length(); i += 100) {
            s.append("\n      ").append(b, i, Math.min(b.length(), i + 100));
        }
        return s.toString();
    }

    private static void rect(StringBuilder s, int x, int y, int w, int h, double ox, double oy, double scale) {
        s.append(" x=\"").append(x).append("\" y=\"").append(y).append("\" width=\"").append(w)
                .append("\" height=\"").append(h).append("\" origin=\"").append(num(ox)).append(' ').append(num(oy))
                .append("\" scale=\"").append(num(scale)).append('"');
    }

    static String id(int page) {
        return "p" + page;
    }

    /** The shortest decimal that reads back as the same double; a whole number without its {@code .0}. */
    private static String num(double v) {
        if (v == Math.rint(v) && Math.abs(v) < 1e15) {
            return Long.toString((long) v);
        }
        return Double.toString(v);
    }

    private static String num(float v) {
        if (v == Math.rint(v) && Math.abs(v) < 1e7) {
            return Long.toString((long) v);
        }
        return Float.toString(v);
    }

    private static String escape(String v) {
        StringBuilder s = new StringBuilder(v.length());
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            switch (c) {
                case '&' -> s.append("&amp;");
                case '<' -> s.append("&lt;");
                case '"' -> s.append("&quot;");
                case '>' -> s.append("&gt;");
                case '\t' -> s.append("&#9;");
                case '\n' -> s.append("&#10;");
                case '\r' -> s.append("&#13;");
                default -> {
                    boolean pair = Character.isHighSurrogate(c) && i + 1 < v.length()
                            && Character.isLowSurrogate(v.charAt(i + 1));
                    if (pair) {
                        s.append(c).append(v.charAt(++i));
                    } else if (c < 0x20 || Character.isSurrogate(c) || c == 0xFFFE || c == 0xFFFF) {
                        throw new IllegalArgumentException(String.format(Locale.ROOT,
                                "\"%s\" holds U+%04X, which XML cannot carry", v, (int) c));
                    } else {
                        s.append(c);
                    }
                }
            }
        }
        return s.toString();
    }

    // ------------------------------------------------------------------------------------------------- reading

    /**
     * @param pages the PNG at a relative {@code href}, or null when there is nowhere to look; it is given the path
     *              already checked to stay beside the file
     */
    static Sdg read(byte[] xml, Function<String, byte[]> pages) {
        Element root = parse(xml).getDocumentElement();
        if (!NS.equals(root.getNamespaceURI()) || !"sdg".equals(root.getLocalName())) {
            throw new IllegalArgumentException("not an SDG drawing: its root is <" + root.getTagName()
                    + "> in " + (root.getNamespaceURI() == null ? "no namespace" : root.getNamespaceURI())
                    + ", not <sdg> in " + NS);
        }
        String version = required(root, "version");
        int dot = version.indexOf('.');
        int major;
        try {
            major = Integer.parseInt(dot < 0 ? version : version.substring(0, dot));
            if (dot >= 0) {
                Integer.parseInt(version.substring(dot + 1));
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("an SDG drawing of version \"" + version + "\"");
        }
        if (major != Sdg.MAJOR) {
            throw new IllegalArgumentException("an SDG drawing of version " + version + ", this reads "
                    + Sdg.MAJOR + ".x");
        }
        double width = real(root, "width", Double.NaN), height = real(root, "height", Double.NaN);

        Map<String, Integer> ids = new HashMap<>(), fontIds = new HashMap<>();
        List<Sdg.Page> pageList = new ArrayList<>();
        List<Sdg.Font> fonts = new ArrayList<>();
        List<Sdg.Layer> layers = new ArrayList<>();
        for (Element e : children(root)) {
            switch (e.getLocalName()) {
                case "page" -> {
                    String id = required(e, "id");
                    if (ids.putIfAbsent(id, pageList.size()) != null) {
                        throw new IllegalArgumentException("two pages called " + id);
                    }
                    Sdg.Page p = Png.decode(href(id, required(e, "href"), pages));
                    if (e.hasAttribute("width") && integer(e, "width", 0) != p.width()
                            || e.hasAttribute("height") && integer(e, "height", 0) != p.height()) {
                        throw new IllegalArgumentException("page " + id + " says it is " + e.getAttribute("width")
                                + " × " + e.getAttribute("height") + ", and its picture is " + p.width() + " × "
                                + p.height());
                    }
                    pageList.add(p);
                }
                case "font" -> {
                    String id = required(e, "id");
                    if (fontIds.putIfAbsent(id, fonts.size()) != null) {
                        throw new IllegalArgumentException("two fonts called " + id);
                    }
                    fonts.add(font(e, ids));
                }
                case "layer" -> layers.add(layer(e, ids));
                case "group" -> layers.add(group(e, ids, fontIds, 1));
                case "text" -> layers.add(text(e, ids, fontIds));
                default -> {
                    // an element this reader does not know: skipped
                }
            }
        }
        return new Sdg(width, height, pageList, layers, fonts);
    }

    /** A {@code <font>} (since 1.3): its metrics, and its {@code <glyph>}s in order. */
    private static Sdg.Font font(Element e, Map<String, Integer> ids) {
        List<Sdg.Glyph> glyphs = new ArrayList<>();
        for (Element g : children(e)) {
            if (!g.getLocalName().equals("glyph")) {
                continue;   // an element this reader does not know: skipped
            }
            glyphs.add(new Sdg.Glyph(g.hasAttribute("kind") ? stencil(g, ids) : null, real(g, "advance", 0),
                    g.getAttribute("codepoints")));
        }
        return new Sdg.Font(e.getAttribute("family"), e.getAttribute("style"), real(e, "ascender", 0),
                real(e, "descender", 0), real(e, "line-height", 0), glyphs, e.getAttribute("license"));
    }

    /** A {@code <text>} (since 1.3): its font, size, string and glyphs, and the one {@code <paint>} it holds. */
    private static Sdg.Layer text(Element e, Map<String, Integer> ids, Map<String, Integer> fontIds) {
        String name = e.hasAttribute("name") ? e.getAttribute("name") : "";
        String fontId = required(e, "font");
        Integer font = fontIds.get(fontId);
        if (font == null) {
            throw new IllegalArgumentException("text " + name + " is set in font " + fontId
                    + ", which no <font> before it is");
        }
        String all = e.getAttribute("glyphs").strip();
        String[] parts = all.isEmpty() ? new String[0] : all.split("[\\s,]+");
        if (parts.length % 3 != 0) {
            throw new IllegalArgumentException("text " + name + "'s glyphs \"" + all
                    + "\": each is a glyph and its x and y");
        }
        List<Sdg.Placed> placed = new ArrayList<>(parts.length / 3);
        for (int i = 0; i < parts.length; i += 3) {
            int glyph;
            try {
                glyph = Integer.parseInt(parts[i]);
            } catch (NumberFormatException x) {
                throw new IllegalArgumentException("text " + name + " sets glyph \"" + parts[i]
                        + "\", not a whole number");
            }
            placed.add(new Sdg.Placed(glyph, number(parts[i + 1], "glyph's x"), number(parts[i + 2], "glyph's y")));
        }
        Sdg.Text text = new Sdg.Text(font, real(e, "size", Double.NaN), placed, e.getAttribute("string"));
        return Sdg.Layer.text(name, (float) real(e, "opacity", 1), paint(only(e, "paint", name), ids), text);
    }

    /** A {@code <group>} {@code depth} deep: its {@code <clip>}, its {@code <mask>}, and its layers and groups in order. */
    private static Sdg.Layer group(Element e, Map<String, Integer> ids, Map<String, Integer> fontIds, int depth) {
        String name = e.hasAttribute("name") ? e.getAttribute("name") : "";
        if (depth > Sdg.Group.MAX_DEPTH) {
            throw new IllegalArgumentException("group " + name + " nests groups past " + Sdg.Group.MAX_DEPTH + " deep");
        }
        float opacity = (float) real(e, "opacity", 1);
        Sdg.Stencil clip = null;
        Sdg.Mask mask = null;
        List<Sdg.Layer> layers = new ArrayList<>();
        for (Element c : children(e)) {
            switch (c.getLocalName()) {
                case "layer" -> layers.add(layer(c, ids));
                case "group" -> layers.add(group(c, ids, fontIds, depth + 1));
                case "text" -> layers.add(text(c, ids, fontIds));
                case "clip" -> {
                    if (clip != null) {
                        throw new IllegalArgumentException("group " + name + " has two of <clip>");
                    }
                    clip = stencil(c, ids);
                }
                case "mask" -> {
                    if (mask != null) {
                        throw new IllegalArgumentException("group " + name + " has two of <mask>");
                    }
                    List<Sdg.Layer> m = new ArrayList<>();
                    for (Element ml : children(c)) {
                        switch (ml.getLocalName()) {
                            case "layer" -> m.add(layer(ml, ids));
                            case "group" -> m.add(group(ml, ids, fontIds, depth + 1));
                            case "text" -> m.add(text(ml, ids, fontIds));
                            default -> {
                                // skipped, as at the top
                            }
                        }
                    }
                    mask = new Sdg.Mask(c.hasAttribute("kind") ? c.getAttribute("kind") : Sdg.Mask.LUMINANCE, m);
                }
                default -> {
                    // an element this reader does not know: skipped
                }
            }
        }
        return Sdg.Layer.group(name, opacity, new Sdg.Group(layers, clip, mask));
    }

    private static Sdg.Stencil stencil(Element st, Map<String, Integer> ids) {
        return new Sdg.Stencil(required(st, "kind"), page(st, ids), integer(st, "channel", 0),
                integer(st, "x", -1), integer(st, "y", -1), integer(st, "width", -1), integer(st, "height", -1),
                pair(st, 0), pair(st, 1), real(st, "scale", 1), (float) real(st, "range", Double.NaN));
    }

    private static Sdg.Layer layer(Element e, Map<String, Integer> ids) {
        String name = e.hasAttribute("name") ? e.getAttribute("name") : "";
        float opacity = (float) real(e, "opacity", 1);
        Element st = only(e, "stencil", name), pt = only(e, "paint", name);
        return new Sdg.Layer(name, stencil(st, ids), paint(pt, ids), opacity);
    }

    private static Sdg.Paint paint(Element pt, Map<String, Integer> ids) {
        String kind = required(pt, "kind");
        return switch (kind) {
            case "solid" -> new Sdg.Paint.Solid(colour(pt), pt.getAttribute("role"));
            case "sampled" -> new Sdg.Paint.Sampled(page(pt, ids), integer(pt, "x", -1), integer(pt, "y", -1),
                    integer(pt, "width", -1), integer(pt, "height", -1), pair(pt, 0), pair(pt, 1),
                    real(pt, "scale", 1));
            default -> new Sdg.Paint.Unknown(kind);
        };
    }

    private static byte[] href(String id, String href, Function<String, byte[]> pages) {
        if (href.regionMatches(true, 0, "data:", 0, 5)) {
            if (!href.regionMatches(true, 0, DATA, 0, DATA.length())) {
                throw new IllegalArgumentException("page " + id + " is a data URI, and not of a base64 PNG");
            }
            String b = href.substring(DATA.length()).replaceAll("\\s+", "");
            try {
                return Base64.getDecoder().decode(b);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("page " + id + "'s base64 is broken: " + e.getMessage(), e);
            }
        }
        String path;
        try {
            URI u = new URI(href);
            if (u.isAbsolute() || u.getRawAuthority() != null || u.getRawQuery() != null
                    || u.getRawFragment() != null) {
                throw new IllegalArgumentException("page " + id + " is at " + href
                        + ": a page is a data URI or a path relative to the drawing");
            }
            path = u.getPath();
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("page " + id + " is at " + href + ", which is not a URI reference");
        }
        if (path.isEmpty() || path.startsWith("/") || path.contains("\\") || path.indexOf(':') >= 0) {
            throw new IllegalArgumentException("page " + id + " is at " + href
                    + ": a page is a data URI or a path relative to the drawing");
        }
        for (String segment : path.split("/")) {
            if (segment.equals("..")) {
                throw new IllegalArgumentException("page " + id + " is at " + href
                        + ", above the drawing; a page is beside it or below");
            }
        }
        if (pages == null) {
            throw new IllegalArgumentException("page " + id + " is in a file, " + path
                    + ", beside the drawing; read the drawing by its path");
        }
        return pages.apply(path);
    }

    private static int page(Element e, Map<String, Integer> ids) {
        String id = required(e, "page");
        Integer i = ids.get(id);
        if (i == null) {
            throw new IllegalArgumentException("<" + e.getLocalName() + "> reads page " + id
                    + ", which no <page> before it is");
        }
        return i;
    }

    private static int colour(Element e) {
        String v = required(e, "colour").strip();
        if (v.startsWith("#")) {
            String h = v.substring(1);
            try {
                switch (h.length()) {
                    case 3, 4 -> {
                        StringBuilder full = new StringBuilder(8);
                        for (char c : h.toCharArray()) {
                            full.append(c).append(c);
                        }
                        if (h.length() == 3) {
                            full.append("ff");
                        }
                        return Integer.parseUnsignedInt(full.toString(), 16);
                    }
                    case 6 -> {
                        return Integer.parseUnsignedInt(h + "ff", 16);
                    }
                    case 8 -> {
                        return Integer.parseUnsignedInt(h, 16);
                    }
                    default -> {
                    }
                }
            } catch (NumberFormatException ignored) {
                // falls through to the message below
            }
        }
        throw new IllegalArgumentException("a colour \"" + v + "\": one is #rgb, #rgba, #rrggbb or #rrggbbaa");
    }

    private static double pair(Element e, int which) {
        if (!e.hasAttribute("origin")) {
            return 0;
        }
        String[] parts = e.getAttribute("origin").strip().split("[\\s,]+");
        if (parts.length != 2) {
            throw new IllegalArgumentException("an origin \"" + e.getAttribute("origin") + "\": one is two numbers");
        }
        return number(parts[which], "origin");
    }

    private static double real(Element e, String name, double fallback) {
        if (!e.hasAttribute(name)) {
            if (Double.isNaN(fallback)) {
                throw new IllegalArgumentException("<" + e.getLocalName() + "> with no " + name);
            }
            return fallback;
        }
        return number(e.getAttribute(name), name);
    }

    private static double number(String v, String name) {
        String t = v.strip();
        // Java's parser takes "1d", "0x1p3" and "Infinity" too; a file's number is decimal, and finite
        if (!t.matches("[-+]?(\\d+\\.?\\d*|\\.\\d+)([eE][-+]?\\d+)?")) {
            throw new IllegalArgumentException("a " + name + " \"" + v + "\", not a number");
        }
        double d = Double.parseDouble(t);
        if (!Double.isFinite(d)) {
            throw new IllegalArgumentException("a " + name + " \"" + v + "\", too large");
        }
        return d;
    }

    private static int integer(Element e, String name, int fallback) {
        if (!e.hasAttribute(name)) {
            if (fallback < 0) {
                throw new IllegalArgumentException("<" + e.getLocalName() + "> with no " + name);
            }
            return fallback;
        }
        String v = e.getAttribute(name).strip();
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException x) {
            throw new IllegalArgumentException("a " + name + " \"" + v + "\", not a whole number");
        }
    }

    private static String required(Element e, String name) {
        if (!e.hasAttribute(name)) {
            throw new IllegalArgumentException("<" + e.getLocalName() + "> with no " + name);
        }
        return e.getAttribute(name);
    }

    /** Exactly one child {@code <name>} of SDG's namespace. */
    private static Element only(Element layer, String name, String layerName) {
        Element found = null;
        for (Element c : children(layer)) {
            if (c.getLocalName().equals(name)) {
                if (found != null) {
                    throw new IllegalArgumentException(layer.getLocalName() + " " + layerName + " has two of <"
                            + name + ">");
                }
                found = c;
            }
        }
        if (found == null) {
            throw new IllegalArgumentException(layer.getLocalName() + " " + layerName + " has no <" + name + ">");
        }
        return found;
    }

    /** The element children in SDG's namespace; any other namespace's are another application's, and skipped. */
    private static List<Element> children(Element e) {
        List<Element> out = new ArrayList<>();
        for (Node n = e.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element c && NS.equals(c.getNamespaceURI())) {
                out.add(c);
            }
        }
        return out;
    }

    private static Document parse(byte[] xml) {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setFeature("http://xml.org/sax/features/external-general-entities", false);
            f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            f.setXIncludeAware(false);
            f.setExpandEntityReferences(false);
            DocumentBuilder b = f.newDocumentBuilder();
            b.setErrorHandler(new ErrorHandler() {
                @Override
                public void warning(SAXParseException e) {
                }

                @Override
                public void error(SAXParseException e) throws SAXException {
                    throw e;
                }

                @Override
                public void fatalError(SAXParseException e) throws SAXException {
                    throw e;
                }
            });
            return b.parse(new InputSource(new ByteArrayInputStream(xml)));
        } catch (SAXParseException e) {
            throw new IllegalArgumentException("an SDG drawing that is not well-formed XML, at line "
                    + e.getLineNumber() + ": " + e.getMessage(), e);
        } catch (SAXException e) {
            throw new IllegalArgumentException("an SDG drawing that is not well-formed XML: " + e.getMessage(), e);
        } catch (ParserConfigurationException | IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
