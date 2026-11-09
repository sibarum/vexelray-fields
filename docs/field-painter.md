# Field Painter and SDG

**Status: draft, 2026-11-08, for ruling; ruling 6 (log space outside) made by the author the same day.** The source is the design document *Field Painter: concept and design*
(November 2026): a painting program whose brush paints one nested field rather than colour. This plan says where that
field sits among the kinds this library already has, and how it reaches SDG. Nothing here is built.

## The short answer

The nested field is a **source kind**, not a new SDG stencil kind. It reaches SDG by a **conversion**, one more entry in
`Convert` under the rules of [`conversions.md`](conversions.md). SDG stays what it is: a delivery format that any
reader draws the same way.

The design itself names the point where the two models meet: *"Clamping the field to the range −1 to 1 keeps only
depth 1 … That is the classic signed field."* A nested field clamped to depth 1 and normalized to uniform width **is**
an SDG `sdf` stencil. Everything else in the design (deeper levels, painted slope, plateau detail, edge rules,
materials, effects) sits above that, and is either baked into SDG layers when exported or stays in the editor.

## The nested field, briefly

One float channel per layer. Below 0 is outside; even values are outlines, odd values interiors; the outline of depth
`d` is at `2d − 2`, its interior at `2d − 1`. The value only climbs, so a continuous field always nests: a depth-2
region is always inside a depth-2 outline, which is always inside the true outline. Clamping at `2d − 1` is level of
detail. Multiplying the field moves no outline, so magnitude, and with it **slope**, is free, and the design uses it:
hardness sets the slope, and the slope sets how wide an outline renders and how far it moves with the cutoff. The
deviation of a plateau from its odd value carries further intent (bevel depth, glow falloff, ramps). Rendering reads
a cutoff `c`, a band half-width `w`, a depth limit, an edge rule (value band, slope or gated), a presence per depth and
a material per depth or by parity.

### Outside is log space

**Ruled 2026-11-08: effects that go beyond the shape interpret the field in log space.** The design leaves the outside
open: *"values farther below zero can drive shadows and halos"*, and a drop shadow *"wants true distance"*. So the
outside, `v < 0`, is read as a distance by the README's log curve (*Log space*), with a `knee` and a `reach` per layer:

```
outside distance(v) = knee · sinh(−v · asinh(reach / knee))      for v ≤ 0;   v = −1 is `reach`
```

* **Nothing at the edge changes.** `asinh` is a straight line through zero, bending only with the cube, so within the
  knee the outside is the linear field it was. The value band, the cutoff and the slope rule read the outline exactly as
  before. The scale of that line is one more slope, which the field already leaves free.
* **The default canvas is far away.** The fill of −1 is `reach`: no shadow or glow reaches into empty canvas.
* **Painting outside shapes the effects.** A soft stroke below zero leaves a long, gentle outside; a hard one a short,
  steep one, so a shadow or halo follows the brushwork, as plateau detail does inside.
* **Normalize writes log space.** Its outside pass (jump flooding from the true outline) stores the true distance
  through the same curve, so a normalized field reaches `reach` with the edge as precise as before. Keep the knee above
  about two texels (the README's measurement: under that, filtering the curve rather than the distance costs edge
  precision).
* **Cutoff below zero** moves the true outline by the curve: linearly within the knee, then farther per step. A grow
  past the knee is therefore a log-scaled grow, which suits a slider that has to span a hairline and a halo.

The interior stays linear. Its levels are the nesting, and each plateau's detail is local, so it has no reach to buy.

## Why not a stencil kind

SDG §4.3 lets a new stencil kind in, placed as every stencil is. The nested field does not fit there, for three
reasons:

1. **It is not a distance.** SDG's coverage, `clamp(0.5 + (d / scale) / upp, 0, 1)` (§2), assumes a slope of one: it
   cuts the edge half a pixel either way. A painted field's slope is the artist's, by design, so a soft stroke would
   be drawn as a smear rather than as a wider outline, and a hard one would alias. Drawing it right needs the band
   rule (`|v − c − level| ≤ w`), which is another drawing rule in the normative section.
2. **Precision.** Pages are RGBA8, and a stencil holds one edge within `range / 2` texels. A nested field spans `2 ×
   depth` units and keeps plateau detail between them: the design budgets 16-bit floats to depth 16, 32-bit past it.
3. **One paint.** An SDG layer has one paint. A nested layer has a material per depth (or two, by parity), an edge
   profile, presence, and effects that read depth, plateau detail and slope. All of that would become normative
   drawing, which SDG keeps small on purpose so that every reader draws a file alike.

## The conversion: nested field → SDG

**Kind:** SYNTHESISING (the materials are data the field does not hold) and LOSSY (the live settings are fixed at
export, plateau detail survives only as baked paint, and field groups must be flattened first).

**Required arguments**, none defaulted (conversions rule 2). They are the painter's own per-layer settings, so the
editor always has them:

| argument | what it is |
|---|---|
| `Classify` | cutoff `c` (a number, or a cutoff map per texel), band half-width `w`, depth limit, edge rule with its slope threshold, softness and smoothing radius |
| `Materials` | per depth or by parity: an interior `Paint`, an outline `Paint`, an edge profile; and presence per depth |
| `Effects` | the effects in use, each with its parameters, or none |
| `Outside` | `knee` and `reach`, the log curve the outside is read by |
| sampling | oversample for masks, flatness for tracing, field density (texels a unit) |

**Layers, back to front.** For each depth `d` from 1 to the limit, with `L = 2d − 2 + c`:

| layer | stencil | paint | opacity |
|---|---|---|---|
| interior `d` | the region `v > L`, reaching to the middle of its outline | the interior material; a `sampled` paint when an effect or plateau detail shades it, carried past its edge | presence(d) |
| outline `d` | the band `\|v − L\| ≤ w`: a ring with two edges | the outline material; a `sampled` paint whose alpha is the edge profile when the profile is not a step | presence(d) |

The order is interior 1, outline 1, interior 2, outline 2 and so on. Nesting makes this the correct SDG stack with no
work: interior `d + 1` starts at `L + 2`, past the top of outline `d`'s band at `L + w` (for any `w < 1`), and each interior reaches under the
outline drawn over it, which is §5's *stack, don't tile*.

**Hops**, each shown in the route (conversions rule 3):

* An interior: **Nested → Mask** (at `v > L`) **→ Shape → MSDF → SDG layer**. Corners stay sharp, as in `sdgToMtsdf`.
* An outline under the value-band rule: **Nested → Mask** (the band) **→ Shape → MSDF**. The ring is two contours of
  opposite winding, which the nonzero fill already handles. Its width varies with the painted slope, and that variation
  is **geometry** now, so it stays sharp at any size and needs no new drawing rule.
* An outline under the slope or gated rule: **Nested → Slope mask** (central differences on a copy blurred by the
  smoothing radius, `≥` the threshold, gated by the band if the rule is gated) **→ SDF**. These lines are open and
  taper to nothing, which an SDF of a mask draws as it is. This answers the design's open question on vector export of
  slope edges: SDG carries a tapering open line natively, where one closed SVG path cannot. The softness ramp goes in
  the paint's alpha.
* A sampled paint: **Nested → Raster** by the reference renderer below, one material and depth at a time, colours run
  on past the edge (§3.2).

**Effects.**

| effect | in SDG |
|---|---|
| depth palette, interior ramp, texture by depth | the interior layers' paints |
| bevel, emboss, inner glow, height lighting, rim light | baked into the interior or outline layer's sampled paint |
| drop shadow, outer glow | a layer under interior 1 whose stencil is the depth-1 outside **in log space**, an `sdf-log` stencil (below), with the effect's grow and softness; a shadow is the same stencil at an offset origin. Paint: the effect's colour, solid, or sampled for a coloured falloff |

### What SDG needs for it: `sdf-log` stencils (1.3)

An SDG stencil is decoded linearly (§2), so it reaches `range / 2` texels and no farther: an outer glow wider than
that finds a flat zero. Log space is the fix the README already measured, and it needs one new stencil kind:

| kind | channels | meaning |
|---|---|---|
| `sdf-log` | `channel` (0–3) | one true distance, stored as `0.5 + 0.5 · asinh(d / knee) / asinh(reach / knee)`; attributes `knee` and `reach` in texels, replacing `range`; optional `grow` and `soften` in units |

Drawing it (§2, step 2.2 and 2.3 for this kind): `d = knee · sinh((e − 0.5) · 2 · asinh(reach / knee))` texels, then
`a = clamp(0.5 + (d / scale + grow) / max(upp, soften), 0, 1)`. With `grow` and `soften` both 0 it is the shape, drawn
as any stencil is. An outer glow fading from the edge to `r` units is `grow = r / 2`, `soften = r`; a drop shadow adds
an offset origin.

* **Older readers skip it**, as every unknown kind (§4.3): the drawing keeps its shapes and loses its shadows and
  glows, which is the right way to fall short. Putting `grow` and `soften` on the existing `sdf` kind instead would make
  an older reader draw a shadow as a hard copy of the shape, which is a wrong picture, so they belong to the new kind.
* **It is filtered then decoded**, as the README's shader does; the encoder keeps the knee at two texels or more.
* **The margin rule** (§3.1) becomes `reach` texels: the rectangle holds the whole effect, and coverage falls to zero
  before the clamp of step 2.1. `Field.extended` (the README's *Past the texture's edge*) is the way to go farther, and
  is not part of this.
* **It covers what MTSDF stencils were listed for** in effects (sdg.md §7) wherever the effect needs only the distance,
  not sharp corners near the edge. An MTSDF in log space is a later kind if corners are wanted too.
* `Convert` gains **SDF → SDF in log space** (EXACT within the encoding, needs `knee`, `reach`), and the Nested → SDG
  route takes its outside layer through it. `LogEncoding` already does the bytes.

**Presence as groups, or not.** The flat list above draws presence as each layer's opacity. Each depth could instead be
an SDG 1.2 group clipped to its parent's interior, which writes the containment into the file, for an editor that
re-imports or for a reader that edits layers. It costs an isolated pass per group on the GPU. Ruling 4.

## The reference renderer and its witness

`NestedRender`, on the CPU, draws a nested field with its `Classify`, `Materials` and `Effects` straight, as the design
says, and is the reference the editor's GPU view is checked against, as `SdgRender` is for SDG. It is also what bakes
the sampled paints above. The witness for the conversion:

* **At 1×:** `SdgRender(export)` against `NestedRender(field)`, within a tolerance per channel set in the test, over a
  set of fixed fields made in code: a hard disc to depth 3, a soft blob to depth 2, a stroke whose pressure falls away
  (the design's own demo), each under all three edge rules.
* **At 4×:** the export's outlines stay within a set fraction of a pixel of the nested field's own iso-lines,
  extracted by marching squares and scaled (the bound is measured on the first build, then held). This is the point of exporting to SDG rather than to a raster.

## Other edges

| from → to | kind | needs | notes |
|---|---|---|---|
| SDF → Nested | EXACT | a scale (units of value per texel) | depth 1, the field as it is: a starting canvas, or a shape to paint into |
| Nested → Mask at a level | EXACT to the oversample | level, cutoff, oversample | the hop every route above starts from |
| Nested → SDF at a level, normalized | LOSSY: slope | level, spread | the design's *Normalize, uniform*: jump flooding from the iso-line. The exact route sits beside it, Nested → Mask → Shape → SDF |
| Coverage → Nested | SYNTHESISING: plateau and depth | a depth per region, or a single depth | the design's *Turning rasters into fields*: the antialiasing ramp is already a tiny field; its slope is kept as painted slope |
| Raster → Nested | SYNTHESISING | `Trace.Options`, a depth per region | through `Trace`'s regions: Raster → Coverage per region → Nested. Nesting holds only where regions are inside one another; the rest become separate layers |
| SDG → Nested | LOSSY: paints | which layers, and the depth of each | only for drawings whose layers nest; refused otherwise |

Every one lands with its benchmark case and a rerun of `vexelray-fields-bench` (conversions rule 5).

## Not this plan

* **The editor.** Brushes, smudge, the contour tool, normalize as a dialog, the inspectors, tiles and undo: the
  design's wireframes. Where it lives is ruling 2.
* **The design's own open questions** (default brush feel, snapping targets to odd values, the default presence curve,
  cross-layer references): editor decisions, for the author.
* **Height and slice roles, and volume.** A slice of a nested field is close to the vexel field's per-cell question
  (`conversions.md`, *Not edges yet*), and waits on the same definitions.
* **Fields as a codec side stream.** A separate idea, for `vexelray-codec`, if ever.

## Order, once ruled

1. The model: `NestedField` (tiles of float16, float32 past depth 16), classification (`depth`, nearest outline,
   region) as pure functions, and tests for the guarantees the design states: nesting under union and blur, clamping as
   level of detail, scale moving no outline.
2. `NestedRender`, the reference.
3. SDG 1.3, `sdf-log`: the spec, both forms, `SdgRender`, a 1.2 reader skipping it, then the GPU painter checked
   against `SdgRender` as every SDG kind is.
4. The edges in `Convert`: SDF → Nested, Nested → Mask, SDF → SDF in log space, then the Nested → SDG route, with
   benchmark cases.
5. The witness, both scales, with an outer glow and a drop shadow among the fixed fields.
6. The raster and coverage edges.

## Rulings

1. **The nested field is a source kind, reaching SDG by conversion; SDG gains no stencil kind for it.** *Recommended*,
   for the three reasons above. The alternative, a `nested` stencil with per-depth paints, puts band classification,
   materials and effects into SDG's normative drawing.
2. **Where it lives.** *Recommended:* the kind, `NestedRender` and the conversions in a new module of this library,
   `vexelray-fields-nested`, so the editor, `Trace` and anything else can use them. The editor is the author's call:
   an application of its own (a v0.2 probe), or a mode of Rasterware.
3. **Outlines are exported as geometry**, a ring stencil per outline, so painted line weight survives at any size.
   *Recommended.* The alternative, one stencil per level and the outline drawn by SDG's half-pixel cut, loses every
   width the brush painted.
4. **Presence is layer opacity, in a flat list.** *Recommended*, cheaper on the GPU. Groups clipped to the parent can
   be an option of the conversion later, when something re-imports SDG.
5. **Field groups are flattened before export.** SDG groups composite colour, not fields, and a union or smooth union
   of fields has no exact equivalent in colour. *Recommended:* the route takes one field, and the editor's *Flatten
   field* is the hop before it.
6. **Effects beyond the shape read the outside in log space.** *Ruled 2026-11-08 by the author.* In the field, `v < 0`
   through the log curve; in SDG, a new `sdf-log` stencil kind in 1.3.
7. **`grow` and `soften` belong to `sdf-log` only**, so an older reader drops a shadow rather than drawing it hard.
   *Recommended.*
