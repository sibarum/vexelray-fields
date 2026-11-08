package dev.vexelray.fields.convert;

import java.util.ArrayList;
import java.util.List;

/**
 * What a conversion keeps: what it loses of its source, and what it needed that the source does not hold. Neither, and
 * it is exact within the sampling it was asked for. A route's fidelity is its hops' joined, in order, so nothing a hop
 * loses or needs goes unsaid in the route.
 *
 * @param lost   what is dropped, in words ("paints", "corners: rounded at magnification")
 * @param needed what was supplied because the source does not hold it ("a paint", "a footprint's σ")
 */
public record Fidelity(List<String> lost, List<String> needed) {

    public static final Fidelity EXACT = new Fidelity(List.of(), List.of());

    public Fidelity {
        lost = List.copyOf(lost);
        needed = List.copyOf(needed);
    }

    /** Losing {@code what}. */
    public static Fidelity lossy(String... what) {
        return new Fidelity(List.of(what), List.of());
    }

    /** Needing {@code what}, which is the conversion's required argument. */
    public static Fidelity synthesising(String... what) {
        return new Fidelity(List.of(), List.of(what));
    }

    /** This and losing {@code what} as well. */
    public Fidelity losing(String... what) {
        return join(lossy(what));
    }

    /** This and needing {@code what} as well. */
    public Fidelity needing(String... what) {
        return join(synthesising(what));
    }

    public boolean exact() {
        return lost.isEmpty() && needed.isEmpty();
    }

    /** This, then {@code next}'s. */
    public Fidelity join(Fidelity next) {
        List<String> l = new ArrayList<>(lost);
        l.addAll(next.lost);
        List<String> n = new ArrayList<>(needed);
        n.addAll(next.needed);
        return new Fidelity(l, n);
    }

    /** {@code EXACT}, or {@code LOSSY (...)}, {@code SYNTHESISING (...)} or both, as the tables in conversions.md. */
    @Override
    public String toString() {
        if (exact()) {
            return "EXACT";
        }
        StringBuilder s = new StringBuilder();
        if (!lost.isEmpty()) {
            s.append("LOSSY (").append(String.join("; ", lost)).append(')');
        }
        if (!needed.isEmpty()) {
            s.append(s.isEmpty() ? "" : ", ").append("SYNTHESISING (").append(String.join("; ", needed)).append(')');
        }
        return s.toString();
    }
}
