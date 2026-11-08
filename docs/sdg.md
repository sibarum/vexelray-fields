# SDG: signed distance graphics

**Version 1.1, draft.** What SVG is to paths, SDG is to distance fields: a drawing in layers, each layer a shape and
a colour, that stays sharp at any size. The difference is what a layer's shape is. In SVG it is an outline, to be
filled at draw time. In SDG it is a distance field, sampled once per pixel by a shader with no tessellation and no
path rasteriser, as the stack already draws its icons and glyphs.

The reference implementation is two modules: `vexelray-fields-sdg-format` holds the file (`Sdg`, and `SdgFile` to read
and write it) and depends on nothing; `vexelray-fields-sdg` holds `SdgRender`, which draws it as this document says a
reader must, and `SvgToSdg.convert` and `Trace.trace`, which make one.

## 1. Model

A drawing is a **canvas** `width × height` **units**, y running down, and a list of **layers** painted back to front.

A layer is:

| part | what it is |
|---|---|
| **stencil** | where the layer is: a signed distance field, its edge where the distance is zero, positive inside |
| **paint** | what colour it is there: one colour, or a colour texture |
| **opacity** | `0..1`, times everything else |

All texels live in **pages**: RGBA8 images, any size up to 65535 × 65535 (an encoder keeps to 4096, what every Vulkan
device takes). A stencil or a sampled paint names a rectangle of one page, and maps it onto the canvas by an **origin**
(the canvas point at the rectangle's top-left corner) and a **scale** (texels per unit). Different layers may sit at
different densities: a small detailed part can be denser than the background behind it.

## 2. Drawing

This section is normative. A reader that draws differently is wrong, and `SdgRender` is the reference.

For a pixel whose centre is the canvas point `p`, with `upp` canvas units per pixel (from the view, or the screen-space
derivative of `p`):

1. Start from transparent, premultiplied: `dst = (0, 0, 0, 0)`.
2. For each layer, back to front, skipping any layer a reader does not know (§4):
   1. **Texel coordinates.** `t = (p − origin) · scale`, in texels from the rectangle's corner. If `t` is outside
      `[0, w] × [0, h]`, the layer does not cover `p`; go on to the next layer.
   2. **Distance.** Clamp `t` to `[0.5, w − 0.5] × [0.5, h − 0.5]`, so nothing outside the rectangle is ever read, and
      read the page there **between texel centres** (bilinear), each channel on its own. Then:
      `sdf`: `e` is the stencil's channel; `msdf`: `e = median(r, g, b)`, taken after filtering, never before.
      The distance is `d = (e − 0.5) · range` texels, `d / scale` units.
   3. **Coverage.** `a = clamp(0.5 + (d / scale) / upp, 0, 1)`: the edge cut half a pixel either side.
   4. **Colour.** `c`, straight RGBA in `0..1`, from the paint at `p` (§3.2).
   5. **Over.** With `α = a · c.a · opacity`: `dst.rgb = c.rgb · α + dst.rgb · (1 − α)`, `dst.a = α + dst.a · (1 − α)`.
3. `dst` is the pixel, premultiplied.

Colour is blended as it is stored, sRGB-encoded, as SVG and browsers blend by default; a page is read as UNORM, never
through an sRGB format's decode, since half its pages are distances.

## 3. Kinds

Every kind is a **name**, not a number. A reader meeting a name it does not know reads the layer as unknown and skips
it when drawing; the rest of the drawing draws (§4).

### 3.1 Stencils

| kind | channels | meaning |
|---|---|---|
| `sdf` | `channel` (0–3) | one true distance; four SDF layers can share one rectangle's area, a channel each |
| `msdf` | red, green, blue (`channel` is 0) | Chlumský's multi-channel field: corners stay sharp under magnification |

A distance `d` texels is stored as the byte `round(255 · clamp(0.5 + d / range, 0, 1))`: the edge at one half,
`range / 2` texels the farthest it reaches each way. **An encoder leaves a margin** of at least `range / 2` texels
between the shape and the rectangle's border, so that coverage has fallen to zero before step 2.1 cuts it off, at any
magnification and at minification as long as `upp ≤ range / scale` (a pixel no wider than `range` texels).

### 3.2 Paints

| kind | colour at `p` |
|---|---|
| `solid` | one straight RGBA8 colour; or, if it names a **role** the reader's theme has a colour for, that colour |
| `sampled` | a rectangle of straight RGBA8 texels, mapped onto the canvas by its own origin and scale, read as a stencil is (clamped half a texel in, bilinear) |

A sampled paint's colours **run on past the layer's edge**: an encoder fills the texels outside the shape from the
colours inside it, so a read near the edge never takes in a colour from beyond it. Its texels need be no denser than the
colour varies; edges are the stencil's business.

**Roles** (since 1.1) let one drawing follow a light and a dark theme. A solid paint may name a colour of the
reader's theme, such as `foreground`, `accent` or `muted`, and stores the colour it was authored in beside the name.
A reader whose theme has a colour of that name draws that colour, with the stored alpha. A reader without one, or with
no theme, draws the stored colour. That covers every reader older than 1.1 too, which never reads the name. Which
names a theme has is the reader's business, not this format's. A paint with no role, or with an empty name, is drawn
as stored in every theme.

## 4. File

`.sdg`. Little-endian throughout. `u8`, `u16`, `u32`: unsigned integers; `f32`, `f64`: IEEE 754 floats; `str`: a
`u16` length and that many bytes of UTF-8; `block`: a `u32` length and that many bytes.

```
signature   89 53 44 47 0D 0A 1A 0A        "\x89SDG\r\n\x1A\n", as PNG's: a 7-bit channel or a line-ending
                                            rewrite is caught
chunk*      tag (4 ASCII bytes), length (u32), that many bytes
```

| tag | contents, in order |
|---|---|
| `SDGH` | header, first. `u16` major, `u16` minor, `f64` width, `f64` height, `u32` pages, `u32` layers |
| `PAGE` | one per page, in order (pages are numbered from 0). `u16` width, `u16` height, `u8` filter, then the texels deflated (zlib): `width · height · 4` bytes, rows top first. Filter 0: as they are; filter 1: each byte less the byte four before it in its row, the first texel of a row as it is |
| `LAYR` | one per layer, back to front. `str` name, `f32` opacity, `block` stencil, `block` paint |
| `SEND` | the end, empty. Nothing after it is read |

**Stencil block:** `str` kind, `u16` page, `u8` channel, `u16` x, `u16` y, `u16` width, `u16` height, `f64` originX,
`f64` originY, `f64` scale, `f32` range.

**Paint block:** `str` kind, then by kind. `solid`: `u32` colour, `0xRRGGBBAA`, then (since 1.1) `str` role, empty
for none; a block that ends at the colour has no role. `sampled`: `u16` page, `u16` x,
`u16` y, `u16` width, `u16` height, `f64` originX, `f64` originY, `f64` scale.

### Compatibility

Files outlive the readers that read them, both ways, by these rules:

* **A reader skips what it does not know:** a chunk with a tag it does not know, the bytes past the end of a chunk or a
  block it has read what it knows of, and (§3) a layer of a kind it does not know.
* **A writer only adds, at the end:** a new field goes at the end of its chunk or block and the minor version goes up.
  Nothing is removed, reordered or retyped.
* **A new kind of stencil starts as every stencil does** (kind, page, channel, rectangle, origin, scale, range), so a
  reader can still tell where it is without knowing what it holds.
* **The major version** goes up only for a change an older reader would draw wrong. A reader refuses a file whose major
  version it does not know, whole.

## 5. Making one (informative)

**From an SVG.** One layer per fill and per stroke, in the order SVG paints them. A fill by the nonzero rule has its
outline read exactly, and its stencil is an MSDF; a stroke or an even-odd fill is drawn as a fine mask and its stencil
is an SDF. One colour is `solid`; a gradient is baked into a `sampled` paint at the field's density. A group's opacity,
mask or clip composites several parts at once, which separate layers cannot; such a drawing is refused, and can be
rendered and traced instead.

**From a raster** (tracing). The colours are clustered into regions; each antialiased pixel is taken as a blend of the
two regions round it whose blend comes nearest its colour, in premultiplied colour, which puts each edge within a
fraction of a pixel; regions are cut on a fine grid as they stack, each the highest-layered region that with those over
it holds at least half the share there; a region's paint is its mean colour if it hardly varies, otherwise its own
pixels as a texture, carried outward past its edge.

**Stack, don't tile.** Two layers whose edges meet exactly, each antialiased against the other, let the background show
through the seam: where each covers half a pixel, together they cover three quarters. So a layer under others reaches
a little way (two texels, say) beneath the layers over it, and an edge between two regions is drawn once, by the upper
one, over the lower one solid. SVG has the same seam between abutting fills, and SDG made from an SVG keeps it, as it
keeps everything else the SVG says.

## 6. In a shader (informative)

Per layer, given its page bound as a sampler with linear filtering and clamp-to-edge:

```glsl
float median(vec3 v) { return max(min(v.r, v.g), min(max(v.r, v.g), v.b)); }

// p: the canvas point; upp: canvas units per pixel, e.g. length(fwidth(p)) / sqrt(2)
float coverage(sampler2D page, vec4 rect, vec2 origin, float scale, float range, int kind, int channel,
               vec2 p, float upp) {
    vec2 t = (p - origin) * scale;
    if (any(lessThan(t, vec2(0.0))) || any(greaterThan(t, rect.zw))) return 0.0;
    vec4 s = texture(page, (rect.xy + clamp(t, vec2(0.5), rect.zw - 0.5)) / vec2(textureSize(page, 0)));
    float e = kind == 1 ? median(s.rgb) : s[channel];
    return clamp(0.5 + (e - 0.5) * range / scale / upp, 0.0, 1.0);
}

// over, premultiplied:   float a = coverage(...) * colour.a * opacity;   dst = vec4(colour.rgb * a, a) + dst * (1.0 - a);
```

A layer's rectangle in canvas units bounds what it can cover, so a renderer can draw each layer as one quad over that
rectangle and let the GPU blend, or draw the whole canvas as one quad and loop over the layers in the shader.

## 7. Not yet

* **Analytic gradients.** A gradient is baked into texels today; `linear` and `radial` paint kinds with their stops
  would be smaller and exact.
* **MTSDF stencils**, the true distance in alpha, for effects (glows, shadows, outlines) that need distance far from
  the edge (see the README, *Log space* and *Past the texture's edge*).
* **Groups**: a layer list composited apart and laid over with one opacity, mask or clip, so every SVG converts.
* **Sharp corners from a raster.** A traced raster's stencils are SDFs from a mask, so its corners are as round as the
  raster's pixels make them, and a feature thinner than half a pixel is lost. Fitting curves and corners to a region's
  outline, then making an MSDF from those, would sharpen them.
* **A `.vxa` item.** The same model as an item kind of `vexelray-codec-asset`'s file, beside its `DistanceField`, so an
  asset carries its drawings.
