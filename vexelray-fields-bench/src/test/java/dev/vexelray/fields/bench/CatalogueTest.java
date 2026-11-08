package dev.vexelray.fields.bench;

import dev.vexelray.fields.convert.Conversion;
import dev.vexelray.fields.convert.Convert;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** A conversion lands with its benchmark: every public factory of {@link Convert} has a case, and every case runs. */
class CatalogueTest {

    @Test
    void everyConversionHasABenchmark() {
        Set<String> factories = new TreeSet<>();
        for (Method m : Convert.class.getMethods()) {
            if (Modifier.isStatic(m.getModifiers()) && m.getReturnType() == Conversion.class) {
                factories.add(m.getName());
            }
        }
        Set<String> benchmarked = Catalogue.cases().values().stream().map(Catalogue.Case::factory)
                .collect(Collectors.toCollection(TreeSet::new));
        Set<String> missing = new TreeSet<>(factories);
        missing.removeAll(benchmarked);
        assertEquals(Set.of(), missing, "conversions with no benchmark");
    }

    @Test
    void everyCaseRunsOnItsInput() {
        for (Catalogue.Case c : Catalogue.cases().values()) {
            assertNotNull(c.conversion().apply(c.input().get()), c.key());
        }
    }
}
