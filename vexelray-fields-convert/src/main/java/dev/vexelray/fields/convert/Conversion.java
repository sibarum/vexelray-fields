package dev.vexelray.fields.convert;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * One kind of field into another, as a value: its name, its hops, what it keeps, and the function.
 *
 * <p><b>A route is only ever made by {@link #then} or {@link #beside}.</b> So its {@link #hops()} are the edges it is
 * made of, in order, its {@link #name()} names every kind it passes through ({@code "SDG → Mask → Shape → MTSDF"}),
 * and its {@link #fidelity()} is each hop's joined. A conversion that goes through another kind cannot look like one
 * hop.
 *
 * <p>What a conversion needs that its source does not hold is an argument of the factory that made it (see
 * {@link Convert}); a conversion holds it, immutable, and never invents it.
 *
 * @param <A> what it takes
 * @param <B> what it makes
 */
public final class Conversion<A, B> {

    /** A join of two branches of one source, for {@link #beside}: it sees the source and both results. */
    @FunctionalInterface
    public interface Join<A, B, C, D> {
        D apply(A from, B left, C right);
    }

    private final String name;
    private final String from;
    private final String to;
    private final Fidelity fidelity;
    private final List<Conversion<?, ?>> hops;
    private final Function<A, B> f;

    private Conversion(String name, String from, String to, Fidelity fidelity, List<Conversion<?, ?>> hops,
                       Function<A, B> f) {
        this.name = name;
        this.from = from;
        this.to = to;
        this.fidelity = fidelity;
        this.hops = hops;
        this.f = f;
    }

    /** One hop, from kind {@code from} to kind {@code to}. */
    public static <A, B> Conversion<A, B> edge(String from, String to, Fidelity fidelity, Function<A, B> f) {
        return new Conversion<>(from + " → " + to, from, to, fidelity, null, f);
    }

    /**
     * Two routes from one source, side by side, joined into kind {@code into} by {@code join}, which is a hop of its
     * own ({@code joining} says what it keeps): {@code "(SDG → Mask → Shape → MTSDF) beside (SDG → Raster sizes)
     * → Art pair"}.
     */
    public static <A, B, C, D> Conversion<A, D> beside(Conversion<A, B> left, Conversion<A, C> right, String into,
                                                       Fidelity joining, Join<A, B, C, D> join) {
        if (!left.from.equals(right.from)) {
            throw new IllegalArgumentException(left.name + " and " + right.name + " do not start from one kind");
        }
        Conversion<Object, D> joint = edge("(" + left.to + ", " + right.to + ")", into, joining, x -> {
            throw new UnsupportedOperationException("a join is applied by the route it joins, with its source");
        });
        List<Conversion<?, ?>> h = new ArrayList<>(left.hops());
        h.addAll(right.hops());
        h.add(joint);
        String name = "(" + left.name + ") beside (" + right.name + ") → " + into;
        return new Conversion<>(name, left.from, into, left.fidelity.join(right.fidelity).join(joining), List.copyOf(h),
                a -> join.apply(a, left.apply(a), right.apply(a)));
    }

    /** {@code "SDG → Mask → Shape → MTSDF"}. */
    public String name() {
        return name;
    }

    /** The kind it takes. */
    public String from() {
        return from;
    }

    /** The kind it makes. */
    public String to() {
        return to;
    }

    public Fidelity fidelity() {
        return fidelity;
    }

    /** The edges it is made of, in order; itself alone for one hop. */
    public List<Conversion<?, ?>> hops() {
        return hops == null ? List.of(this) : hops;
    }

    public B apply(A source) {
        return f.apply(source);
    }

    /**
     * This, then {@code next}: a route through this one's last kind. The kinds must meet, {@code next} taking the kind
     * this one makes, by name as well as by type, so a route is never assembled from two hops that only happen to share
     * a Java type ({@code Field} is an SDF, an MSDF and an MTSDF alike).
     */
    public <C> Conversion<A, C> then(Conversion<B, C> next) {
        if (!to.equals(next.from)) {
            throw new IllegalArgumentException(name + " cannot go on into " + next.name + ": it makes " + to
                    + " and that takes " + next.from);
        }
        String prefix = next.from + " → ";
        String rest = next.name.startsWith(prefix) ? next.name.substring(prefix.length()) : "(" + next.name + ")";
        List<Conversion<?, ?>> h = new ArrayList<>(hops());
        h.addAll(next.hops());
        return new Conversion<>(name + " → " + rest, from, next.to, fidelity.join(next.fidelity), List.copyOf(h),
                f.andThen(next.f));
    }

    /** {@code "SDG → Mask → SDF: LOSSY (paints)"}. */
    @Override
    public String toString() {
        return name + ": " + fidelity;
    }
}
