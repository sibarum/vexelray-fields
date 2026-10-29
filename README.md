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
vexelray-fields-vexel   experimental. Vexel fields: baked from any signed distance, filtered by combining moments,
                        and traced by rays for the probability of a hit.
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

A distance field says where one surface is, and averaging distances (a mip level, a texel seen from far away) gives
the distance to no surface at all. A vexel field keeps, per cell, statistics that average exactly:

| per cell | what it is |
|---|---|
| occupancy | the share of the cell that is solid |
| surface density | surface area per unit volume (in 2D, outline length per unit area) |
| mean normal `n̄` | the area-weighted mean outward normal; `1 - |n̄|` is the cell's roughness |
| offset mean, variance | where the surface sits along `n̄` from the cell's centre, and how far it strays |
| attribute mean, variance | anything the solid gives per surface point: albedo, material, roughness |

`coarser()` combines eight cells into one (four in 2D) by moments, never by resampling: volume and area are kept
exactly, and the mean normal of surfaces facing different ways shortens, which is roughness appearing where detail
is lost. A sphere coarsened to one cell has a mean normal of nearly zero.

A ray asks each cell it crosses for the probability that it is stopped there. By the Crofton formula a ray crossing
length `l` of a cell meets on average `N = S · l · E|n·ω|` pieces of surface; the normals are modelled as a mixture,
a share `|n̄|` facing along `n̄` and the rest every way alike. Under `HitModel.SHEET` the facing share is one sheet
where the offset moments put it, so a flat surface stops every ray that crosses it, at any angle, and no other; the
rest stops a ray with probability `min(1, N)`. Under `HitModel.SCATTERED` it is all `1 - exp(-N)`, a participating
medium's transmittance. `RayHits.sample(u)` draws one outcome, for a path tracer.

This is one reading of the idea, to be argued with: the cell's layout, both hit models and the bake are expected to
change.
