package dev.vexelray.fields.shape;

/**
 * SVG path data ({@code d="M10 10 L20 10 ..."}) as a {@link Path} or a {@link Shape}: moves, lines, horizontal and
 * vertical lines, quadratic and cubic curves with their smooth forms, elliptical arcs (as cubics), and closes, absolute
 * and relative. Coordinates are kept as written, y down.
 */
public final class PathData {

    private final String s;
    private int i;

    private PathData(String s) {
        this.s = s;
    }

    /** The fill of {@code d}, every subpath closed. */
    public static Shape parse(String d) {
        return path(d).shape();
    }

    /** {@code d} as drawn, open subpaths left open: what a stroke follows. */
    public static Path path(String d) {
        return new PathData(d).read();
    }

    private Path read() {
        Path.Builder b = Path.builder();
        char cmd = 0;
        Vec2 ctrl = null;   // the last curve's second control point, for S and T
        char last = 0;
        while (true) {
            skip();
            if (i >= s.length()) {
                break;
            }
            char c = s.charAt(i);
            if (Character.isLetter(c)) {
                cmd = c;
                i++;
            } else if (cmd == 0) {
                throw error("a number before any command");
            }
            boolean rel = Character.isLowerCase(cmd);
            Vec2 p = b.pen();
            double ox = rel ? p.x() : 0, oy = rel ? p.y() : 0;
            switch (Character.toUpperCase(cmd)) {
                case 'M' -> {
                    b.moveTo(ox + num(), oy + num());
                    cmd = rel ? 'l' : 'L';   // further pairs are lines
                }
                case 'L' -> b.lineTo(ox + num(), oy + num());
                case 'H' -> b.lineTo(ox + num(), p.y());
                case 'V' -> b.lineTo(p.x(), oy + num());
                case 'Q' -> {
                    double cx = ox + num(), cy = oy + num();
                    b.quadTo(cx, cy, ox + num(), oy + num());
                    ctrl = new Vec2(cx, cy);
                }
                case 'T' -> {
                    Vec2 cp = "QqTt".indexOf(last) >= 0 && ctrl != null ? p.times(2).minus(ctrl) : p;
                    b.quadTo(cp.x(), cp.y(), ox + num(), oy + num());
                    ctrl = cp;
                }
                case 'C' -> {
                    double c1x = ox + num(), c1y = oy + num(), c2x = ox + num(), c2y = oy + num();
                    b.cubicTo(c1x, c1y, c2x, c2y, ox + num(), oy + num());
                    ctrl = new Vec2(c2x, c2y);
                }
                case 'S' -> {
                    Vec2 c1 = "CcSs".indexOf(last) >= 0 && ctrl != null ? p.times(2).minus(ctrl) : p;
                    double c2x = ox + num(), c2y = oy + num();
                    b.cubicTo(c1.x(), c1.y(), c2x, c2y, ox + num(), oy + num());
                    ctrl = new Vec2(c2x, c2y);
                }
                case 'A' -> {
                    double rx = num(), ry = num(), rotation = num();
                    boolean large = flag(), sweep = flag();
                    b.arcTo(rx, ry, rotation, large, sweep, ox + num(), oy + num());
                }
                case 'Z' -> {
                    b.close();
                    cmd = 0;
                }
                default -> throw error("unknown command '" + cmd + "'");
            }
            last = cmd == 0 ? 'Z' : cmd;
        }
        return b.build();
    }

    private void skip() {
        while (i < s.length() && (Character.isWhitespace(s.charAt(i)) || s.charAt(i) == ',')) {
            i++;
        }
    }

    /** An arc's flag: one digit, which may run straight into what follows ({@code a5 5 0 016 0}). */
    private boolean flag() {
        skip();
        if (i < s.length() && (s.charAt(i) == '0' || s.charAt(i) == '1')) {
            return s.charAt(i++) == '1';
        }
        throw error("an arc's flag, 0 or 1, expected");
    }

    private double num() {
        skip();
        int from = i;
        if (i < s.length() && (s.charAt(i) == '-' || s.charAt(i) == '+')) {
            i++;
        }
        boolean dot = false, exp = false;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (Character.isDigit(c)) {
                i++;
            } else if (c == '.' && !dot && !exp) {
                dot = true;
                i++;
            } else if ((c == 'e' || c == 'E') && !exp) {
                exp = true;
                i++;
                if (i < s.length() && (s.charAt(i) == '-' || s.charAt(i) == '+')) {
                    i++;
                }
            } else {
                break;
            }
        }
        if (from == i) {
            throw error("a number expected");
        }
        return Double.parseDouble(s.substring(from, i));
    }

    private IllegalArgumentException error(String what) {
        return new IllegalArgumentException("path data at " + i + ": " + what);
    }
}
