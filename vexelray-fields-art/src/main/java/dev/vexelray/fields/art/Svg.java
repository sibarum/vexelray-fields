package dev.vexelray.fields.art;

import dev.vexelray.fields.shape.Bounds;
import dev.vexelray.fields.shape.Contour;
import dev.vexelray.fields.shape.Edge;
import dev.vexelray.fields.shape.Path;
import dev.vexelray.fields.shape.PathData;
import dev.vexelray.fields.shape.Shape;
import dev.vexelray.fields.shape.Vec2;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * An SVG drawing, read and drawn here in Java: what a field is made from when the art is vector. Two ways out of it.
 *
 * <ul>
 *   <li>{@link #render} draws it into a {@link Raster} at any size, its view box fitted in with its aspect kept and
 *       centred, every pixel sampled at its centre. Fills, strokes (caps, joins, miter limits, dashes), masks,
 *       clips, opacity and transforms are drawn as SVG means them, so art drawn as a mask with knockouts, as
 *       {@code vexelray-icons}' are, comes out as it does in a browser. A mask made from it is the shape at the
 *       resolution it is drawn at; drawn at a field's oversampling, that is as fine as a field can use.</li>
 *   <li>{@link #outline} is the drawing's filled area as a {@link Shape}, exactly, curves as curves, when the drawing
 *       is only opaque fills: what a multi-channel field needs, since it colours edges and a raster has none.</li>
 * </ul>
 *
 * <p>Drawn: {@code svg}, {@code g}, {@code a}, {@code use}, {@code path} (arcs too), {@code rect} (rounded),
 * {@code circle}, {@code ellipse}, {@code line}, {@code polyline}, {@code polygon}; {@code mask} and
 * {@code clipPath} in user space; {@code fill}, {@code stroke} and the stroke's properties, {@code fill-rule},
 * {@code clip-rule}, {@code opacity}, {@code fill-opacity}, {@code stroke-opacity}, {@code color},
 * {@code display}, {@code visibility}, and {@code transform}, as attributes or in a {@code style} attribute. Elements
 * in other namespaces (an editor's own) are passed over.
 *
 * <p>Text is left out, as {@code vexelray-gui}'s icon bake leaves it out of a silhouette: at the sizes a field is
 * made for, letters are a smear, and nothing here has fonts to set them in. A gradient paints as its stops' mean
 * colour at their mean opacity, so the shape it covers is exact when read by alpha, and only its colour is not.
 *
 * <p>Refused, by an {@link IllegalArgumentException} naming it, rather than drawn wrongly: images, patterns,
 * filters, markers, style sheets, nested {@code svg}, and masks or clips in bounding-box units. A mask's region in
 * bounding-box units, the default, is taken as unbounded; one in user space is kept to.
 */
public final class Svg {

    private static final String NS = "http://www.w3.org/2000/svg";

    private static final Set<String> SHAPES = Set.of("path", "rect", "circle", "ellipse", "line", "polyline",
            "polygon");

    /** Never drawn where they stand: definitions, and what describes the drawing. */
    private static final Set<String> QUIET = Set.of("defs", "mask", "clipPath", "symbol", "title", "desc", "metadata",
            "linearGradient", "radialGradient", "pattern", "filter", "marker", "text");

    private static final Set<String> REFUSED = Set.of("image", "foreignObject", "style", "svg", "switch", "script");

    private final Element root;
    private final Bounds viewBox;
    private final boolean stretch;
    private final Map<String, Element> ids = new HashMap<>();

    private Svg(Element root) {
        this.root = root;
        index(root);
        double[] vb = root.hasAttribute("viewBox") ? numbers(root.getAttribute("viewBox")) : null;
        if (vb != null) {
            if (vb.length != 4 || !(vb[2] > 0) || !(vb[3] > 0)) {
                throw new IllegalArgumentException("viewBox '" + root.getAttribute("viewBox") + "'");
            }
            viewBox = new Bounds(vb[0], vb[1], vb[0] + vb[2], vb[1] + vb[3]);
        } else {
            double w = length(root, "width", 0, 0), h = length(root, "height", 0, 0);
            if (!(w > 0) || !(h > 0)) {
                throw new IllegalArgumentException("an SVG with neither a viewBox nor a width and a height");
            }
            viewBox = new Bounds(0, 0, w, h);
        }
        String par = root.getAttribute("preserveAspectRatio").strip();
        stretch = par.equals("none");
        if (!par.isEmpty() && !stretch && !par.equals("xMidYMid") && !par.equals("xMidYMid meet")) {
            throw new IllegalArgumentException("preserveAspectRatio '" + par + "': only xMidYMid meet and none");
        }
    }

    /** The SVG document {@code text}. */
    public static Svg parse(String text) {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            f.setFeature("http://xml.org/sax/features/external-general-entities", false);
            f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            f.setExpandEntityReferences(false);
            f.setXIncludeAware(false);
            DocumentBuilder b = f.newDocumentBuilder();
            b.setErrorHandler(null);
            Document doc = b.parse(new InputSource(new StringReader(text)));
            Element root = doc.getDocumentElement();
            if (!"svg".equals(root.getLocalName())) {
                throw new IllegalArgumentException("not an SVG: its root is <" + root.getTagName() + ">");
            }
            return new Svg(root);
        } catch (ParserConfigurationException | SAXException | IOException e) {
            throw new IllegalArgumentException("not an SVG this can read: " + e.getMessage(), e);
        }
    }

    public static Svg read(java.nio.file.Path file) throws IOException {
        return parse(Files.readString(file, StandardCharsets.UTF_8));
    }

    /** The drawing's own frame, in its user units: what is fitted to a raster or a field. */
    public Bounds viewBox() {
        return viewBox;
    }

    /**
     * The drawing in a raster {@code width × height}: the view box fitted in, its aspect kept and centred (stretched to
     * fill, if the drawing says {@code preserveAspectRatio="none"}), on transparent.
     */
    public Raster render(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("a raster " + width + " × " + height);
        }
        Renderer r = new Renderer(width, height);
        Layer layer = new Layer(width, height);
        r.children(root, toPixels(width, height), Style.initial().with(root, this), layer);
        return layer.raster();
    }

    /** Whether the drawing is stretched to fill, by {@code preserveAspectRatio="none"}, rather than fitted. */
    boolean stretches() {
        return stretch;
    }

    /** Where the view box lands in a raster {@code width × height}. */
    Affine toPixels(int width, int height) {
        double sx = width / viewBox.width(), sy = height / viewBox.height();
        if (!stretch) {
            sx = sy = Math.min(sx, sy);
        }
        double ox = (width - viewBox.width() * sx) / 2, oy = (height - viewBox.height() * sy) / 2;
        return Affine.translate(ox, oy).times(Affine.scale(sx, sy))
                .times(Affine.translate(-viewBox.xMin(), -viewBox.yMin()));
    }

    /**
     * The filled area as a shape in the view box's units, y down, if the drawing is only fills of solid, opaque paint
     * by the nonzero rule: no stroke, mask, clip or opacity. Empty if the drawing is anything more, or if its fills
     * overlap or touch: every edge must have the fill on one side of it and not the other, since a distance is to the
     * nearest edge, and an edge hidden inside the fill would draw as a seam. {@link #render} draws it all.
     */
    public Optional<Shape> outline() {
        List<Contour> contours = new ArrayList<>();
        if (!new Outliner(contours).children(root, Affine.IDENTITY, Style.initial().with(root, this))) {
            return Optional.empty();
        }
        Shape shape = new Shape(contours);
        return everyEdgeBounds(shape) ? Optional.of(shape) : Optional.empty();
    }

    /** Whether every edge of {@code shape}, all along it, has the fill on exactly one side. */
    private static boolean everyEdgeBounds(Shape shape) {
        Bounds b = shape.bounds();
        double eps = 1e-6 * Math.max(1e-12, Math.max(b.width(), b.height()));
        for (Contour c : shape.contours()) {
            for (Edge e : c.edges()) {
                for (int i = 1; i < 10; i++) {
                    Vec2 m = e.point(i / 10.0);
                    Vec2 n = e.direction(i / 10.0).rightNormal();
                    if (!n.isZero() && shape.filled(m.plus(n.times(eps))) == shape.filled(m.minus(n.times(eps)))) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    // ---------------------------------------------------------------------------------------------------- reading

    private void index(Element e) {
        if (e.hasAttribute("id")) {
            ids.putIfAbsent(e.getAttribute("id"), e);
        }
        for (Element c : children(e)) {
            index(c);
        }
    }

    /** The SVG elements under {@code e}, in order; elements in other namespaces are an editor's and passed over. */
    static List<Element> children(Element e) {
        List<Element> out = new ArrayList<>();
        for (Node n = e.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element c && (c.getNamespaceURI() == null || NS.equals(c.getNamespaceURI()))) {
                out.add(c);
            }
        }
        return out;
    }

    static String name(Element e) {
        return e.getLocalName() != null ? e.getLocalName() : e.getTagName();
    }

    /** The element {@code url(#id)} or {@code #id} names. */
    Element referenced(String ref, String what) {
        String s = ref.strip();
        if (s.startsWith("url(") && s.endsWith(")")) {
            s = s.substring(4, s.length() - 1).strip();
            if (s.length() > 1 && (s.charAt(0) == '"' || s.charAt(0) == '\'')) {
                s = s.substring(1, s.length() - 1);
            }
        }
        if (!s.startsWith("#") || !ids.containsKey(s.substring(1))) {
            throw new IllegalArgumentException(what + " '" + ref + "' names nothing in the drawing");
        }
        return ids.get(s.substring(1));
    }

    /** Numbers separated by spaces or commas. */
    static double[] numbers(String s) {
        String t = s.strip();
        if (t.isEmpty()) {
            return new double[0];
        }
        String[] parts = t.split("[\\s,]+|(?<=[0-9.])(?=-)");
        double[] v = new double[parts.length];
        for (int i = 0; i < parts.length; i++) {
            v[i] = Double.parseDouble(parts[i]);
        }
        return v;
    }

    /** Attribute {@code name} of {@code e} as a length in user units; a percentage is of {@code of}. */
    static double length(Element e, String name, double fallback, double of) {
        String s = e.getAttribute(name).strip();
        return s.isEmpty() ? fallback : length(s, of);
    }

    static double length(String s, double of) {
        String t = s.strip().toLowerCase(Locale.ROOT);
        if (t.endsWith("%")) {
            return Double.parseDouble(t.substring(0, t.length() - 1)) / 100 * of;
        }
        String[][] units = {{"px", "1"}, {"pt", "1.3333333333"}, {"pc", "16"}, {"mm", "3.7795275591"},
                {"cm", "37.795275591"}, {"in", "96"}};
        for (String[] u : units) {
            if (t.endsWith(u[0])) {
                return Double.parseDouble(t.substring(0, t.length() - 2).strip()) * Double.parseDouble(u[1]);
            }
        }
        try {
            return Double.parseDouble(t);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("length '" + s + "'");
        }
    }

    /** The geometry of shape element {@code e}, in its own user units, or null if it has none. */
    Path geometry(Element e) {
        double vw = viewBox.width(), vh = viewBox.height(), vd = Math.hypot(vw, vh) / Math.sqrt(2);
        return switch (name(e)) {
            case "path" -> PathData.path(e.getAttribute("d"));
            case "rect" -> {
                double x = length(e, "x", 0, vw), y = length(e, "y", 0, vh);
                double w = length(e, "width", 0, vw), h = length(e, "height", 0, vh);
                if (!(w > 0) || !(h > 0)) {
                    yield null;
                }
                boolean hasRx = !e.getAttribute("rx").isBlank(), hasRy = !e.getAttribute("ry").isBlank();
                double rx = length(e, "rx", 0, vw), ry = length(e, "ry", 0, vh);
                if (hasRx && !hasRy) {
                    ry = rx;
                } else if (hasRy && !hasRx) {
                    rx = ry;
                }
                rx = Math.clamp(rx, 0, w / 2);
                ry = Math.clamp(ry, 0, h / 2);
                if (rx > 0 && ry > 0) {
                    yield Path.builder().moveTo(x + rx, y).lineTo(x + w - rx, y)
                            .arcTo(rx, ry, 0, false, true, x + w, y + ry).lineTo(x + w, y + h - ry)
                            .arcTo(rx, ry, 0, false, true, x + w - rx, y + h).lineTo(x + rx, y + h)
                            .arcTo(rx, ry, 0, false, true, x, y + h - ry).lineTo(x, y + ry)
                            .arcTo(rx, ry, 0, false, true, x + rx, y).close().build();
                }
                yield Path.builder().moveTo(x, y).lineTo(x + w, y).lineTo(x + w, y + h).lineTo(x, y + h).close()
                        .build();
            }
            case "circle" -> {
                double r = length(e, "r", 0, vd);
                yield ellipse(length(e, "cx", 0, vw), length(e, "cy", 0, vh), r, r);
            }
            case "ellipse" -> ellipse(length(e, "cx", 0, vw), length(e, "cy", 0, vh), length(e, "rx", 0, vw),
                    length(e, "ry", 0, vh));
            case "line" -> Path.builder().moveTo(length(e, "x1", 0, vw), length(e, "y1", 0, vh))
                    .lineTo(length(e, "x2", 0, vw), length(e, "y2", 0, vh)).build();
            case "polyline", "polygon" -> {
                double[] p = numbers(e.getAttribute("points"));
                if (p.length < 2) {
                    yield null;
                }
                Path.Builder b = Path.builder().moveTo(p[0], p[1]);
                for (int i = 2; i + 1 < p.length; i += 2) {
                    b.lineTo(p[i], p[i + 1]);
                }
                if (name(e).equals("polygon")) {
                    b.close();
                }
                yield b.build();
            }
            default -> throw new IllegalStateException(name(e));
        };
    }

    private static Path ellipse(double cx, double cy, double rx, double ry) {
        if (!(rx > 0) || !(ry > 0)) {
            return null;
        }
        return Path.builder().moveTo(cx + rx, cy).arcTo(rx, ry, 0, false, true, cx - rx, cy)
                .arcTo(rx, ry, 0, false, true, cx + rx, cy).close().build();
    }

    /** The transform {@code e} itself adds, and a {@code use}'s placement after it. */
    static Affine own(Element e) {
        Affine m = e.hasAttribute("transform") ? Affine.parse(e.getAttribute("transform")) : Affine.IDENTITY;
        if (name(e).equals("use")) {
            m = m.times(Affine.translate(length(e, "x", 0, 0), length(e, "y", 0, 0)));
        }
        return m;
    }

    static void refuse(Element e) {
        String n = name(e);
        if (REFUSED.contains(n)) {
            throw new IllegalArgumentException("<" + n + "> is not drawn here");
        }
        for (String a : new String[]{"filter", "marker-start", "marker-mid", "marker-end"}) {
            if (e.hasAttribute(a) && !e.getAttribute(a).strip().equals("none")) {
                throw new IllegalArgumentException("<" + n + " " + a + "> is not drawn here");
            }
        }
    }

    /** The element a {@code use} draws. */
    Element used(Element use) {
        String href = use.getAttribute("href");
        if (href.isBlank()) {
            href = use.getAttributeNS("http://www.w3.org/1999/xlink", "href");
        }
        Element target = referenced(href, "<use href>");
        if (name(target).equals("symbol") && target.hasAttribute("viewBox")) {
            throw new IllegalArgumentException("<use> of a <symbol> with a viewBox is not drawn here");
        }
        return target;
    }

    // ---------------------------------------------------------------------------------------------------- drawing

    /** Draws elements into layers, as a browser composites them. */
    private final class Renderer {

        private final int width;
        private final int height;
        private final Cover cover;
        private int depth;

        Renderer(int width, int height) {
            this.width = width;
            this.height = height;
            this.cover = new Cover(width, height);
        }

        void children(Element parent, Affine ctm, Style style, Layer into) {
            for (Element c : Svg.children(parent)) {
                element(c, ctm, style, into);
            }
        }

        void element(Element e, Affine ctm, Style inherited, Layer into) {
            refuse(e);
            String n = name(e);
            if (QUIET.contains(n) || "none".equals(Style.property(e, "display"))) {
                return;
            }
            if (!n.equals("g") && !n.equals("a") && !n.equals("use") && !SHAPES.contains(n)) {
                throw new IllegalArgumentException("<" + n + "> is not drawn here");
            }
            if (++depth > 64) {
                throw new IllegalArgumentException("the drawing refers to itself, or nests past 64 deep");
            }
            try {
                Style style = inherited.with(e, Svg.this);
                Affine m = ctm.times(own(e));
                String maskRef = Style.property(e, "mask");
                String clipRef = Style.property(e, "clip-path");
                boolean masked = maskRef != null && !maskRef.equals("none");
                boolean clipped = clipRef != null && !clipRef.equals("none");
                boolean alone = masked || clipped || style.opacity < 1;
                Layer target = alone ? new Layer(width, height) : into;
                if (n.equals("use")) {
                    element(used(e), m, style, target);
                } else if (SHAPES.contains(n)) {
                    shape(e, m, style, target);
                } else {
                    children(e, m, style, target);
                }
                if (alone) {
                    if (clipped) {
                        target.clipBy(clip(referenced(clipRef, "clip-path"), m));
                    }
                    if (masked) {
                        mask(referenced(maskRef, "mask"), m, target);
                    }
                    into.over(target, style.opacity);
                }
            } finally {
                depth--;
            }
        }

        void shape(Element e, Affine m, Style style, Layer into) {
            Path path = geometry(e);
            if (path == null || !style.visible) {
                return;
            }
            Path px = path.mapped(m::apply);
            if (style.fill != null && !name(e).equals("line")) {
                cover.fill(px.shape(), style.evenOdd);
                into.paint(cover, style.fill, style.fillOpacity * style.fill[3]);
                cover.clear();
            }
            if (style.stroke != null && style.strokeWidth > 0) {
                double k = m.scale();
                double[] dashes = style.dashes == null ? null : style.dashes.clone();
                if (dashes != null) {
                    for (int i = 0; i < dashes.length; i++) {
                        dashes[i] *= k;
                    }
                }
                Stroker.stroke(cover, px, style.strokeWidth * k, style.cap, style.join, style.miterLimit, dashes,
                        style.dashOffset * k);
                into.paint(cover, style.stroke, style.strokeOpacity * style.stroke[3]);
                cover.clear();
            }
        }

        void mask(Element mask, Affine m, Layer target) {
            if ("objectBoundingBox".equals(mask.getAttribute("maskContentUnits").strip())) {
                throw new IllegalArgumentException("a <mask> in objectBoundingBox content units is not drawn here");
            }
            Layer content = new Layer(width, height);
            children(mask, m.times(own(mask)), styleAt(mask), content);
            int x0 = 0, y0 = 0, x1 = width, y1 = height;
            if ("userSpaceOnUse".equals(mask.getAttribute("maskUnits").strip()) && mask.hasAttribute("width")
                    && mask.hasAttribute("height")) {
                double x = length(mask, "x", 0, viewBox.width()), y = length(mask, "y", 0, viewBox.height());
                double w = length(mask, "width", 0, viewBox.width()), h = length(mask, "height", 0, viewBox.height());
                Bounds b = Bounds.EMPTY;
                for (double[] c : new double[][]{{x, y}, {x + w, y}, {x, y + h}, {x + w, y + h}}) {
                    b = b.include(m.apply(new Vec2(c[0], c[1])));
                }
                // a region not square to the pixels is kept by its box: masks are drawn square in practice
                x0 = (int) Math.clamp(Math.round(b.xMin()), 0, width);
                y0 = (int) Math.clamp(Math.round(b.yMin()), 0, height);
                x1 = (int) Math.clamp(Math.round(b.xMax()), 0, width);
                y1 = (int) Math.clamp(Math.round(b.yMax()), 0, height);
            }
            target.maskBy(content, x0, y0, x1, y1);
        }

        boolean[] clip(Element clipPath, Affine m) {
            if ("objectBoundingBox".equals(clipPath.getAttribute("clipPathUnits").strip())) {
                throw new IllegalArgumentException("a <clipPath> in objectBoundingBox units is not drawn here");
            }
            boolean[] clip = new boolean[width * height];
            Affine base = m.times(own(clipPath));
            Style at = styleAt(clipPath);
            for (Element c : Svg.children(clipPath)) {
                clipInto(c, base, at, clip);
            }
            return clip;
        }

        private void clipInto(Element e, Affine m, Style inherited, boolean[] clip) {
            refuse(e);
            String n = name(e);
            if (n.equals("use")) {
                clipInto(used(e), m.times(own(e)), inherited.with(e, Svg.this), clip);
                return;
            }
            if (!SHAPES.contains(n)) {
                throw new IllegalArgumentException("<" + n + "> in a <clipPath> is not drawn here");
            }
            if (e.hasAttribute("clip-path") || "none".equals(Style.property(e, "display"))) {
                if (e.hasAttribute("clip-path")) {
                    throw new IllegalArgumentException("a clip inside a <clipPath> is not drawn here");
                }
                return;
            }
            Style style = inherited.with(e, Svg.this);
            Path path = geometry(e);
            if (path == null || !style.visible || n.equals("line")) {
                return;
            }
            cover.fill(path.mapped(m.times(own(e))::apply).shape(), style.clipEvenOdd);
            for (int y = cover.y0; y < cover.y1; y++) {
                for (int x = cover.x0; x < cover.x1; x++) {
                    clip[y * width + x] |= cover.bits[y * width + x];
                }
            }
            cover.clear();
        }
    }

    /** The style {@code e} inherits from its ancestors: what a mask's or a clip's content starts from. */
    private Style styleAt(Element e) {
        List<Element> chain = new ArrayList<>();
        for (Node n = e; n instanceof Element el; n = n.getParentNode()) {
            chain.addFirst(el);
        }
        Style s = Style.initial();
        for (Element el : chain) {
            s = s.with(el, this);
        }
        return s;
    }

    // ---------------------------------------------------------------------------------------------------- outline

    /** Collects the outline of a drawing of fills alone, or says it is more than that. */
    private final class Outliner {

        private final List<Contour> contours;
        private int depth;

        Outliner(List<Contour> contours) {
            this.contours = contours;
        }

        boolean children(Element parent, Affine ctm, Style style) {
            for (Element c : Svg.children(parent)) {
                if (!element(c, ctm, style)) {
                    return false;
                }
            }
            return true;
        }

        boolean element(Element e, Affine ctm, Style inherited) {
            refuse(e);
            String n = name(e);
            if (QUIET.contains(n) || "none".equals(Style.property(e, "display"))) {
                return true;
            }
            if (!n.equals("g") && !n.equals("a") && !n.equals("use") && !SHAPES.contains(n)) {
                throw new IllegalArgumentException("<" + n + "> is not drawn here");
            }
            if (++depth > 64) {
                throw new IllegalArgumentException("the drawing refers to itself, or nests past 64 deep");
            }
            try {
                String mask = Style.property(e, "mask"), clip = Style.property(e, "clip-path");
                Style style = inherited.with(e, Svg.this);
                if ((mask != null && !mask.equals("none")) || (clip != null && !clip.equals("none"))
                        || style.opacity < 1) {
                    return false;
                }
                Affine m = ctm.times(own(e));
                if (n.equals("use")) {
                    return element(used(e), m, style);
                }
                if (!SHAPES.contains(n)) {
                    return children(e, m, style);
                }
                Path path = geometry(e);
                if (path == null || !style.visible) {
                    return true;
                }
                if (style.stroke != null && style.strokeWidth > 0) {
                    return false;
                }
                if (style.fill == null || n.equals("line")) {
                    return true;
                }
                if (style.fillOpacity * style.fill[3] < 1 || style.evenOdd) {
                    return false;
                }
                contours.addAll(path.mapped(m::apply).shape().oriented().contours());
                return true;
            } finally {
                depth--;
            }
        }
    }
}
