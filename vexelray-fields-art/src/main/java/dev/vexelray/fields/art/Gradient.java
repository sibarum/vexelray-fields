package dev.vexelray.fields.art;

import dev.vexelray.fields.shape.Bounds;
import dev.vexelray.fields.shape.Vec2;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.List;

/**
 * A {@code linearGradient} or {@code radialGradient}, its attributes and stops gathered along its {@code href}s as SVG
 * inherits them: units, {@code gradientTransform}, {@code spreadMethod}, the geometry, and the stops of the first in
 * the chain that has any. Stops are interpolated premultiplied, as browsers draw them.
 */
final class Gradient {

    /** A paint's colour at a pixel, premultiplied: {@code r, g, b, a}. */
    interface Shader {
        void at(double x, double y, float[] out);
    }

    private enum Spread { PAD, REFLECT, REPEAT }

    private final boolean radial;
    private final boolean boundingBox;
    private final Affine transform;
    private final Spread spread;
    private final String[] geometry;   // x1 y1 x2 y2, or cx cy r fx fy
    private final float[] offsets;
    private final float[][] colours;   // premultiplied

    private Gradient(boolean radial, boolean boundingBox, Affine transform, Spread spread, String[] geometry,
                     float[] offsets, float[][] colours) {
        this.radial = radial;
        this.boundingBox = boundingBox;
        this.transform = transform;
        this.spread = spread;
        this.geometry = geometry;
        this.offsets = offsets;
        this.colours = colours;
    }

    /** The gradient {@code e} is, its stops' {@code currentColor} the {@code color} given. */
    static Gradient of(Element e, Svg svg, float[] color) {
        List<Element> chain = new ArrayList<>();
        for (Element g = e; g != null; ) {
            String n = Svg.name(g);
            if (!n.equals("linearGradient") && !n.equals("radialGradient")) {
                throw new IllegalArgumentException("a paint of <" + n + "> is not drawn here");
            }
            if (chain.contains(g) || chain.size() > 16) {
                throw new IllegalArgumentException("gradients that refer to each other in a loop");
            }
            chain.add(g);
            String href = g.getAttribute("href").isBlank()
                    ? g.getAttributeNS("http://www.w3.org/1999/xlink", "href") : g.getAttribute("href");
            g = href.isBlank() ? null : svg.referenced(href, "gradient href");
        }
        boolean radial = Svg.name(e).equals("radialGradient");
        String units = inherited(chain, "gradientUnits");
        String t = inherited(chain, "gradientTransform");
        String s = inherited(chain, "spreadMethod");
        Spread spread = s == null ? Spread.PAD : switch (s) {
            case "reflect" -> Spread.REFLECT;
            case "repeat" -> Spread.REPEAT;
            default -> Spread.PAD;
        };
        String[] names = radial ? new String[]{"cx", "cy", "r", "fx", "fy"} : new String[]{"x1", "y1", "x2", "y2"};
        String[] geometry = new String[names.length];
        for (int i = 0; i < names.length; i++) {
            geometry[i] = inherited(chain, names[i]);
        }

        List<Float> offsets = new ArrayList<>();
        List<float[]> colours = new ArrayList<>();
        for (Element g : chain) {
            for (Element stop : Svg.children(g)) {
                if (!Svg.name(stop).equals("stop")) {
                    continue;
                }
                String o = stop.getAttribute("offset").strip();
                float offset = o.isEmpty() ? 0 : o.endsWith("%")
                        ? Float.parseFloat(o.substring(0, o.length() - 1)) / 100 : Float.parseFloat(o);
                offset = Math.clamp(offset, offsets.isEmpty() ? 0f : offsets.getLast(), 1f);
                String c = Style.property(stop, "stop-color");
                float[] rgb = c == null ? new float[3] : c.equals("currentColor") ? color : Style.rgb(c);
                String a = Style.property(stop, "stop-opacity");
                float alpha = a == null ? 1 : Math.clamp(Float.parseFloat(a.strip()), 0f, 1f);
                offsets.add(offset);
                colours.add(new float[]{rgb[0] * alpha, rgb[1] * alpha, rgb[2] * alpha, alpha});
            }
            if (!offsets.isEmpty()) {
                break;
            }
        }
        float[] off = new float[offsets.size()];
        for (int i = 0; i < off.length; i++) {
            off[i] = offsets.get(i);
        }
        return new Gradient(radial, !"userSpaceOnUse".equals(units),
                t == null ? Affine.IDENTITY : Affine.parse(t), spread, geometry, off, colours.toArray(float[][]::new));
    }

    private static String inherited(List<Element> chain, String attribute) {
        for (Element g : chain) {
            if (g.hasAttribute(attribute)) {
                return g.getAttribute(attribute).strip();
            }
        }
        return null;
    }

    /** Whether every stop is opaque: what covers its shape whole, read by alpha. */
    boolean opaque() {
        for (float[] c : colours) {
            if (c[3] < 1) {
                return false;
            }
        }
        return colours.length > 0;
    }

    /**
     * The gradient over a shape whose box in user space is {@code box}, drawn under {@code ctm}; null where it paints
     * nothing (no stops, or a box with no area in bounding-box units).
     */
    Shader shader(Bounds box, Affine ctm, Bounds viewport) {
        if (colours.length == 0) {
            return null;
        }
        if (colours.length == 1) {
            float[] c = colours[0];
            return (x, y, out) -> System.arraycopy(c, 0, out, 0, 4);
        }
        Affine units = Affine.IDENTITY;
        double w = viewport.width(), h = viewport.height(), diag = Math.hypot(w, h) / Math.sqrt(2);
        if (boundingBox) {
            if (!(box.width() > 0) || !(box.height() > 0)) {
                return null;
            }
            units = Affine.translate(box.xMin(), box.yMin()).times(Affine.scale(box.width(), box.height()));
            w = h = diag = 1;
        }
        Affine inv = ctm.times(units).times(transform).inverse();
        if (inv == null) {
            return null;
        }
        if (!radial) {
            Vec2 p1 = new Vec2(len(geometry[0], 0, w), len(geometry[1], 0, h));
            Vec2 p2 = new Vec2(len(geometry[2], boundingBox ? 1 : w, w), len(geometry[3], 0, h));
            Vec2 d = p2.minus(p1);
            double dd = d.dot(d);
            return (x, y, out) -> {
                Vec2 g = inv.apply(new Vec2(x, y));
                colour(dd == 0 ? 1 : g.minus(p1).dot(d) / dd, out);
            };
        }
        double half = boundingBox ? 0.5 : 0;
        Vec2 c = new Vec2(len(geometry[0], boundingBox ? half : w / 2, w), len(geometry[1], boundingBox ? half : h / 2, h));
        double r = len(geometry[2], boundingBox ? half : diag / 2, diag);
        if (!(r > 0)) {
            float[] last = colours[colours.length - 1];
            return (x, y, out) -> System.arraycopy(last, 0, out, 0, 4);
        }
        Vec2 f0 = new Vec2(geometry[3] == null ? c.x() : len(geometry[3], 0, w),
                geometry[4] == null ? c.y() : len(geometry[4], 0, h));
        // a focus on or past the circle is drawn just inside it, as SVG 1.1 has it
        Vec2 fc = f0.minus(c);
        Vec2 f = fc.length() > r * 0.99 ? c.plus(fc.times(r * 0.99 / fc.length())) : f0;
        return (x, y, out) -> {
            Vec2 g = inv.apply(new Vec2(x, y));
            Vec2 gf = g.minus(f);
            double dist = gf.length();
            if (dist == 0) {
                colour(0, out);
                return;
            }
            Vec2 dir = gf.times(1 / dist);
            Vec2 cf = c.minus(f);
            double along = dir.dot(cf);
            double reach = along + Math.sqrt(Math.max(0, along * along - (cf.dot(cf) - r * r)));
            colour(dist / reach, out);
        };
    }

    private static double len(String s, double fallback, double of) {
        return s == null || s.isEmpty() ? fallback : Svg.length(s, of);
    }

    private void colour(double t, float[] out) {
        double u = switch (spread) {
            case PAD -> Math.clamp(t, 0, 1);
            case REPEAT -> t - Math.floor(t);
            case REFLECT -> {
                double m = t - 2 * Math.floor(t / 2);
                yield m > 1 ? 2 - m : m;
            }
        };
        int n = offsets.length;
        if (u <= offsets[0]) {
            System.arraycopy(colours[0], 0, out, 0, 4);
            return;
        }
        for (int i = 1; i < n; i++) {
            if (u <= offsets[i]) {
                float span = offsets[i] - offsets[i - 1];
                float k = span <= 0 ? 1 : (float) ((u - offsets[i - 1]) / span);
                for (int ch = 0; ch < 4; ch++) {
                    out[ch] = colours[i - 1][ch] + (colours[i][ch] - colours[i - 1][ch]) * k;
                }
                return;
            }
        }
        System.arraycopy(colours[n - 1], 0, out, 0, 4);
    }
}
