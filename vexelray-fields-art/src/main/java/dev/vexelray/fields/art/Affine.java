package dev.vexelray.fields.art;

import dev.vexelray.fields.shape.Vec2;

/**
 * An SVG transform: {@code x' = a x + c y + e}, {@code y' = b x + d y + f}, its {@code matrix(a b c d e f)}.
 */
record Affine(double a, double b, double c, double d, double e, double f) {

    static final Affine IDENTITY = new Affine(1, 0, 0, 1, 0, 0);

    static Affine translate(double x, double y) {
        return new Affine(1, 0, 0, 1, x, y);
    }

    static Affine scale(double x, double y) {
        return new Affine(x, 0, 0, y, 0, 0);
    }

    Vec2 apply(Vec2 p) {
        return new Vec2(a * p.x() + c * p.y() + e, b * p.x() + d * p.y() + f);
    }

    /** This after {@code m}: {@code m} applied first. */
    Affine times(Affine m) {
        return new Affine(a * m.a + c * m.b, b * m.a + d * m.b, a * m.c + c * m.d, b * m.c + d * m.d,
                a * m.e + c * m.f + e, b * m.e + d * m.f + f);
    }

    /** How much a length grows, on average over directions: the square root of the area's growth. */
    double scale() {
        return Math.sqrt(Math.abs(a * d - b * c));
    }

    /** A {@code transform} attribute: its functions in order, the first outermost. */
    static Affine parse(String s) {
        Affine m = IDENTITY;
        int i = 0;
        while (true) {
            while (i < s.length() && (Character.isWhitespace(s.charAt(i)) || s.charAt(i) == ',')) {
                i++;
            }
            if (i >= s.length()) {
                return m;
            }
            int open = s.indexOf('(', i);
            int close = open < 0 ? -1 : s.indexOf(')', open);
            if (close < 0) {
                throw new IllegalArgumentException("transform '" + s + "' is not functions of numbers");
            }
            String name = s.substring(i, open).strip();
            double[] v = Svg.numbers(s.substring(open + 1, close));
            m = m.times(function(name, v, s));
            i = close + 1;
        }
    }

    private static Affine function(String name, double[] v, String s) {
        return switch (name) {
            case "matrix" -> {
                need(v, 6, 6, s);
                yield new Affine(v[0], v[1], v[2], v[3], v[4], v[5]);
            }
            case "translate" -> {
                need(v, 1, 2, s);
                yield translate(v[0], v.length > 1 ? v[1] : 0);
            }
            case "scale" -> {
                need(v, 1, 2, s);
                yield scale(v[0], v.length > 1 ? v[1] : v[0]);
            }
            case "rotate" -> {
                need(v, 1, 3, s);
                double r = Math.toRadians(v[0]), cos = Math.cos(r), sin = Math.sin(r);
                Affine turn = new Affine(cos, sin, -sin, cos, 0, 0);
                if (v.length == 3) {
                    yield translate(v[1], v[2]).times(turn).times(translate(-v[1], -v[2]));
                }
                need(v, 1, 1, s);
                yield turn;
            }
            case "skewX" -> {
                need(v, 1, 1, s);
                yield new Affine(1, 0, Math.tan(Math.toRadians(v[0])), 1, 0, 0);
            }
            case "skewY" -> {
                need(v, 1, 1, s);
                yield new Affine(1, Math.tan(Math.toRadians(v[0])), 0, 1, 0, 0);
            }
            default -> throw new IllegalArgumentException("transform '" + s + "': no function '" + name + "'");
        };
    }

    private static void need(double[] v, int min, int max, String s) {
        if (v.length < min || v.length > max) {
            throw new IllegalArgumentException("transform '" + s + "': wrong number of arguments");
        }
    }
}
