package dev.vexelray.fields.art;

import org.w3c.dom.Element;

import java.util.Locale;
import java.util.Map;

/**
 * The properties an element is drawn with: its own, from its attributes or its {@code style} attribute (which wins),
 * and the rest inherited. {@code opacity} is the one not inherited, being applied once to the group it is on.
 * Colours are sRGB components {@code 0..1}, as the drawing gives them, and composited as they are.
 */
final class Style {

    private static final Map<String, Integer> NAMED = Map.ofEntries(
            Map.entry("black", 0x000000), Map.entry("white", 0xFFFFFF), Map.entry("red", 0xFF0000),
            Map.entry("lime", 0x00FF00), Map.entry("green", 0x008000), Map.entry("blue", 0x0000FF),
            Map.entry("yellow", 0xFFFF00), Map.entry("cyan", 0x00FFFF), Map.entry("aqua", 0x00FFFF),
            Map.entry("magenta", 0xFF00FF), Map.entry("fuchsia", 0xFF00FF), Map.entry("gray", 0x808080),
            Map.entry("grey", 0x808080), Map.entry("silver", 0xC0C0C0), Map.entry("maroon", 0x800000),
            Map.entry("olive", 0x808000), Map.entry("teal", 0x008080), Map.entry("navy", 0x000080),
            Map.entry("purple", 0x800080), Map.entry("orange", 0xFFA500));

    String fillSpec = "black";
    String strokeSpec = "none";
    float[] color = {0, 0, 0};
    float[] fill;
    float[] stroke;
    float fillOpacity = 1;
    float strokeOpacity = 1;
    float opacity = 1;
    double strokeWidth = 1;
    Stroker.Cap cap = Stroker.Cap.BUTT;
    Stroker.Join join = Stroker.Join.MITER;
    double miterLimit = 4;
    double[] dashes;
    double dashOffset;
    boolean evenOdd;
    boolean clipEvenOdd;
    boolean visible = true;

    private Style() {
    }

    static Style initial() {
        Style s = new Style();
        s.fill = new float[]{0, 0, 0, 1};
        return s;
    }

    private Style copy() {
        Style s = new Style();
        s.fillSpec = fillSpec;
        s.strokeSpec = strokeSpec;
        s.color = color;
        s.fillOpacity = fillOpacity;
        s.strokeOpacity = strokeOpacity;
        s.strokeWidth = strokeWidth;
        s.cap = cap;
        s.join = join;
        s.miterLimit = miterLimit;
        s.dashes = dashes;
        s.dashOffset = dashOffset;
        s.evenOdd = evenOdd;
        s.clipEvenOdd = clipEvenOdd;
        s.visible = visible;
        return s;
    }

    /** This, as {@code e} inherits it, with {@code e}'s own properties over it. */
    Style with(Element e, Svg svg) {
        Style s = copy();
        String v;
        if ((v = property(e, "color")) != null && !v.equals("inherit")) {
            s.color = v.equals("currentColor") ? color : rgb(v);
        }
        if ((v = property(e, "fill")) != null && !v.equals("inherit")) {
            s.fillSpec = v;
        }
        if ((v = property(e, "stroke")) != null && !v.equals("inherit")) {
            s.strokeSpec = v;
        }
        // currentColor is the colour of the element drawn, so a paint is resolved where it is used
        s.fill = s.paint(s.fillSpec, svg);
        s.stroke = s.paint(s.strokeSpec, svg);
        if ((v = property(e, "fill-opacity")) != null) {
            s.fillOpacity = unit(v);
        }
        if ((v = property(e, "stroke-opacity")) != null) {
            s.strokeOpacity = unit(v);
        }
        if ((v = property(e, "opacity")) != null) {
            s.opacity = unit(v);
        }
        if ((v = property(e, "stroke-width")) != null) {
            s.strokeWidth = Svg.length(v, Math.hypot(svg.viewBox().width(), svg.viewBox().height()) / Math.sqrt(2));
        }
        if ((v = property(e, "stroke-linecap")) != null) {
            s.cap = Stroker.Cap.valueOf(keyword(v, "stroke-linecap", "butt", "round", "square"));
        }
        if ((v = property(e, "stroke-linejoin")) != null) {
            s.join = Stroker.Join.valueOf(keyword(v, "stroke-linejoin", "miter", "round", "bevel"));
        }
        if ((v = property(e, "stroke-miterlimit")) != null) {
            s.miterLimit = Math.max(1, Double.parseDouble(v));
        }
        if ((v = property(e, "stroke-dasharray")) != null) {
            s.dashes = dashes(v);
        }
        if ((v = property(e, "stroke-dashoffset")) != null) {
            s.dashOffset = Svg.length(v, 0);
        }
        if ((v = property(e, "fill-rule")) != null) {
            s.evenOdd = keyword(v, "fill-rule", "nonzero", "evenodd").equals("EVENODD");
        }
        if ((v = property(e, "clip-rule")) != null) {
            s.clipEvenOdd = keyword(v, "clip-rule", "nonzero", "evenodd").equals("EVENODD");
        }
        if ((v = property(e, "visibility")) != null) {
            s.visible = keyword(v, "visibility", "visible", "hidden", "collapse").equals("VISIBLE");
        }
        return s;
    }

    /**
     * Property {@code name} of {@code e}: from its {@code style} attribute if that sets it, else its attribute of the
     * name, else null.
     */
    static String property(Element e, String name) {
        String style = e.getAttribute("style");
        if (!style.isBlank()) {
            for (String decl : style.split(";")) {
                int colon = decl.indexOf(':');
                if (colon > 0 && decl.substring(0, colon).strip().equals(name)) {
                    return strip(decl.substring(colon + 1));
                }
            }
        }
        return e.hasAttribute(name) ? strip(e.getAttribute(name)) : null;
    }

    private static String strip(String v) {
        String s = v.strip();
        if (s.endsWith("!important")) {
            s = s.substring(0, s.length() - "!important".length()).strip();
        }
        return s;
    }

    /**
     * A paint: red, green, blue and an opacity it adds, or null for none. A gradient is its stops' mean colour at
     * their mean opacity: the shape it covers is exact, read by alpha with opaque stops, and only its colour is not.
     */
    private float[] paint(String spec, Svg svg) {
        if (spec.equals("none") || spec.equals("transparent")) {
            return null;
        }
        if (spec.startsWith("url(")) {
            return gradient(svg.referenced(spec, "paint"), svg, spec);
        }
        float[] c = spec.equals("currentColor") ? color : rgb(spec);
        return new float[]{c[0], c[1], c[2], 1};
    }

    private float[] gradient(Element g, Svg svg, String spec) {
        for (int hops = 0; hops < 16; hops++) {
            String n = Svg.name(g);
            if (!n.equals("linearGradient") && !n.equals("radialGradient")) {
                throw new IllegalArgumentException("paint '" + spec + "': a <" + n + "> is not drawn here");
            }
            float[] sum = new float[4];
            int stops = 0;
            for (Element s : Svg.children(g)) {
                if (Svg.name(s).equals("stop")) {
                    String c = property(s, "stop-color");
                    float[] rgb = c == null ? new float[3] : c.equals("currentColor") ? color : rgb(c);
                    String o = property(s, "stop-opacity");
                    sum[0] += rgb[0];
                    sum[1] += rgb[1];
                    sum[2] += rgb[2];
                    sum[3] += o == null ? 1 : unit(o);
                    stops++;
                }
            }
            if (stops > 0) {
                return new float[]{sum[0] / stops, sum[1] / stops, sum[2] / stops, sum[3] / stops};
            }
            String href = g.getAttribute("href").isBlank()
                    ? g.getAttributeNS("http://www.w3.org/1999/xlink", "href") : g.getAttribute("href");
            if (href.isBlank()) {
                return null;   // a gradient of no stops paints nothing
            }
            g = svg.referenced(href, "gradient href");
        }
        throw new IllegalArgumentException("paint '" + spec + "': its gradients refer to each other in a loop");
    }

    /** A colour: {@code #rgb}, {@code #rrggbb}, {@code rgb(…)} in numbers or percentages, or a basic name. */
    static float[] rgb(String spec) {
        String s = spec.strip().toLowerCase(Locale.ROOT);
        try {
            if (s.startsWith("#") && (s.length() == 4 || s.length() == 7)) {
                int v = Integer.parseInt(s.substring(1), 16);
                if (s.length() == 4) {
                    v = (v >> 8 & 0xF) * 0x110000 + (v >> 4 & 0xF) * 0x1100 + (v & 0xF) * 0x11;
                }
                return rgb(v);
            }
            if (s.startsWith("rgb(") && s.endsWith(")")) {
                String[] parts = s.substring(4, s.length() - 1).split("[\\s,]+");
                if (parts.length == 3) {
                    float[] c = new float[3];
                    for (int i = 0; i < 3; i++) {
                        String p = parts[i].strip();
                        c[i] = Math.clamp(p.endsWith("%") ? Float.parseFloat(p.substring(0, p.length() - 1)) / 100
                                : Float.parseFloat(p) / 255, 0f, 1f);
                    }
                    return c;
                }
            }
        } catch (NumberFormatException e) {
            // refused below
        }
        Integer named = NAMED.get(s);
        if (named == null) {
            throw new IllegalArgumentException("colour '" + spec + "' is not one read here");
        }
        return rgb(named);
    }

    private static float[] rgb(int v) {
        return new float[]{(v >> 16 & 0xFF) / 255f, (v >> 8 & 0xFF) / 255f, (v & 0xFF) / 255f};
    }

    private static float unit(String v) {
        String s = v.strip();
        float f = s.endsWith("%") ? Float.parseFloat(s.substring(0, s.length() - 1)) / 100 : Float.parseFloat(s);
        return Math.clamp(f, 0f, 1f);
    }

    private static String keyword(String v, String property, String... allowed) {
        for (String a : allowed) {
            if (a.equals(v)) {
                return a.toUpperCase(Locale.ROOT);
            }
        }
        throw new IllegalArgumentException(property + " '" + v + "'");
    }

    /** A dash array, or null for none: an odd count is repeated to make it even, as SVG has it. */
    private static double[] dashes(String v) {
        if (v.equals("none")) {
            return null;
        }
        String[] parts = v.strip().split("[\\s,]+");
        double[] d = new double[parts.length];
        double total = 0;
        for (int i = 0; i < parts.length; i++) {
            d[i] = Svg.length(parts[i], 0);
            if (d[i] < 0) {
                return null;   // an error, which SVG draws solid
            }
            total += d[i];
        }
        if (!(total > 0)) {
            return null;
        }
        if (d.length % 2 == 1) {
            double[] twice = new double[d.length * 2];
            System.arraycopy(d, 0, twice, 0, d.length);
            System.arraycopy(d, 0, twice, d.length, d.length);
            return twice;
        }
        return d;
    }
}
