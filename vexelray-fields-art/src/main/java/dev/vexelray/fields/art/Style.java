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
    Paint fill;
    Paint stroke;
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
        s.fill = new Paint(new float[]{0, 0, 0}, null);
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

    /** A paint: one colour, or a gradient. */
    record Paint(float[] rgb, Gradient gradient) {

        /** Whether it covers what it paints whole, before any opacity property. */
        boolean opaque() {
            return gradient == null || gradient.opaque();
        }
    }

    /** The paint {@code spec} names, or null for none. */
    private Paint paint(String spec, Svg svg) {
        if (spec.equals("none") || spec.equals("transparent")) {
            return null;
        }
        if (spec.startsWith("url(")) {
            return new Paint(null, Gradient.of(svg.referenced(spec, "paint"), svg, color));
        }
        return new Paint(spec.equals("currentColor") ? color : rgb(spec), null);
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
