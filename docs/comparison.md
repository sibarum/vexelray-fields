# SDG, SVG and rasters compared

**Written by vexelray-gui-format-bench's `Publish`, not by hand.** The same art in each form, at the
same sizes: what it costs on disk, to load and to draw, and how far what it draws is from the truth.
Run it again with:

```
mvn -pl vexelray-gui-format-bench -am install -DskipTests
mvn -pl vexelray-gui-format-bench exec:exec
```

## The run

| | |
| --- | --- |
| Date | 2026-11-08 |
| CPU | Intel(R) Core(TM) Ultra 7 255HX |
| Cores the JVM sees | 20 |
| OS | Windows 11 10.0 |
| JDK | Java HotSpot(TM) 64-Bit Server VM 25.0.3+9-LTS-jvmci-b01 |
| JMH | average time per operation, 1 fork, 2 warm-up and 3 measured iterations of 1 s, one thread |

## Method

* **The art** is six of the suite's own drawings, square, as SVG:

  * `badge`: a gradient, flat fills, a stroke and a star: vexelray-fields' test badge
  * `rasterware`: Rasterware's mark: rounded panels, bars and a needle
  * `vexelray-logo`: the VexelRay mark: facets, an orb with a gradient
  * `mainframe`: MainFrame's mark: a terminal, text strokes
  * `background-icon`: a hidden window's icon: a dashed rim and a group at half opacity
  * `file-icon`: a file-type icon: a page cut by a clip path

* **The forms.** The SVG itself. The SDG made from it, 128 texels across, as the icon plugin makes a mark, compiled (`.sdgb`) and as XML (`.sdg`).
  A PNG of it at 256 and at 1024 pixels, drawn by imagelib, as an application ships a picture.
* **The contenders**, each drawing the art at 64, 256 and 1024 pixels square on the CPU:

  * **SDG, Java**: the drawing by SdgRender (Java, one thread): the reference, written to be read, not tuned
  * **SDG, C**: the drawing by sdg-signed-distance-graphics' C rasterizer (native, one thread, /O2), called through the foreign-function API: SdgRender's pixels, byte for byte, with the work it can skip skipped (tiles wholly in or out of a shape, texel taps once a row and a column)
  * **SDG, C, all cores**: the same C rasterizer on one thread a processor (20 here), the picture cut into bands of rows: the same pixels; every other contender here is one thread
  * **SVG, imagelib**: the SVG rasterised by imagelib (native, built from Rust), parse included, as Ico.java made icons
  * **SVG, Java**: the SVG, parsed once, rasterised by vexelray-fields' renderer (Java, one thread) at 4 × 4 samples a pixel
  * **PNG 256, scaled**: a 256 px PNG, decoded once, scaled to the size by bilinear filtering (Java)
  * **PNG 1024, scaled**: a 1024 px PNG, decoded once, scaled to the size by bilinear filtering (Java)
  * **SDG, GPU**: the SDG drawn by a node in a headless window, the stack's own renderer: its error from
    a capture (`GuiApp.capture`), its time from Vulkan timestamps (`GuiApp.gpuMillis`): 8 copies in a frame,
    less an empty frame, over 8, the median of 30 frames.
  * **PNG 1024, GPU**: the 1024 px PNG drawn the way a picture is, a still feed with its mip chain,
    sampled at the size: the GPU's own way to show a raster, the one to set SDG against there.

* **The truth** is the SVG drawn by imagelib at four times the size and averaged down four by four.
  Error is in premultiplied colour, every channel of every pixel, in steps of 1/255: the mean, the
  worst, and the share of pixels with a channel more than 8 off. The GPU's is over a grey page, its truth laid over the same grey.
* **Read with care:** imagelib is also the truth's renderer, so its own row measures antialiasing
  alone; an SDG's error includes what making it lost (its texels); a PNG is exact only at its size.

## Size on disk

Bytes, as each form is shipped (not compressed further).

| Art | SVG | SDG .sdgb | SDG .sdg | PNG 256 | PNG 1024 |
| --- | ---: | ---: | ---: | ---: | ---: |
| `badge` | 689 B | 9.3 KiB | 14.0 KiB | 8.3 KiB | 30.6 KiB |
| `rasterware` | 1.5 KiB | 12.8 KiB | 19.5 KiB | 9.5 KiB | 40.4 KiB |
| `vexelray-logo` | 2.0 KiB | 16.2 KiB | 24.7 KiB | 16.0 KiB | 89.7 KiB |
| `mainframe` | 1.2 KiB | 11.2 KiB | 17.0 KiB | 9.1 KiB | 38.1 KiB |
| `background-icon` | 1.7 KiB | 17.4 KiB | 26.2 KiB | 11.4 KiB | 46.2 KiB |
| `file-icon` | 664 B | 5.2 KiB | 8.0 KiB | 2.7 KiB | 14.3 KiB |

## Load

From the bytes to what draws: an SVG parsed (vexelray-fields), an SDG read with its pages
decoded, a PNG decoded (imagelib). imagelib's SVG path parses on every draw, so it has no load of its own.

| Art | SVG | SDG .sdgb | SDG .sdg | PNG 256 | PNG 1024 |
| --- | ---: | ---: | ---: | ---: | ---: |
| `badge` | 54.0 ± 19.6 µs | 640.7 ± 171.2 µs | 838.1 ± 201.0 µs | 142.4 ± 57.8 µs | 3.83 ± 1.36 ms |
| `rasterware` | 56.3 ± 23.5 µs | 1.33 ± 0.13 ms | 1.58 ± 0.33 ms | 123.7 ± 27.6 µs | 3.52 ± 0.08 ms |
| `vexelray-logo` | 63.3 ± 6.1 µs | 875.9 ± 455.4 µs | 1.10 ± 0.37 ms | 108.4 ± 33.1 µs | 3.51 ± 0.14 ms |
| `mainframe` | 57.0 ± 3.5 µs | 1.12 ± 0.07 ms | 1.36 ± 0.35 ms | 136.7 ± 155.4 µs | 3.97 ± 1.87 ms |
| `background-icon` | 59.4 ± 4.1 µs | 1.63 ± 0.16 ms | 1.90 ± 0.36 ms | 130.4 ± 19.8 µs | 3.87 ± 0.27 ms |
| `file-icon` | 48.9 ± 0.3 µs | 282.3 ± 20.0 µs | 428.3 ± 303.6 µs | 76.7 ± 8.7 µs | 3.21 ± 1.18 ms |

## At 64 pixels

### Draw time

| Art | SDG, Java | SDG, C | SDG, C, all cores | SVG, imagelib | SVG, Java | PNG 256, scaled | PNG 1024, scaled |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| `badge` | 326.6 ± 70.8 µs | 79.6 ± 5.8 µs | 79.6 ± 5.1 µs | 12.03 ± 1.05 ms | 1.35 ± 0.03 ms | 85.2 ± 2.1 µs | 84.0 ± 17.4 µs |
| `rasterware` | 435.4 ± 15.1 µs | 113.5 ± 5.8 µs | 113.7 ± 1.5 µs | 11.98 ± 1.04 ms | 1.53 ± 0.87 ms | 89.4 ± 5.9 µs | 87.3 ± 1.3 µs |
| `vexelray-logo` | 289.9 ± 6.5 µs | 77.1 ± 13.8 µs | 79.8 ± 3.3 µs | 12.46 ± 15.34 ms | 942.1 ± 148.1 µs | 58.1 ± 2.4 µs | 57.0 ± 0.7 µs |
| `mainframe` | 434.2 ± 41.9 µs | 106.6 ± 40.5 µs | 105.0 ± 2.4 µs | 11.39 ± 1.26 ms | 1.43 ± 0.41 ms | 90.3 ± 22.0 µs | 89.7 ± 3.6 µs |
| `background-icon` | 517.1 ± 131.1 µs | 136.1 ± 20.2 µs | 133.4 ± 34.0 µs | 13.23 ± 3.30 ms | 1.73 ± 0.76 ms | 87.4 ± 8.6 µs | 90.3 ± 5.2 µs |
| `file-icon` | 150.3 ± 9.4 µs | 47.3 ± 2.9 µs | 47.2 ± 6.6 µs | 13.33 ± 14.30 ms | 778.4 ± 195.6 µs | 63.0 ± 1.0 µs | 63.8 ± 3.4 µs |

### GPU draw time

One drawing at the size, by Vulkan timestamps (see *Method*).

| Art | SDG, GPU | PNG 1024, GPU |
| --- | ---: | ---: |
| `badge` | 1.0 µs | 0.0 µs |
| `rasterware` | 1.6 µs | 0.1 µs |
| `vexelray-logo` | 1.2 µs | 0.0 µs |
| `mainframe` | 1.5 µs | 0.1 µs |
| `background-icon` | 6.9 µs | 0.1 µs |
| `file-icon` | 6.5 µs | 0.1 µs |

### Error against the truth

Mean / worst / share of pixels past 8.

| Art | SDG, Java | SDG, C | SDG, C, all cores | SVG, imagelib | SVG, Java | PNG 256, scaled | PNG 1024, scaled | SDG, GPU |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| `badge` | 0.91 / 55 / 2.4% | 0.91 / 55 / 2.4% | 0.91 / 55 / 2.4% | 1.31 / 68 / 6.1% | 0.61 / 21 / 4.3% | 1.22 / 51 / 10.4% | 4.65 / 124 / 12.4% | 0.62 / 54 / 1.9% |
| `rasterware` | 0.70 / 32 / 5.2% | 0.70 / 32 / 5.2% | 0.70 / 32 / 5.2% | 1.10 / 73 / 8.9% | 0.84 / 28 / 7.1% | 2.55 / 65 / 12.1% | 3.93 / 108 / 13.6% | 0.70 / 33 / 3.5% |
| `vexelray-logo` | 1.19 / 100 / 4.8% | 1.19 / 100 / 4.8% | 1.19 / 100 / 4.8% | 1.01 / 51 / 4.5% | 0.55 / 24 / 2.3% | 1.84 / 53 / 8.8% | 3.04 / 107 / 9.3% | 0.97 / 40 / 2.4% |
| `mainframe` | 0.56 / 55 / 4.0% | 0.56 / 55 / 4.0% | 0.56 / 55 / 4.0% | 1.06 / 73 / 10.3% | 0.74 / 27 / 7.4% | 2.19 / 53 / 12.5% | 2.78 / 108 / 13.8% | 0.56 / 54 / 2.1% |
| `background-icon` | 0.90 / 41 / 7.1% | 0.90 / 41 / 7.1% | 0.90 / 41 / 7.1% | 1.81 / 87 / 10.2% | 1.64 / 34 / 8.3% | 3.17 / 62 / 16.8% | 6.63 / 124 / 18.0% | 0.81 / 20 / 5.2% |
| `file-icon` | 0.28 / 14 / 0.5% | 0.28 / 14 / 0.5% | 0.28 / 14 / 0.5% | 0.17 / 19 / 0.9% | 0.19 / 18 / 1.2% | 0.62 / 55 / 2.9% | 1.08 / 102 / 2.9% | 0.52 / 9 / 0.0% |

## At 256 pixels

### Draw time

| Art | SDG, Java | SDG, C | SDG, C, all cores | SVG, imagelib | SVG, Java | PNG 256, scaled | PNG 1024, scaled |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| `badge` | 5.07 ± 1.10 ms | 955.8 ± 22.8 µs | 409.2 ± 990.1 µs | 12.27 ± 0.93 ms | 20.31 ± 1.21 ms | 1.33 ± 0.01 ms | 1.33 ± 0.03 ms |
| `rasterware` | 6.68 ± 0.08 ms | 1.34 ± 0.17 ms | 469.2 ± 497.7 µs | 13.02 ± 7.61 ms | 22.00 ± 5.61 ms | 1.37 ± 0.24 ms | 1.35 ± 0.02 ms |
| `vexelray-logo` | 4.31 ± 0.19 ms | 925.5 ± 273.6 µs | 474.6 ± 1063.1 µs | 12.92 ± 15.45 ms | 14.29 ± 1.18 ms | 895.7 ± 11.1 µs | 893.0 ± 58.5 µs |
| `mainframe` | 6.83 ± 2.19 ms | 1.24 ± 0.02 ms | 484.4 ± 862.1 µs | 13.25 ± 2.97 ms | 23.11 ± 5.45 ms | 1.38 ± 0.02 ms | 1.39 ± 0.04 ms |
| `background-icon` | 8.02 ± 0.35 ms | 1.96 ± 0.35 ms | 589.9 ± 233.8 µs | 13.77 ± 1.32 ms | 27.71 ± 3.83 ms | 1.40 ± 0.05 ms | 1.41 ± 0.07 ms |
| `file-icon` | 2.27 ± 0.15 ms | 986.6 ± 141.3 µs | 374.8 ± 182.1 µs | 12.19 ± 3.42 ms | 13.65 ± 0.91 ms | 985.7 ± 12.1 µs | 989.7 ± 19.2 µs |

### GPU draw time

One drawing at the size, by Vulkan timestamps (see *Method*).

| Art | SDG, GPU | PNG 1024, GPU |
| --- | ---: | ---: |
| `badge` | 8.2 µs | 2.8 µs |
| `rasterware` | 15.4 µs | 2.7 µs |
| `vexelray-logo` | 9.4 µs | 2.7 µs |
| `mainframe` | 14.7 µs | 2.8 µs |
| `background-icon` | 25.5 µs | 2.8 µs |
| `file-icon` | 10.9 µs | 2.8 µs |

### Error against the truth

Mean / worst / share of pixels past 8.

| Art | SDG, Java | SDG, C | SDG, C, all cores | SVG, imagelib | SVG, Java | PNG 256, scaled | PNG 1024, scaled | SDG, GPU |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| `badge` | 0.29 / 59 / 1.3% | 0.29 / 59 / 1.3% | 0.29 / 59 / 1.3% | 0.23 / 62 / 0.9% | 0.06 / 28 / 0.3% | 0.23 / 62 / 0.9% | 0.63 / 72 / 2.9% | 0.27 / 59 / 0.5% |
| `rasterware` | 0.29 / 161 / 2.0% | 0.29 / 161 / 2.0% | 0.29 / 161 / 2.0% | 0.26 / 51 / 2.3% | 0.22 / 23 / 1.8% | 0.26 / 51 / 2.3% | 0.66 / 77 / 3.3% | 0.38 / 161 / 1.8% |
| `vexelray-logo` | 0.62 / 148 / 2.6% | 0.62 / 148 / 2.6% | 0.62 / 148 / 2.6% | 0.22 / 62 / 1.2% | 0.13 / 24 / 0.4% | 0.22 / 62 / 1.2% | 0.48 / 65 / 2.4% | 0.93 / 76 / 2.3% |
| `mainframe` | 0.24 / 98 / 1.6% | 0.24 / 98 / 1.6% | 0.24 / 98 / 1.6% | 0.24 / 51 / 2.4% | 0.19 / 28 / 1.8% | 0.24 / 51 / 2.4% | 0.55 / 64 / 3.2% | 0.34 / 98 / 1.6% |
| `background-icon` | 0.49 / 96 / 2.1% | 0.49 / 96 / 2.1% | 0.49 / 96 / 2.1% | 0.57 / 100 / 2.6% | 0.42 / 39 / 2.0% | 0.57 / 100 / 2.6% | 0.81 / 69 / 4.2% | 0.47 / 96 / 1.9% |
| `file-icon` | 0.11 / 19 / 0.2% | 0.11 / 19 / 0.2% | 0.11 / 19 / 0.2% | 0.05 / 28 / 0.3% | 0.04 / 28 / 0.2% | 0.05 / 28 / 0.3% | 0.15 / 55 / 0.7% | 0.43 / 13 / 0.0% |

## At 1024 pixels

### Draw time

| Art | SDG, Java | SDG, C | SDG, C, all cores | SVG, imagelib | SVG, Java | PNG 256, scaled | PNG 1024, scaled |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| `badge` | 79.56 ± 2.34 ms | 16.16 ± 1.45 ms | 3.32 ± 0.37 ms | 16.71 ± 2.71 ms | 342.92 ± 405.07 ms | 21.36 ± 1.20 ms | 21.21 ± 0.37 ms |
| `rasterware` | 105.70 ± 1.87 ms | 19.48 ± 0.79 ms | 3.59 ± 1.95 ms | 20.21 ± 12.42 ms | 380.09 ± 425.62 ms | 21.74 ± 0.61 ms | 21.83 ± 6.41 ms |
| `vexelray-logo` | 70.13 ± 4.98 ms | 13.40 ± 2.34 ms | 3.43 ± 0.33 ms | 20.82 ± 16.28 ms | 257.72 ± 429.01 ms | 14.44 ± 0.62 ms | 14.07 ± 0.77 ms |
| `mainframe` | 102.38 ± 3.46 ms | 17.86 ± 1.40 ms | 3.58 ± 2.68 ms | 19.84 ± 6.61 ms | 437.90 ± 334.19 ms | 22.30 ± 1.44 ms | 22.20 ± 1.38 ms |
| `background-icon` | 128.95 ± 11.73 ms | 26.82 ± 0.98 ms | 3.99 ± 0.91 ms | 27.35 ± 11.87 ms | 516.05 ± 779.02 ms | 22.63 ± 0.79 ms | 22.08 ± 7.05 ms |
| `file-icon` | 36.21 ± 2.65 ms | 17.55 ± 4.80 ms | 3.46 ± 2.60 ms | 16.49 ± 1.19 ms | 227.08 ± 217.73 ms | 15.81 ± 0.65 ms | 15.70 ± 0.76 ms |

### GPU draw time

One drawing at the size, by Vulkan timestamps (see *Method*).

| Art | SDG, GPU | PNG 1024, GPU |
| --- | ---: | ---: |
| `badge` | 71.5 µs | 25.2 µs |
| `rasterware` | 155.0 µs | 25.2 µs |
| `vexelray-logo` | 95.9 µs | 25.1 µs |
| `mainframe` | 151.7 µs | 24.9 µs |
| `background-icon` | 219.9 µs | 25.2 µs |
| `file-icon` | 55.6 µs | 25.2 µs |

### Error against the truth

Mean / worst / share of pixels past 8.

| Art | SDG, Java | SDG, C | SDG, C, all cores | SVG, imagelib | SVG, Java | PNG 256, scaled | PNG 1024, scaled | SDG, GPU |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| `badge` | 0.12 / 69 / 0.5% | 0.12 / 69 / 0.5% | 0.12 / 69 / 0.5% | 0.06 / 64 / 0.4% | 0.05 / 30 / 0.4% | 1.30 / 149 / 5.3% | 0.06 / 64 / 0.4% | 0.21 / 69 / 0.2% |
| `rasterware` | 0.18 / 225 / 0.8% | 0.18 / 225 / 0.8% | 0.18 / 225 / 0.8% | 0.06 / 47 / 0.5% | 0.06 / 31 / 0.5% | 1.98 / 164 / 7.8% | 0.06 / 47 / 0.5% | 0.27 / 225 / 0.8% |
| `vexelray-logo` | 0.65 / 218 / 1.6% | 0.65 / 218 / 1.6% | 0.65 / 218 / 1.6% | 0.05 / 32 / 0.2% | 0.03 / 71 / 0.1% | 1.31 / 169 / 5.1% | 0.05 / 32 / 0.2% | 0.99 / 125 / 1.4% |
| `mainframe` | 0.15 / 217 / 0.8% | 0.15 / 217 / 0.8% | 0.15 / 217 / 0.8% | 0.06 / 47 / 0.6% | 0.06 / 28 / 0.5% | 1.68 / 141 / 7.8% | 0.06 / 47 / 0.6% | 0.25 / 217 / 0.7% |
| `background-icon` | 0.39 / 139 / 1.4% | 0.39 / 139 / 1.4% | 0.39 / 139 / 1.4% | 0.14 / 107 / 0.7% | 0.11 / 59 / 0.6% | 2.42 / 204 / 8.9% | 0.14 / 107 / 0.7% | 0.40 / 135 / 1.3% |
| `file-icon` | 0.03 / 67 / 0.1% | 0.03 / 67 / 0.1% | 0.03 / 67 / 0.1% | 0.01 / 39 / 0.1% | 0.01 / 26 / 0.1% | 1.08 / 183 / 2.6% | 0.01 / 39 / 0.1% | 0.41 / 38 / 0.1% |
