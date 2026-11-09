# Conversions

**Status: ruled "build as drafted" and built, 2026-11-08**, in `vexelray-fields-convert` (every edge and route below)
and `vexelray-fields-bench` (their benchmarks, published in [`benchmarks.md`](benchmarks.md)). Where the code differs
from the draft, *How it reads in code* says how.

## The rule

There are many kinds of field (SDF, MSDF, MTSDF, SDG, coverage, the experimental vexel and fractal fields), and
effects want different ones: `Condense` wants an MTSDF and a colour square, an SDG look wants the back layer's stencil,
the OS wants pixels. So:

1. **From any kind to any kind an effect wants, there is a conversion.** None is left for an application to write.
2. **A conversion that would have to invent data takes it as a required argument instead**, as an immutable value or
   as a function per texel. It never has a default that quietly fills the gap, so leaving the data out is a compile
   error rather than a wrong picture.
3. **A conversion that goes through another kind shows each hop in the code.** It is written as the composition of its
   hops, named in order, and its description lists them. A two-hop conversion never looks like one hop.
4. **Every conversion says what it keeps.** It is `EXACT` (within the sampling it is asked for), `LOSSY` (it drops
   something, which it names), or `SYNTHESISING` (it needs data the source does not hold, which is its required
   argument).
5. **Every conversion is benchmarked, and the numbers are published** in [`benchmarks.md`](benchmarks.md), generated
   from the benchmark run, with the machine, the JDK and the inputs beside them. A conversion lands with its benchmark
   or does not land.
6. **Conversions run when a field is built or loaded, never per frame.** The runtime never rasterises (SDG ruling 5);
   this is the library that makes that possible.

## The kinds

| Kind | What it holds | Type here |
| --- | --- | --- |
| **Shape** | contours of lines and Béziers, nonzero fill | `Shape` (core) |
| **SVG** | shapes with fills, strokes, gradients | `Svg` (art) |
| **Raster** | RGBA pixels | `Raster` (art) |
| **Mask** | inside or out per sample, at an oversample | `boolean[]` today; a `Mask` record proposed |
| **SDF** | one true signed distance per texel | `Field`, `FieldKind.SDF` |
| **MSDF** | three pseudo-distances whose median is the edge | `Field`, `FieldKind.MSDF` |
| **MTSDF** | MSDF and a true distance | `Field`, `FieldKind.MTSDF` |
| **Coverage** | how much of each pixel is inside, `0..1` | `float[]` today; a `Coverage` record proposed |
| **SDG** | layers of a stencil (SDF or MSDF) and a paint, back to front | `Sdg` (sdg-format) |
| **Art pair** | an MTSDF of the whole shape beside its colour, per size | `ArtPair` (atlas) |
| **Icon** | a raster per OS size, packed as `.ico` | proposed |
| **Vexel field** | 3D moments per cell: rate, granularity, the flat share, materials | `VexelField` (vexel) |
| **Fractal coverage** | a Gaussian footprint's share of a rational fractal, 3D | `Coverage` (fractal) |

Encoding (linear or `LogEncoding`, its range) is not a kind. It is how any field becomes bytes, and every field kind
takes it the same way.

## The edges

**Vector is the hub.** Most conversions are exact only through the vector shape: an MSDF's corners can be recovered
from a shape and not from an SDF's samples. So a conversion between two sampled kinds goes out to a shape and back when
it has to be exact, and that is its hops. A direct edge exists only where it is exact on its own or measurably faster
for the same result; where it is a lossy shortcut, the exact route sits beside it under its own name.

### One hop

| From → to | Kind | Needs | Notes |
| --- | --- | --- | --- |
| Shape → SDF / MSDF / MTSDF | EXACT | `Framing`, size | *exists*: `ShapeSdf.generate`, `Msdf.generate`, `Msdf.generateMtsdf` |
| SVG → SDG | EXACT | texels across | *exists*: `SvgToSdg.convert`. Refuses group opacity, mask and clip, which composite several parts at once |
| SVG → Shape | LOSSY: paints | which parts (fills, strokes) | the union of the chosen parts; *partly exists* inside `ArtField` |
| Raster → Mask | SYNTHESISING: a threshold | channel, threshold, invert | *partly exists* inside `ArtField` / `MaskSdf.ofRgba` |
| Mask → SDF | EXACT to the oversample | spread | *exists*: `MaskSdf.of`, an exact Euclidean distance transform |
| Mask → Shape | EXACT to the oversample | flatness | *new, built*: trace the boundary and fit curves. Opens the exact route from anything sampled |
| SDF → MSDF / MTSDF, as is | LOSSY: corners stay round | none | *exists*: `ArtField.widen`. Named `widen`, never `toMsdf`, so it is not mistaken for the exact route |
| SDF / MSDF / MTSDF → Coverage | EXACT | pixels | *exists*: `Coverage.render` |
| SDF / MSDF / MTSDF → SDG | SYNTHESISING: a paint | `Paint` (a colour and an optional role, or a texture) | *new, built*: one layer |
| Raster → SDG | LOSSY: colours clustered | `Trace.Options` | *exists*: `Trace.trace` |
| SDG → Raster | EXACT | pixels, origin, scale | *exists*: `SdgRender.render` |
| SDG → Mask | LOSSY: paints | oversample | *new, built*: the union of the layers' stencils, where any layer is opaque |
| SDG → SDG layer | EXACT | layer index | *new, built*: one layer's stencil as its own field |
| SDF → Coverage under a footprint | SYNTHESISING: a footprint | `σ`, or `σ` per texel | *new, built*: `Φ(d / σ)`, a probability that the footprint is inside |
| Coverage under a footprint → SDF | LOSSY: flat beyond the band | `σ`, the band | *new, built*: `σ · Φ⁻¹(p)` where `0 < p < 1`, the band's edge beyond |
| Raster sizes → Icon | EXACT | none | *new, built*: an `.ico` of PNGs, written without AWT |

### Explicit routes

| From → to | Hops | Kind | Needs |
| --- | --- | --- | --- |
| SVG → MTSDF | SVG → Shape → MTSDF | LOSSY: paints | parts, `Framing` |
| SDG → MTSDF, exact | SDG → Mask → Shape → MTSDF | LOSSY: paints | oversample, flatness, `Framing` |
| SDG → MTSDF, fast | SDG → Mask → SDF → widen | LOSSY: paints, corners | oversample, spread |
| SDG → SDF, exact | SDG → Mask → Shape → SDF | LOSSY: paints | oversample, flatness, `Framing` |
| SDG → Art pair | (SDG → Mask → Shape → SDF) beside (SDG → Raster), per size | LOSSY: paints in the field | sizes, field side, margin. An SDF, not the traced MTSDF: `ArtPair`'s near channels are one true distance, which `Condense` reads well off the edge |
| SVG → Art pair | SVG → SDG → Art pair | as above | as above; replaces `ArtPair.bake(Svg, ...)` |
| SDG → Icon | (SDG → Raster) per size → Icon | EXACT | sizes, and **a drawing per small size where there is one**: the hand-drawn 16 and 24 px marks are data the source does not hold, so they are a required list, empty when there are none |
| Raster → MSDF | Raster → Mask → Shape → MSDF | SYNTHESISING: a threshold | channel, threshold, flatness, `Framing` |
| MSDF → SDF | MSDF → Coverage → Mask → SDF | EXACT to the oversample | oversample |

### Not edges yet

* **Vexel field ↔ anything 2D.** A vexel field is 3D and holds a probability of a hit and its material, not a
  distance. A 2D slice of it, or a vexel field from an extruded SDG, is a conversion with real choices: the depth, the
  granularity, the materials. Those are for the author to define before anything is built.
* **Nested field ↔ SDG and the rest.** Field Painter's nested field (one channel, outlines at even values, painted
  slope) is drafted as a source kind with its own edges in [`field-painter.md`](field-painter.md), for ruling.
* **Fractal coverage ↔ field.** Its `rasterize` is already a conversion to a 3D coverage grid. A distance from it
  needs the footprint model above in 3D.

## How it reads in code

A conversion is a value: its name, its hops, its kind, what it loses, and the function. Composition is the only way
to make a route, so the hops cannot go missing from the description:

```java
// A final class: a route is made by then() and beside() and nothing else.
Conversion<Sdg, Field> exact = Convert.sdgToMask(64, 64, 8, Set.of())
        .then(Convert.maskToShape(0.25))
        .then(Convert.shapeToMtsdf(Convert.margin(8), 80, 80));
exact.name();       // "SDG → Mask → Shape → MTSDF"
exact.fidelity();   // LOSSY (paints)
exact.hops();       // the three edges, each a Conversion of its own

// What a route needs is its arguments; there are no defaults that invent data.
Conversion<Field, Sdg> painted = Convert.fieldToSdg(FieldKind.SDF, 8, new Sdg.Paint.Solid(0x2D6CDFFF, "accent"));

// Two branches of one source, joined: the join is a hop of its own.
Convert.sdgToArtPair(104, 0.25f, new int[]{104, 208}, "sun").name();
// "(SDG → Mask → Shape → SDF) beside (SDG → Raster sizes) → Art pair"
```

* `Fidelity` lists what is lost and what was needed, in words; a route's is its hops' joined, so a route that loses
  two things says both.
* `then` checks the kinds meet **by name**, not only by Java type: `Field` is an SDF, an MSDF and an MTSDF alike, and
  an MSDF hop cannot be followed by one that takes an SDF.
* **The mask tracer** (Mask → Shape) follows the cracks between inside and outside samples, takes their midpoints,
  splits them into runs within a sample of a line (Douglas–Peucker), refits each run's line by least squares and puts
  each corner where neighbouring lines meet. On the README's star, at 16 samples to a texel, its corners land within
  0.002 texel of the star's own, and drawn at 16× its MSDF's error round the corners is a twentieth of the widened
  SDF's.
* SVG parts now carry their element's id (the outermost one they are drawn under), and an SDG layer converted from
  one is named after it, so a part can be named across the conversion (the art pair's lifted part).

`Convert` holds every edge and every named route as static factories, each documented with its row from the tables
above. It lives in a new module, `vexelray-fields-convert`, which depends on the encoders. The format module stays
dependency-free, and readers still never pull in an encoder.

## Benchmarks

* A new module, `vexelray-fields-bench`, on JMH: one benchmark per edge and per named route, on fixed inputs kept in
  the module's `Inputs` (the README's star, a badge drawing with fills, a stroke and a named part, and a picture of
  it for the raster conversions), at two sizes each. Made in code, read from nowhere.
* Each run records the time per operation and the allocation per operation (JMH's `gc` profiler). A route is reported
  hop by hop as well as whole, so its cost can be put on the hop that has it.
* `benchmarks.md` is written by the run, not by hand: the machine (CPU, cores, memory), the OS, the JDK and its flags,
  the JMH settings, and a table per conversion. Rows are keyed by `Convert`'s factory names.
* The bench module is not shipped and is not a native image, so JMH's generated code and reflection are fine there.
  Nothing in it is a dependency of anything else.
* A conversion is not called done until it has a row: `CatalogueTest` fails while a public factory of `Convert` has
  no benchmark case.

## What slice 5 of the SDG plan uses

* **About** draws an art pair converted from the application's SDG (SDG → Art pair), so `Condense` stays as it is and
  the `pair` goal's separate bake goes.
* **OS icons** are built from the SDG (SDG → Icon), with the hand-drawn small sizes passed in.

## Rulings

**Ruled 2026-11-08: build as drafted.** So:

1. **Both SDG → MTSDF routes** are built: `sdgToMtsdf` (exact, traced) and `sdgToMtsdfFast` (widened, corners round),
   and the art pair has both (`sdgToArtPair`, `sdgToArtPairFast`).
2. **Probability fields** are built as drafted, `Φ(d / σ)` under a Gaussian footprint and its inverse. Still open: whether
   that is the author's probability field in 2D, and the vexel and fractal edges, which wait on the author's
   definitions.
3. **`benchmarks.md` is published in this repository's `docs/`.** Nothing goes to a live site (the live sites stay
   0.1 until 0.2 ships).
