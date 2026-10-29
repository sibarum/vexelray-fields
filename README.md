# vexelray-fields

Shapes as fields. Signed distance fields (SDF), multi-channel ones (MSDF, MTSDF), and **vexel fields**, an
experiment in keeping the probability of a hit and what the surface is like where it is hit, instead of one distance.

Plain Java 25, no dependencies, no GPU and no native code. A field is made here and drawn by whoever uploads it: the
bytes are `vexelray-canvas`'s `DistanceField` layout, and the kinds are `vexelray-codec-asset`'s.

```
mvn install        # build, test, and put it in the local Maven repository
```

## Modules

```
vexelray-fields-core    shapes (lines, quadratic and cubic Béziers; SVG path data), the nonzero fill by scanline,
                        Field (the floats), Framing (shape units to texels), Encoding (the bytes), Coverage (drawn
                        back on the CPU, as the shaders draw it).
vexelray-fields-sdf     one true distance per texel: ShapeSdf from a shape's edges, MaskSdf from a raster mask by an
                        exact Euclidean distance transform.
vexelray-fields-msdf    three distances whose median is the edge (Chlumský's MSDF), and MTSDF with the true distance
                        in a fourth: edge colouring, generation, sign and clash correction.
vexelray-fields-vexel   experimental. Vexel fields: collision rate, granularity and materials per cell, filtered by
                        combining moments, and collided with by hashed rays that hold still frame to frame.
```

### Conventions

* Distances are **positive inside**, in **texels** in a `Field`, and kept unclamped until encoded.
* Texel `(x, y)` covers `[x, x+1) × [y, y+1)`, row 0 at the top, sampled at its centre. A `Framing` says whether the
  shape's y runs up (font units) or down (SVG).
* A distance `d` is encoded as `0.5 + d / range`, clamped: the edge at one half, `range / 2` texels each way the farthest
  a byte tells apart.
* A shape is filled by the **nonzero** rule. The sign of every distance comes from the fill, not from how contours
  happen to be wound, so overlapping contours and ones wound either way come out as they are filled.

### SDF, MSDF, MTSDF

```java
Shape star = PathData.parse("M50 0 L61 35 L98 35 L68 57 L79 91 L50 70 L21 91 L32 57 L2 35 L39 35 Z");
Framing framing = Framing.fit(star.bounds(), 32, 32, 3, false);

Field sdf   = ShapeSdf.generate(star, framing, 32, 32);
Field msdf  = Msdf.generate(star, framing, 32, 32);
Field mtsdf = Msdf.generateMtsdf(star, framing, 32, 32);

byte[] rgba = Encoding.rgba(msdf, 6);   // what the GPU uploads
```

What the MSDF buys, measured: the star above at 32×32 drawn at 16× has no clearly wrong pixels as an MSDF and 712 as
an SDF; at 16×16 drawn at 32×, 38 against 3,344 (`MsdfTest.magnifiedCornersStaySharp` holds the margin at ten
times).

The MSDF generator is msdfgen's legacy pipeline: simple edge colouring, per-channel nearest edge by true distance and
its pseudo-distance, then clash correction. Not yet here: msdfgen's newer error correction (which checks
interpolated values, not just neighbours), the overlapping-contour combiner (overlaps are handled by the sign pass
instead), and the ink-trap colouring.

### Vexel fields (experimental)

A vexel field says, per cell, how likely a ray is to collide there and with what:

| per cell | what it is |
|---|---|
| granularity | `1` is smoke, material building up over distance with no particular normal; `0` is a surface, where the collision is at one point with one normal. Between, the cell is both. |
| rate | collisions per unit distance travelled, for the granular share |
| coverage, plane | the flat share: one plane that stops a ray crossing it inside the cell with this chance (`1` a wall, `0.5` a screen door) |
| materials | the chance of each material, given a collision |

The two shares compete along a ray: the granular share's collision falls where an exponential of its rate puts it,
the plane's where the ray crosses it, and whichever comes first is the collision. The rate is per distance because
granular material is met more the further a ray goes through it; the plane's chance is per crossing because a surface
is met once by any ray that crosses it, at any angle.

All of that is derived from moments the cell keeps underneath: how much of it is solid, its surface area, the
area-weighted mean of the surface's normals, where the surface sits along that mean and how far it strays, the share
of each material, and the mean and variance of any continuous attributes. Moments average exactly, which distances do
not, so `coarser()` combines eight cells into one (four in 2D) by moments, never by resampling: volume, area and
material shares are kept exactly, and detail lost to distance turns into granularity. A sphere of radius 3 baked in
cells of 0.25:

```
cell 0.25: mean granularity 0.015    cell 2.00: mean granularity 0.149
cell 0.50: mean granularity 0.034    cell 4.00: mean granularity 0.350
cell 1.00: mean granularity 0.077    cell 8.00: mean granularity 1.000 (one cell: a small cloud)
```

Two ways to ask a ray. `trace` gives the expected outcome, the chance of a collision in each cell. `collide` gives one
outcome, every value it draws a `VexelHash` of the cell, the ray's direction quantized to an angle bucket, a seed and
what is being decided, so the outcome holds still from frame to frame while the view stays in its bucket. The grain is
the cell: a half-covered cell is wholly there or wholly not, from one bucket. Averaged over seeds, `collide` agrees
with `trace` (`hashedCollisionsAverageToTheExpectedOutcome`).

Known: a granular cell treats its surface as independent pieces, so a solid object seen coarsely turns see-through:
the sphere above, as its one cell, stops a ray through its middle with a chance of 0.59, not 1. The cell's occupancy
says it is solid; using that is the next question.

Experimental: the cell's layout, the derivation and the bake are expected to change.
