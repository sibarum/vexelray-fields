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
  * **SDG, C**: the drawing by sdg-signed-distance-graphics' C rasterizer (native, one thread, /O2): SdgRender's algorithm, bit for bit, called through the foreign-function API
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
| `badge` | 59.0 ± 11.8 µs | 774.8 ± 1323.4 µs | 884.2 ± 75.4 µs | 146.9 ± 35.2 µs | 3.85 ± 0.70 ms |
| `rasterware` | 65.2 ± 35.1 µs | 1.49 ± 0.31 ms | 1.70 ± 0.75 ms | 148.4 ± 319.3 µs | 5.92 ± 5.76 ms |
| `vexelray-logo` | 231.9 ± 921.0 µs | 2.36 ± 2.52 ms | 3.67 ± 4.44 ms | 132.7 ± 286.6 µs | 4.05 ± 1.08 ms |
| `mainframe` | 61.9 ± 22.8 µs | 1.12 ± 0.18 ms | 1.36 ± 0.29 ms | 133.9 ± 3.5 µs | 3.61 ± 0.23 ms |
| `background-icon` | 61.1 ± 9.0 µs | 1.61 ± 0.27 ms | 1.92 ± 0.81 ms | 132.5 ± 5.7 µs | 3.66 ± 0.12 ms |
| `file-icon` | 54.0 ± 19.6 µs | 277.0 ± 16.6 µs | 404.4 ± 229.1 µs | 73.2 ± 13.1 µs | 2.97 ± 0.52 ms |

## At 64 pixels

### Draw time

| Art | SDG, Java | SDG, C | SVG, imagelib | SVG, Java | PNG 256, scaled | PNG 1024, scaled |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| `badge` | 347.1 ± 81.3 µs | 333.9 ± 212.9 µs | 15.22 ± 25.62 ms | 1.52 ± 1.68 ms | 120.4 ± 693.5 µs | 292.9 ± 599.5 µs |
| `rasterware` | 906.0 ± 2649.3 µs | 466.8 ± 1123.9 µs | 12.14 ± 11.22 ms | 4.25 ± 16.72 ms | 231.6 ± 505.7 µs | 91.7 ± 36.7 µs |
| `vexelray-logo` | 305.9 ± 215.8 µs | 823.4 ± 3577.9 µs | 13.74 ± 33.40 ms | 901.5 ± 60.0 µs | 58.8 ± 5.8 µs | 56.7 ± 16.4 µs |
| `mainframe` | 421.9 ± 10.2 µs | 394.5 ± 44.4 µs | 11.73 ± 1.91 ms | 1.45 ± 0.36 ms | 88.7 ± 1.5 µs | 89.1 ± 1.7 µs |
| `background-icon` | 510.6 ± 12.6 µs | 473.0 ± 20.5 µs | 12.69 ± 2.56 ms | 1.72 ± 0.04 ms | 87.8 ± 12.3 µs | 89.0 ± 2.2 µs |
| `file-icon` | 148.2 ± 8.0 µs | 126.4 ± 8.6 µs | 11.51 ± 0.74 ms | 778.6 ± 203.5 µs | 64.2 ± 3.9 µs | 64.8 ± 1.0 µs |

### GPU draw time

One drawing at the size, by Vulkan timestamps (see *Method*).

| Art | SDG, GPU | PNG 1024, GPU |
| --- | ---: | ---: |
| `badge` | 0.9 µs | 0.0 µs |
| `rasterware` | 1.6 µs | 0.0 µs |
| `vexelray-logo` | 1.2 µs | 0.0 µs |
| `mainframe` | 1.4 µs | 0.0 µs |
| `background-icon` | 6.9 µs | 0.0 µs |
| `file-icon` | 6.5 µs | 0.1 µs |

### Error against the truth

Mean / worst / share of pixels past 8.

| Art | SDG, Java | SDG, C | SVG, imagelib | SVG, Java | PNG 256, scaled | PNG 1024, scaled | SDG, GPU |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| `badge` | 0.91 / 55 / 2.4% | 0.91 / 55 / 2.4% | 1.31 / 68 / 6.1% | 0.61 / 21 / 4.3% | 1.22 / 51 / 10.4% | 4.65 / 124 / 12.4% | 0.62 / 54 / 1.9% |
| `rasterware` | 0.70 / 32 / 5.2% | 0.70 / 32 / 5.2% | 1.10 / 73 / 8.9% | 0.84 / 28 / 7.1% | 2.55 / 65 / 12.1% | 3.93 / 108 / 13.6% | 0.70 / 33 / 3.5% |
| `vexelray-logo` | 1.19 / 100 / 4.8% | 1.19 / 100 / 4.8% | 1.01 / 51 / 4.5% | 0.55 / 24 / 2.3% | 1.84 / 53 / 8.8% | 3.04 / 107 / 9.3% | 0.97 / 40 / 2.4% |
| `mainframe` | 0.56 / 55 / 4.0% | 0.56 / 55 / 4.0% | 1.06 / 73 / 10.3% | 0.74 / 27 / 7.4% | 2.19 / 53 / 12.5% | 2.78 / 108 / 13.8% | 0.56 / 54 / 2.1% |
| `background-icon` | 0.90 / 41 / 7.1% | 0.90 / 41 / 7.1% | 1.81 / 87 / 10.2% | 1.64 / 34 / 8.3% | 3.17 / 62 / 16.8% | 6.63 / 124 / 18.0% | 0.81 / 20 / 5.2% |
| `file-icon` | 0.28 / 14 / 0.5% | 0.28 / 14 / 0.5% | 0.17 / 19 / 0.9% | 0.19 / 18 / 1.2% | 0.62 / 55 / 2.9% | 1.08 / 102 / 2.9% | 0.52 / 9 / 0.0% |

## At 256 pixels

### Draw time

| Art | SDG, Java | SDG, C | SVG, imagelib | SVG, Java | PNG 256, scaled | PNG 1024, scaled |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| `badge` | 5.17 ± 0.30 ms | 5.47 ± 9.76 ms | 12.50 ± 1.66 ms | 23.17 ± 15.83 ms | 3.26 ± 24.49 ms | 4.17 ± 8.29 ms |
| `rasterware` | 15.95 ± 18.51 ms | 6.54 ± 0.69 ms | 18.25 ± 19.80 ms | 43.30 ± 273.02 ms | 1.57 ± 4.02 ms | 1.50 ± 2.06 ms |
| `vexelray-logo` | 6.14 ± 41.56 ms | 9.85 ± 16.04 ms | 13.29 ± 15.20 ms | 14.16 ± 12.17 ms | 883.1 ± 177.1 µs | 896.1 ± 69.0 µs |
| `mainframe` | 6.48 ± 0.27 ms | 6.16 ± 0.14 ms | 12.47 ± 0.96 ms | 22.39 ± 1.34 ms | 1.38 ± 0.03 ms | 1.38 ± 0.05 ms |
| `background-icon` | 7.84 ± 1.14 ms | 7.64 ± 0.41 ms | 13.67 ± 6.57 ms | 26.59 ± 2.10 ms | 1.37 ± 0.21 ms | 1.38 ± 0.09 ms |
| `file-icon` | 2.21 ± 0.06 ms | 2.30 ± 0.34 ms | 11.66 ± 0.27 ms | 13.64 ± 1.96 ms | 1.00 ± 0.03 ms | 999.1 ± 170.3 µs |

### GPU draw time

One drawing at the size, by Vulkan timestamps (see *Method*).

| Art | SDG, GPU | PNG 1024, GPU |
| --- | ---: | ---: |
| `badge` | 8.5 µs | 2.9 µs |
| `rasterware` | 15.5 µs | 2.7 µs |
| `vexelray-logo` | 9.4 µs | 2.8 µs |
| `mainframe` | 14.6 µs | 2.8 µs |
| `background-icon` | 25.4 µs | 2.8 µs |
| `file-icon` | 10.9 µs | 2.6 µs |

### Error against the truth

Mean / worst / share of pixels past 8.

| Art | SDG, Java | SDG, C | SVG, imagelib | SVG, Java | PNG 256, scaled | PNG 1024, scaled | SDG, GPU |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| `badge` | 0.29 / 59 / 1.3% | 0.29 / 59 / 1.3% | 0.23 / 62 / 0.9% | 0.06 / 28 / 0.3% | 0.23 / 62 / 0.9% | 0.63 / 72 / 2.9% | 0.27 / 59 / 0.5% |
| `rasterware` | 0.29 / 161 / 2.0% | 0.29 / 161 / 2.0% | 0.26 / 51 / 2.3% | 0.22 / 23 / 1.8% | 0.26 / 51 / 2.3% | 0.66 / 77 / 3.3% | 0.38 / 161 / 1.8% |
| `vexelray-logo` | 0.62 / 148 / 2.6% | 0.62 / 148 / 2.6% | 0.22 / 62 / 1.2% | 0.13 / 24 / 0.4% | 0.22 / 62 / 1.2% | 0.48 / 65 / 2.4% | 0.93 / 76 / 2.3% |
| `mainframe` | 0.24 / 98 / 1.6% | 0.24 / 98 / 1.6% | 0.24 / 51 / 2.4% | 0.19 / 28 / 1.8% | 0.24 / 51 / 2.4% | 0.55 / 64 / 3.2% | 0.34 / 98 / 1.6% |
| `background-icon` | 0.49 / 96 / 2.1% | 0.49 / 96 / 2.1% | 0.57 / 100 / 2.6% | 0.42 / 39 / 2.0% | 0.57 / 100 / 2.6% | 0.81 / 69 / 4.2% | 0.47 / 96 / 1.9% |
| `file-icon` | 0.11 / 19 / 0.2% | 0.11 / 19 / 0.2% | 0.05 / 28 / 0.3% | 0.04 / 28 / 0.2% | 0.05 / 28 / 0.3% | 0.15 / 55 / 0.7% | 0.43 / 13 / 0.0% |

## At 1024 pixels

### Draw time

| Art | SDG, Java | SDG, C | SVG, imagelib | SVG, Java | PNG 256, scaled | PNG 1024, scaled |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| `badge` | 83.22 ± 16.74 ms | 84.27 ± 19.19 ms | 18.00 ± 9.62 ms | 418.80 ± 612.60 ms | 55.11 ± 420.88 ms | 22.39 ± 6.99 ms |
| `rasterware` | 247.74 ± 406.21 ms | 104.26 ± 11.19 ms | 26.29 ± 77.83 ms | 1155.79 ± 4144.25 ms | 24.06 ± 10.62 ms | 41.36 ± 143.12 ms |
| `vexelray-logo` | 104.08 ± 310.22 ms | 87.91 ± 439.84 ms | 16.88 ± 0.02 ms | 256.76 ± 126.97 ms | 14.35 ± 1.65 ms | 14.06 ± 2.19 ms |
| `mainframe` | 102.97 ± 1.10 ms | 98.54 ± 14.33 ms | 19.66 ± 16.05 ms | 426.77 ± 37.36 ms | 22.15 ± 0.25 ms | 22.06 ± 3.25 ms |
| `background-icon` | 124.70 ± 32.54 ms | 121.90 ± 4.07 ms | 28.53 ± 0.80 ms | 502.43 ± 300.83 ms | 22.16 ± 2.59 ms | 21.96 ± 2.06 ms |
| `file-icon` | 35.28 ± 0.46 ms | 38.87 ± 3.58 ms | 17.40 ± 3.54 ms | 230.79 ± 89.03 ms | 16.28 ± 2.32 ms | 16.00 ± 0.94 ms |

### GPU draw time

One drawing at the size, by Vulkan timestamps (see *Method*).

| Art | SDG, GPU | PNG 1024, GPU |
| --- | ---: | ---: |
| `badge` | 71.5 µs | 25.3 µs |
| `rasterware` | 155.0 µs | 25.2 µs |
| `vexelray-logo` | 95.6 µs | 25.0 µs |
| `mainframe` | 152.0 µs | 25.5 µs |
| `background-icon` | 219.9 µs | 25.4 µs |
| `file-icon` | 55.7 µs | 25.0 µs |

### Error against the truth

Mean / worst / share of pixels past 8.

| Art | SDG, Java | SDG, C | SVG, imagelib | SVG, Java | PNG 256, scaled | PNG 1024, scaled | SDG, GPU |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| `badge` | 0.12 / 69 / 0.5% | 0.12 / 69 / 0.5% | 0.06 / 64 / 0.4% | 0.05 / 30 / 0.4% | 1.30 / 149 / 5.3% | 0.06 / 64 / 0.4% | 0.21 / 69 / 0.2% |
| `rasterware` | 0.18 / 225 / 0.8% | 0.18 / 225 / 0.8% | 0.06 / 47 / 0.5% | 0.06 / 31 / 0.5% | 1.98 / 164 / 7.8% | 0.06 / 47 / 0.5% | 0.27 / 225 / 0.8% |
| `vexelray-logo` | 0.65 / 218 / 1.6% | 0.65 / 218 / 1.6% | 0.05 / 32 / 0.2% | 0.03 / 71 / 0.1% | 1.31 / 169 / 5.1% | 0.05 / 32 / 0.2% | 0.99 / 125 / 1.4% |
| `mainframe` | 0.15 / 217 / 0.8% | 0.15 / 217 / 0.8% | 0.06 / 47 / 0.6% | 0.06 / 28 / 0.5% | 1.68 / 141 / 7.8% | 0.06 / 47 / 0.6% | 0.25 / 217 / 0.7% |
| `background-icon` | 0.39 / 139 / 1.4% | 0.39 / 139 / 1.4% | 0.14 / 107 / 0.7% | 0.11 / 59 / 0.6% | 2.42 / 204 / 8.9% | 0.14 / 107 / 0.7% | 0.40 / 135 / 1.3% |
| `file-icon` | 0.03 / 67 / 0.1% | 0.03 / 67 / 0.1% | 0.01 / 39 / 0.1% | 0.01 / 26 / 0.1% | 1.08 / 183 / 2.6% | 0.01 / 39 / 0.1% | 0.41 / 38 / 0.1% |
