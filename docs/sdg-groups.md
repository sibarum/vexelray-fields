# SDG 1.2: groups, with opacity, clips and masks

**Status: ruled 2026-11-08, all five as recommended. Built the same day: the spec (1.2), both forms, the reader's
checks, `SdgRender`, and the SVG encoder (`Svg.tree`, groups, clips, masks). A real 1.1 reader was run on a 1.2 file
with a group: it reads both forms and draws the rest. Not built yet: the canvas's isolated pass, so the GPU skips a
group (and says so once, `canvas:sdg-group`), and the encoder's cheap paths (pushing an opacity onto children that do
not overlap).**

## Why

SVG has two kinds of opacity, and SDG 1.1 has one. Opacity on **one shape** is a layer's own `opacity`, composited
over what is under it, exactly as SVG composites it. Opacity on a **group** is different: SVG draws the group's
children into a buffer of their own and lays that buffer down once, so where two children overlap only the top one
shows, at the group's opacity. A **mask** needs the same buffer, and a **clip** cuts every child by one shape. SDG
layers are composited straight onto the canvas, so none of the three can be said, and `SvgToSdg` refuses such a drawing
rather than draw it wrong. The suite's own background and file-type icons are refused for exactly this (slice 5 of the
SDG plan draws them from the SVG instead). The author ruled 2026-11-08 that SDG needs a way to say them.

## The model

A layer becomes one of two things: a **shape** (a stencil and a paint, as in 1.1) or a **group**:

| part | what it is |
|---|---|
| **layers** | its own list, painted back to front, shapes and groups alike |
| **opacity** | `0..1`, the group's, applied once to the composite |
| **clip** | optional: a stencil; the composite is cut to its coverage |
| **mask** | optional: a list of layers, drawn apart; the composite is multiplied by their **luminance** or their **alpha** (named, as SVG's `mask-type`) |

### Drawing a group (normative, §2 of the spec, new step)

For a pixel `p`, a group is drawn as a layer is, with its colour and coverage from its own composite:

1. Draw the group's layers by §2 into their own `g = (0, 0, 0, 0)`, premultiplied.
2. If it has a mask, draw the mask's layers by §2 into `m`, and take `k = luminance(m.rgb) · m.a`-style per its kind
   (`luminance`: the Rec. 709 luma of the unpremultiplied colour times alpha, as SVG; `alpha`: `m.a`). Else `k = 1`.
3. If it has a clip, `c` is the clip stencil's coverage at `p` (§2 steps 1–3). Else `c = 1`.
4. `g · (opacity · k · c)` goes **over** `dst`, premultiplied.

A clip as coverage, not a hard cut: that is what browsers draw for an antialiased `clip-path`, and it keeps the edge
as sharp as any other at any size.

### Both forms

* **XML.** `<group opacity=".5">` holds `<layer>` and `<group>` elements; `<clip>` is a `<stencil>` in it;
  `<mask kind="luminance">` holds layers. A 1.1 reader skips the unknown `<group>` whole and draws the rest (§4.3, as it
  skips an unknown kind of layer).
* **Compiled.** `vexelray.sdg.Layer` gains, at its end, `since 2`: `Group group` (absent for a shape), with
  `vexelray.sdg.Group`: `list<Layer>` layers, `Stencil` clip (or absent), `Mask` mask (or absent); and
  `vexelray.sdg.Mask`: `string` kind, `list<Layer>` layers. A group layer's stencil kind is `"group"`, which a 1.1 reader
  does not know and so skips, by the rule it already keeps. Minor 2, major unchanged: a 1.1 reader draws a 1.2 file
  with its groups missing, which is the compatibility SDG already promises for kinds it does not know.
* **Limits a reader checks** (§4.4): groups nest at most 16 deep, and every list and stencil in them is checked as a
  top-level one is.

## Drawing it fast

Most groups never need the buffer. An encoder and a renderer between them take the cheapest exact path:

| the group | how it is drawn | exact? |
|---|---|---|
| opacity 1, no mask, no clip | its layers are spliced into the parent's | yes |
| opacity only, its children do not overlap | the encoder pushes the opacity onto each child | yes |
| a clip, no mask, opacity 1 or children apart | each child's coverage times the clip's, in one pass: two distance reads a pixel | yes |
| children overlap under an opacity, or a mask | an **isolated pass**: drawn into a buffer of its own, then laid over | yes |

**The isolated pass on the GPU.** The canvas draws its elements in one render pass today. A group that needs a buffer
ends it: the group's layers draw into a transient colour target the size of the group's bounds on screen (the stack's
`SampledColorTarget`), the mask's into another, and one quad lays the group over the parent with opacity, mask and clip
in its shader. Then the parent's pass resumes. Its cost is a render-pass break and a target per group per frame, which
the comparison suite (the next piece of work) will measure next to SVG's own offscreen groups.

**On the CPU,** `SdgRender` does the same in buffers, and stays the reference the GPU is checked against.

## The encoders

* **SVG → SDG** converts a group's opacity, `mask` and `clip-path` instead of refusing them, taking the cheapest row
  above it can prove: it knows when children overlap (their coverage at the field's resolution intersects).
* A clip that is a shape is converted exactly (the outline traced to an MSDF stencil); a mask is converted as a layer
  list like any other.

## Rulings

**Ruled 2026-11-08: all five accepted as recommended.**


1. **A group is a kind of layer** (a stencil kind `"group"`, its children inside it), so it can sit anywhere in the
   order and nest, and 1.1 readers skip it by the rule they already keep. *Recommended.* The alternative, a flat list
   with group markers, is smaller to parse and worse at everything else.
2. **Masks in both of SVG's kinds**, luminance (SVG's default) and alpha. *Recommended*, so every SVG mask converts.
3. **A clip is coverage**, multiplied, not a hard cut. *Recommended*: what browsers draw, and sharp at any size.
4. **The GPU draws isolated groups in a pass of their own** (exact), rather than baking such a group to a sampled
   texture at build time (which would rasterise it, and lose "sharp at any size"). *Recommended.*
5. **Nesting at most 16 deep**, refused past it, as the SVG reader refuses past 64.

Once ruled, the order: the spec and both forms (with the compatibility tests, a 1.1 reader on a 1.2 file); `SdgRender`;
the SVG encoder; the canvas's isolated pass, checked against `SdgRender`; then the suite's background and file-type
icons go back to the SDG route.
