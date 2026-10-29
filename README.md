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

A field describes solid, never sheets. A surface is the face of solid: the floor of a level has infinite depth behind
it, a wall is a prism, and nothing is a plane with no thickness. A ray is stopped where it goes from free space into
solid, and never where it leaves.

A vexel field says, per cell, how likely a ray is to collide there and with what:

| per cell | what it is |
|---|---|
| granularity | `1` is smoke, material building up over distance with no particular normal; `0` is a surface, where the collision is at one point with one normal. Between, the cell is both. |
| rate | collisions per unit distance travelled, for the granular share |
| plane, footprint, depth | the flat share: a prism of solid behind the plane, within the footprint, as deep as its share of the cell's solid |
| materials | the chance of each material, given a collision |

The two shares compete along a ray: the granular share's collision falls where an exponential of its rate puts it,
the flat share's where the ray enters its prism, and whichever comes first is the collision. The rate is per distance
because granular material is met more the further a ray goes through it; the prism is met once, where it is entered,
through its face, its sides or a cell's wall alike.

All of that is derived from moments the cell keeps underneath: how much of it is solid, and the centroid and spread
of that solid; its surface area, the area-weighted mean of the surface's normals, where the surface sits along that
mean and how far it strays, and the centroid and covariance of where it lies; the share of each material, and the mean
and variance of any continuous attributes. Moments average exactly, which distances do not, so `coarser()` combines
eight cells into one (four in 2D) by moments, never by resampling: volume, area, material shares and centroids are
kept exactly, and detail lost to distance turns into granularity.

The flat share's prism: its face is a box on the plane from the surface's centroid and covariance (half-widths
`√(3·variance)`, the box a uniform rectangle's moments give back exactly), grown until it holds the flat area within
the plane's slice of the cell, and taken as the whole slice once it holds 85% of it, so a surface spanning its cell
keeps every corner and no crack opens between cells. Its depth is its share of the cell's solid over its face's area:
a floor's reaches the cell's far wall, a coarse cell's piece of a sphere stops short.

The granular share is grains of solid, as stochastic geometry's Boolean model has them. A ray from the free space
between grains enters one at a rate of `S/4 ÷ (1 − occupancy)` per unit distance in 3D (`S/π ÷ (1 − occupancy)` in
2D), so a mostly solid cell stops a ray almost at once. That rate is spread over the cell as a Gaussian at the solid's
centroid with the solid's spread, so a ray through a coarse ball meets it and one past its edge does not.

The solid as a whole counts too: each cell treats its solid as a ball at its centroid whose mean squared radius is its
spread, clipped to the solid side of the cell's plane where it has one, and a ray entering that ball is stopped with
the ball's fill (`solidFill`), `1` for a solid ball and toward `0` for a solid scattered thin, as foliage is.

The walk keeps whether the ray is in solid, as each cell describes it: in a cell's prism, in a granular cell's ball
when that is at least half full, or in a cell with no surface that is at least half solid. A ray that comes to a cell
in solid is leaving it and is not stopped there; one that comes into solid at a cell's wall is stopped at the wall.

A sphere of radius 3, baked in cells of 0.25 and coarsened, with vertical rays over it: the mean chance of a stop for
rays inside its outline (want 1) and outside it (want 0), and its mean granularity.

```
cell   granularity   inside   outside
0.25   0.015         1.000    0.000
0.50   0.034         1.000    0.000
1      0.077         1.000    0.001
2      0.149         1.000    0.009
4      0.350         0.999    0.077
8      1.000         1.000    0.067    one cell
```

Two ways to ask a ray. `trace` gives the expected outcome, the chance of a collision in each cell. `collide` gives one
outcome, every value it draws a `VexelHash` of the cell, the ray's direction quantized to an angle bucket, a seed and
what is being decided, so the outcome holds still from frame to frame while the view stays in its bucket. The grain is
the cell: a cell whose granular share stops a ray half the time is wholly there or wholly not, from one bucket; its
prism draws nothing, being solid. Averaged over seeds, `collide` agrees with `trace`
(`hashedCollisionsAverageToTheExpectedOutcome`).

Known:
* The 85% at which a footprint becomes the whole slice is tuned on one sphere: at 80% the leak came back at cells of 2,
  at 90% cracks opened at cells of 1.
* A prism is a rough shape for three faces meeting at a corner: a cube turned 30° leaks 0.124 outside its outline at
  cells of 4 (0.994 inside), and a curved-triangle cap a little past its corners (the 0.077 above).
* One Gaussian per cell: solid in several clumps (eight grains in a cell's corners) is put in one blob between them,
  and a ray through the empty middle is stopped by the granular share about 30% of the time.
* The Gaussian's tails past the cell's walls are lost, so a cell whose solid is spread wide undercounts its rate.
* The rate's free share is the cell's average occupancy, not the occupancy where the ray is.

Experimental: the cell's layout, the derivation and the bake are expected to change.
