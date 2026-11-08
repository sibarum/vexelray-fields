package dev.vexelray.fields.bench;

import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.results.Result;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Runs every case of {@link Catalogue} through JMH, time and allocation per operation, and writes the results to the
 * file named, {@code docs/benchmarks.md}. The file is written by this and nothing else: a number in it came from a run,
 * with the machine, the JDK and the settings beside it.
 *
 * <p>Pass a second argument, a substring of keys, to run some of the cases only; the file then says so.
 */
public final class Publish {

    private Publish() {
    }

    public static void main(String[] args) throws RunnerException, IOException {
        Path out = Path.of(args.length > 0 ? args[0] : "docs/benchmarks.md");
        String only = args.length > 1 ? args[1] : null;
        List<String> keys = new ArrayList<>();
        for (String k : Catalogue.cases().keySet()) {
            if (only == null || k.contains(only)) {
                keys.add(k);
            }
        }
        Options opt = new OptionsBuilder()
                .include(ConversionBench.class.getName())
                .param("key", keys.toArray(new String[0]))
                .addProfiler(GCProfiler.class)
                .build();
        Collection<RunResult> results = new Runner(opt).run();

        Map<String, RunResult> byKey = new LinkedHashMap<>();
        for (RunResult r : results) {
            byKey.put(r.getParams().getParam("key"), r);
        }
        Files.writeString(out, render(byKey, only), StandardCharsets.UTF_8);
        System.out.println("wrote " + out.toAbsolutePath());
    }

    static String render(Map<String, RunResult> results, String only) {
        StringBuilder s = new StringBuilder();
        s.append("# Benchmarks\n\n");
        s.append("**Written by `vexelray-fields-bench`'s `Publish`, not by hand.** Every conversion in\n");
        s.append("[`conversions.md`](conversions.md), timed and its allocation measured by JMH on fixed inputs (the\n");
        s.append("module's `Inputs`). Run it again with:\n\n");
        s.append("```\nmvn -pl vexelray-fields-bench -am install -DskipTests\nmvn -pl vexelray-fields-bench exec:exec\n```\n\n");
        if (only != null) {
            s.append("**A partial run:** only the cases whose key contains `").append(only).append("`.\n\n");
        }
        s.append("## The run\n\n");
        s.append("| | |\n| --- | --- |\n");
        s.append("| Date | ").append(LocalDate.now()).append(" |\n");
        s.append("| CPU | ").append(cpu()).append(" |\n");
        s.append("| Cores the JVM sees | ").append(Runtime.getRuntime().availableProcessors()).append(" |\n");
        s.append("| Memory | ").append(memory()).append(" |\n");
        s.append("| OS | ").append(System.getProperty("os.name")).append(' ').append(System.getProperty("os.version"))
                .append(' ').append(System.getProperty("os.arch")).append(" |\n");
        s.append("| JDK | ").append(System.getProperty("java.vm.name")).append(' ')
                .append(System.getProperty("java.runtime.version")).append(" |\n");
        s.append("| JVM flags | ").append(flags()).append(" |\n");
        s.append("| JMH | average time per operation, 1 fork, 3 warm-up and 5 measured iterations of 1 s each, ")
                .append("one thread; allocation from the `gc` profiler |\n\n");
        s.append("Time is the mean per conversion and its 99.9% confidence half-width. Allocation is bytes per\n");
        s.append("conversion, normalised by the `gc` profiler. A route's hops are each timed on exactly what the hop\n");
        s.append("before it made inside the route, so where its cost lies is plain; a hop's time and the route's\n");
        s.append("whole can differ by what the JIT does with the whole.\n\n");

        Map<String, List<Catalogue.Case>> byFactory = new LinkedHashMap<>();
        for (Catalogue.Case c : Catalogue.cases().values()) {
            if (c.route() == null && results.containsKey(c.key())) {
                byFactory.computeIfAbsent(c.factory(), f -> new ArrayList<>()).add(c);
            }
        }
        for (Map.Entry<String, List<Catalogue.Case>> e : byFactory.entrySet()) {
            Catalogue.Case first = e.getValue().getFirst();
            s.append("## `Convert.").append(e.getKey()).append("`\n\n");
            s.append(first.conversion().name()).append(": ").append(first.conversion().fidelity()).append("\n\n");
            s.append("| Input | Time | Allocated |\n| --- | ---: | ---: |\n");
            for (Catalogue.Case c : e.getValue()) {
                row(s, c.inputLabel(), results.get(c.key()));
                for (Catalogue.Case h : Catalogue.cases().values()) {
                    if (c.key().equals(h.route()) && results.containsKey(h.key())) {
                        row(s, "&nbsp;&nbsp;hop " + h.hop() + ": " + h.conversion().name(), results.get(h.key()));
                    }
                }
            }
            s.append('\n');
        }
        return s.toString();
    }

    private static void row(StringBuilder s, String label, RunResult r) {
        Result<?> t = r.getPrimaryResult();
        Result<?> alloc = r.getSecondaryResults().get("gc.alloc.rate.norm");   // "·gc..." before JMH 1.37
        if (alloc == null) {
            alloc = r.getSecondaryResults().get("·gc.alloc.rate.norm");
        }
        s.append("| ").append(label).append(" | ").append(time(t.getScore(), t.getScoreError())).append(" | ")
                .append(alloc == null ? "—" : bytes(alloc.getScore())).append(" |\n");
    }

    /** Microseconds as the most readable unit, with the error in the same. */
    static String time(double us, double err) {
        if (us >= 1000) {
            return String.format(Locale.ROOT, "%.2f ± %.2f ms", us / 1000, err / 1000);
        }
        return String.format(Locale.ROOT, "%.1f ± %.1f µs", us, err);
    }

    static String bytes(double b) {
        if (b >= 1 << 20) {
            return String.format(Locale.ROOT, "%.1f MiB", b / (1 << 20));
        }
        if (b >= 1 << 10) {
            return String.format(Locale.ROOT, "%.1f KiB", b / (1 << 10));
        }
        return String.format(Locale.ROOT, "%.0f B", b);
    }

    private static String flags() {
        List<String> a = ManagementFactory.getRuntimeMXBean().getInputArguments();
        return a.isEmpty() ? "none" : "`" + String.join(" ", a) + "`";
    }

    private static String memory() {
        if (ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean os) {
            return String.format(Locale.ROOT, "%.1f GiB", os.getTotalMemorySize() / (double) (1L << 30));
        }
        return "unknown";
    }

    /** The CPU's own name: the registry's on Windows, {@code /proc/cpuinfo}'s on Linux, else the environment's. */
    private static String cpu() {
        try {
            if (System.getProperty("os.name").startsWith("Windows")) {
                String name = firstLine("powershell", "-NoProfile", "-Command",
                        "(Get-CimInstance Win32_Processor | Select-Object -First 1).Name");
                if (name != null && !name.isBlank()) {
                    return name.strip();
                }
            } else if (Files.exists(Path.of("/proc/cpuinfo"))) {
                for (String line : Files.readAllLines(Path.of("/proc/cpuinfo"))) {
                    if (line.startsWith("model name")) {
                        return line.substring(line.indexOf(':') + 1).strip();
                    }
                }
            }
        } catch (IOException | InterruptedException e) {
            // fall through to what the environment says
        }
        String id = System.getenv("PROCESSOR_IDENTIFIER");
        return id != null ? id : "unknown";
    }

    private static String firstLine(String... command) throws IOException, InterruptedException {
        Process p = new ProcessBuilder(command).redirectErrorStream(true).start();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line = r.readLine();
            p.waitFor();
            return line;
        }
    }
}
