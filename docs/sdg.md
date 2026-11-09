# SDG: signed distance graphics

**Version 1.2, draft.** What SVG is to paths, SDG is to distance fields: a drawing in layers, each layer a shape and
a colour, that stays sharp at any size. The difference is what a layer's shape is. In SVG it is an outline, to be
filled at draw time. In SDG it is a distance field, sampled once per pixel by a shader with no tessellation and no
path rasteriser, as the stack already draws its icons and glyphs.

A drawing has two forms (§4): **`.sdg`, XML**, as SVG is, to read, write by hand and exchange; and **`.sdgb`,
compiled**, one intrabuf payload, for an application to load what it built or shipped.

The reference implementation is two modules: `vexelray-fields-sdg-format` holds the file (`Sdg`, and `SdgFile` to read
and write both forms) and depends on nothing but `intrabuf-runtime`; `vexelray-fields-sdg` holds `SdgRender`, which
draws it as this document says a reader must, and `SvgToSdg.convert` and `Trace.trace`, which make one.

## 1. Model

A drawing is a **canvas** `width × height` **units**, y running down, and a list of **layers** painted back to front.

A layer is:

| part | what it is |
|---|---|
| **stencil** | where the layer is: a signed distance field, its edge where the distance is zero, positive inside |
| **paint** | what colour it is there: one colour, or a colour texture |
| **opacity** | `0..1`, times everything else |

Since 1.2, a layer may instead be a **group**: its own list of layers, shapes and groups alike, composited apart and
laid over once, with

| part | what it is |
|---|---|
| **opacity** | `0..1`, applied once to the group's composite, so where its layers overlap only the top one shows through |
| **clip** | optional: a stencil, as a layer's (§3.1); the composite is cut to its coverage |
| **mask** | optional: a list of layers drawn apart, the composite multiplied by their `luminance` or their `alpha` |

Groups nest at most 16 deep.

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
   A **group** (since 1.2) is drawn instead as: draw its layers by this section into their own `g`, starting from
   transparent; if it has a mask, draw the mask's layers likewise into `m`, and `k` is `m`'s luminance (the Rec. 709
   luma of its premultiplied colour, `0.2126 r + 0.7152 g + 0.0722 b`, which is the luma of its colour times its alpha)
   or its alpha, by the mask's kind, else `k = 1`; if it has a clip, `c` is the clip stencil's coverage at `p` (steps 1
   to 3), else `c = 1`. Then with `f = opacity · k · c`: `dst = g · f + dst · (1 − g.a · f)`. A mask of a kind the
   reader does not know hides its group.
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

One model, two forms. A reader tells them apart by the first bytes: the signature of §4.2, or (after an optional UTF-8
byte order mark and white space) `<`. Each holds exactly the model of §1, and converting one to the other and back loses
nothing.

| | `.sdg`, XML | `.sdgb`, compiled |
|---|---|---|
| for | reading, writing by hand, diffing, exchanging | an application loading its own drawings |
| pages | PNG, in the file (`data:` URI) or beside it | PNG, in the payload |
| from anyone? | yes: read defensively (§4.4) | no: intrabuf trusts its payloads |

### 4.1 XML (`.sdg`)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<sdg xmlns="https://sibarum.github.io/ns/sdg" version="1.2" width="100" height="100">
  <page id="p0" width="104" height="134" href="badge-p0.png"/>
  <page id="p1" width="76" height="68" href="data:image/png;base64,iVBORw0KGgo..."/>
  <layer name="sky">
    <stencil kind="msdf" page="p0" x="0" y="0" width="68" height="68" origin="-3.125 -3.125" scale="0.64" range="8"/>
    <paint kind="sampled" page="p1" x="0" y="0" width="68" height="68" origin="-3.125 -3.125" scale="0.64"/>
  </layer>
  <layer name="sun" opacity="0.9">
    <stencil kind="msdf" page="p0" x="68" y="68" width="29" height="29" origin="45.3125 10.9375" scale="0.64" range="8"/>
    <paint kind="solid" colour="#ffcc33" role="accent"/>
  </layer>
  <group name="glow" opacity="0.5">
    <clip kind="sdf" page="p0" x="0" y="68" width="40" height="40" origin="10 10" scale="0.64" range="8"/>
    <mask kind="luminance">
      <layer name="fade">...</layer>
    </mask>
    <layer name="ring">...</layer>
  </group>
</sdg>
```

The namespace is `https://sibarum.github.io/ns/sdg`; the root is `<sdg>`. Its children, in any order, are `<page>`s
`<layer>`s and (since 1.2) `<group>`s; pages are numbered in the order they appear, and a layer names a page that
comes before it. A reader of 1.1 skips a `<group>` whole, as any element it does not know.

| element | attributes (**bold**: required) |
|---|---|
| `sdg` | **`version`** `major.minor`; **`width`**, **`height`** in units |
| `page` | **`id`**, any name unique in the file; **`href`**, the PNG (below); `width`, `height`, which if given must be the PNG's |
| `layer` | `name` (default empty); `opacity` (default 1). Holds exactly one `stencil` and one `paint` |
| `group` | since 1.2: `name`; `opacity` (default 1). Holds at most one `clip`, at most one `mask`, and its `layer`s and `group`s in order |
| `clip` | a stencil's attributes, as `stencil` |
| `mask` | `kind`, `luminance` (default) or `alpha`. Holds `layer`s and `group`s |
| `stencil` | **`kind`**; **`page`**, a page's `id`; `channel` (default 0); **`x`**, **`y`**, **`width`**, **`height`** in texels of that page; `origin`, two numbers in units, apart by white space or a comma (default `0 0`); `scale`, texels a unit (default 1); **`range`** in texels |
| `paint`, `kind="solid"` | **`colour`**: `#rgb`, `#rgba`, `#rrggbb` or `#rrggbbaa`, straight, alpha `ff` if left out; `role` (default none, §3.2) |
| `paint`, `kind="sampled"` | **`page`**, **`x`**, **`y`**, **`width`**, **`height`**, `origin`, `scale`, as a stencil's |

**Numbers** are decimal: an optional sign, digits with an optional point, an optional exponent (`-3.125`, `1.0E-7`),
finite. A writer writes the shortest decimal that reads back as the same value. **Pages** are PNG images of 8 bits a
channel, not interlaced, in any colour type (grey, grey and alpha, RGB, RGBA, palette), read as RGBA8; gamma and colour
profiles are not applied. A page's `href` is either a `data:image/png;base64,` URI, white space in its base64 ignored,
or a relative URI reference to a file beside the drawing or in a folder below it: no scheme, no authority, no leading
`/`, no `..` segment. A writer writes RGBA.

### 4.2 Compiled (`.sdgb`)

The eight bytes `89 53 44 47 0D 0A 1A 0A` (`\x89SDG\r\n\x1A\n`, as PNG's, so a 7-bit channel or a line-ending rewrite
is caught), then one [intrabuf](https://github.com/sibarum/intrabuf) payload, built-in header, of the message
`vexelray.sdg.Drawing`, whose major and minor are the drawing's version. Every message is intrabuf FULL. The schema is
the records of `dev.vexelray.fields.sdg.schema`; in intrabuf's types, fields in order:

| message | fields |
|---|---|
| `vexelray.sdg.Drawing` 1.2 | `float64` width, `float64` height, `list<Page>` pages, `list<Layer>` layers |
| `vexelray.sdg.Page` | `bytes` png, as in §4.1 |
| `vexelray.sdg.Layer` 1.1 | `string` name, `float32` opacity, `Stencil` stencil, `Paint` paint; since 1, `Group` group (absent for a shape) |
| `vexelray.sdg.Stencil` | `string` kind, `int32` page, channel, x, y, width, height, `float64` originX, originY, scale, `float32` range |
| `vexelray.sdg.Paint` | `string` kind, `Solid` solid (or absent), `Sampled` sampled (or absent): the one its kind names |
| `vexelray.sdg.Solid` 1.1 | `fixed32` rgba, `0xRRGGBBAA`; since 1, `string` role (absent for none) |
| `vexelray.sdg.Sampled` | `int32` page, x, y, width, height, `float64` originX, originY, scale |
| `vexelray.sdg.Group` | `list<Layer>` layers, `Stencil` clip (or absent), `Mask` mask (or absent) |
| `vexelray.sdg.Mask` | `string` kind, `list<Layer>` layers |

A group layer's stencil is of the kind `group`, one texel of page 0, and its paint of the kind `group`: a reader of 1.1
finds the rectangle in its page, does not know the kind, and skips the layer, drawing the rest.

### 4.3 Compatibility

Files outlive the readers that read them, both ways, by these rules, which both forms keep:

* **A reader skips what it does not know.** In XML: an element or attribute it does not know, and every element of
  another namespace (another application's, which SDG leaves alone). Compiled: a field it does not know, as intrabuf FULL
  skips one. In both: a layer whose stencil or paint is of a kind it does not know (§3) is read, and skipped when drawn.
* **A writer only adds.** A new attribute, element or field is optional, with a default that draws as before, and the
  minor version goes up. In the schema, a field goes at the end of its message with `since`. Nothing is removed,
  reordered, renamed or retyped.
* **A new kind of stencil is placed as every stencil is** (page, channel, rectangle, origin, scale, range), so a reader
  can still tell where it is without knowing what it holds. A new kind of paint brings its fields in a message of its
  own, on the end of `Paint`, and as attributes (or children) of `<paint>`.
* **The major version** goes up only for a change an older reader would draw wrong. A reader refuses a file whose major
  version it does not know, whole.

### 4.4 Reading a file from anyone

SDG is for exchanging drawings, so a reader of the XML form takes a file to be from anyone. It reads no DTD (a file
with a `<!DOCTYPE>` is refused), expands no entity, and fetches nothing: a page is in the file, or a file beside it,
and an `href` that leaves the drawing's folder is refused. Before drawing, it checks that every stencil and sampled
paint names a page there is and lies inside it, that every number is finite, every scale and range positive, every
channel 0–3 and every opacity in 0–1, so a drawing that is read never reads outside its pages. The compiled form is
checked the same way after decoding, but intrabuf promises less of its decoder (its payloads are a system's own), so a
drawing from elsewhere comes as XML and is compiled by the application that ships it.

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
  the edge (see the README, *Log space* and *Past the texture's edge*). Field Painter's plan proposes an `sdf-log` kind (1.3)
  for effects that need only the distance ([`field-painter.md`](field-painter.md)).
* **Sharp corners from a raster.** A traced raster's stencils are SDFs from a mask, so its corners are as round as the
  raster's pixels make them, and a feature thinner than half a pixel is lost. Fitting curves and corners to a region's
  outline, then making an MSDF from those, would sharpen them. The conversion that does it now exists (Mask → Shape → MSDF,
  `Convert.maskToShape`, see [`conversions.md`](conversions.md)); `Trace` does not use it yet.
* **A `.vxa` item.** The same model as an item kind of `vexelray-codec-asset`'s file, beside its `DistanceField`, so an
  asset carries its drawings.
