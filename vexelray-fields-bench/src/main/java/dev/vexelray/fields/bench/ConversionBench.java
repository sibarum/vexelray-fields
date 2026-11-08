package dev.vexelray.fields.bench;

import dev.vexelray.fields.convert.Conversion;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

/**
 * One case of {@link Catalogue} per run: its input made once, outside the timing, and the conversion applied to it
 * once an operation. {@code case} is set by {@link Publish} to every key in the catalogue.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class ConversionBench {

    /** A key of {@link Catalogue#cases()}; {@link Publish} passes them all. */
    @Param("shapeToSdf @ star, 32 texels")
    public String key;

    private Conversion<Object, Object> conversion;
    private Object input;

    @Setup(Level.Trial)
    public void setUp() {
        Catalogue.Case c = Catalogue.get(key);
        conversion = c.conversion();
        input = c.input().get();
    }

    @Benchmark
    public Object convert() {
        return conversion.apply(input);
    }
}
