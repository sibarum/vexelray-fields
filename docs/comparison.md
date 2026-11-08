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

  * **SDG, CPU**: the drawing by SdgRender, the reference a GPU drawing is checked against
  * **SVG, imagelib**: the SVG rasterised by imagelib (native), parse included, as Ico.java made icons
  * **SVG, Java**: the SVG, parsed once, rasterised by vexelray-fields' renderer at 4 × 4 samples a pixel
  * **PNG 256, scaled**: a 256 px PNG, decoded once, scaled to the size by bilinear filtering
  * **PNG 1024, scaled**: a 1024 px PNG, decoded once, scaled to the size by bilinear filtering
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
| `badge` | 52.5 ± 18.8 µs | 651.1 ± 115.2 µs | 823.9 ± 222.7 µs | 140.9 ± 24.0 µs | 3.77 ± 0.74 ms |
| `rasterware` | 56.1 ± 5.6 µs | 1.36 ± 0.05 ms | 1.62 ± 1.45 ms | 123.3 ± 27.1 µs | 3.44 ± 0.10 ms |
| `vexelray-logo` | 58.6 ± 5.5 µs | 850.3 ± 52.1 µs | 1.11 ± 0.29 ms | 107.6 ± 32.0 µs | 3.46 ± 0.22 ms |
| `mainframe` | 53.5 ± 3.5 µs | 1.09 ± 0.08 ms | 1.31 ± 0.31 ms | 128.8 ± 63.8 µs | 3.47 ± 0.15 ms |
| `background-icon` | 59.9 ± 11.7 µs | 1.63 ± 0.14 ms | 1.88 ± 0.30 ms | 126.7 ± 49.7 µs | 3.66 ± 1.91 ms |
| `file-icon` | 48.8 ± 1.5 µs | 272.5 ± 25.6 µs | 403.2 ± 237.1 µs | 70.3 ± 23.2 µs | 2.94 ± 0.10 ms |

## At 64 pixels

### Draw time

| Art | SDG, CPU | SVG, imagelib | SVG, Java | PNG 256, scaled | PNG 1024, scaled |
| --- | ---: | ---: | ---: | ---: | ---: |
| `badge` | 328.8 ± 60.1 µs | 11.07 ± 4.35 ms | 1.36 ± 0.06 ms | 94.7 ± 269.7 µs | 84.5 ± 4.0 µs |
| `rasterware` | 437.5 ± 22.4 µs | 11.67 ± 0.79 ms | 1.44 ± 0.07 ms | 86.8 ± 1.4 µs | 87.2 ± 2.3 µs |
| `vexelray-logo` | 289.2 ± 11.3 µs | 11.82 ± 4.51 ms | 907.9 ± 48.0 µs | 56.7 ± 2.0 µs | 55.7 ± 1.1 µs |
| `mainframe` | 414.2 ± 30.1 µs | 11.90 ± 0.72 ms | 1.41 ± 0.10 ms | 89.4 ± 3.7 µs | 89.2 ± 11.7 µs |
| `background-icon` | 500.8 ± 20.2 µs | 12.49 ± 8.54 ms | 1.73 ± 0.16 ms | 86.8 ± 0.4 µs | 87.2 ± 2.7 µs |
| `file-icon` | 144.2 ± 5.8 µs | 11.72 ± 1.12 ms | 744.8 ± 256.8 µs | 63.1 ± 3.0 µs | 63.7 ± 1.6 µs |

### GPU draw time

One drawing at the size, by Vulkan timestamps (see *Method*).

| Art | SDG, GPU | PNG 1024, GPU |
| --- | ---: | ---: |
| `badge` | 0.9 µs | 0.1 µs |
| `rasterware` | 1.6 µs | 0.1 µs |
| `vexelray-logo` | 1.3 µs | 0.0 µs |
| `mainframe` | 1.5 µs | 0.0 µs |
| `background-icon` | 6.9 µs | 0.0 µs |
| `file-icon` | 6.5 µs | 0.1 µs |

### Error against the truth

Mean / worst / share of pixels past 8.

| Art | SDG, CPU | SVG, imagelib | SVG, Java | PNG 256, scaled | PNG 1024, scaled | SDG, GPU |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| `badge` | 0.91 / 55 / 2.4% | 1.31 / 68 / 6.1% | 0.61 / 21 / 4.3% | 1.22 / 51 / 10.4% | 4.65 / 124 / 12.4% | 0.62 / 54 / 1.9% |
| `rasterware` | 0.70 / 32 / 5.2% | 1.10 / 73 / 8.9% | 0.84 / 28 / 7.1% | 2.55 / 65 / 12.1% | 3.93 / 108 / 13.6% | 0.70 / 33 / 3.5% |
| `vexelray-logo` | 1.19 / 100 / 4.8% | 1.01 / 51 / 4.5% | 0.55 / 24 / 2.3% | 1.84 / 53 / 8.8% | 3.04 / 107 / 9.3% | 0.97 / 40 / 2.4% |
| `mainframe` | 0.56 / 55 / 4.0% | 1.06 / 73 / 10.3% | 0.74 / 27 / 7.4% | 2.19 / 53 / 12.5% | 2.78 / 108 / 13.8% | 0.56 / 54 / 2.1% |
| `background-icon` | 0.90 / 41 / 7.1% | 1.81 / 87 / 10.2% | 1.64 / 34 / 8.3% | 3.17 / 62 / 16.8% | 6.63 / 124 / 18.0% | 0.81 / 20 / 5.2% |
| `file-icon` | 0.28 / 14 / 0.5% | 0.17 / 19 / 0.9% | 0.19 / 18 / 1.2% | 0.62 / 55 / 2.9% | 1.08 / 102 / 2.9% | 0.52 / 9 / 0.0% |

## At 256 pixels

### Draw time

| Art | SDG, CPU | SVG, imagelib | SVG, Java | PNG 256, scaled | PNG 1024, scaled |
| --- | ---: | ---: | ---: | ---: | ---: |
| `badge` | 5.10 ± 0.55 ms | 12.34 ± 9.31 ms | 20.80 ± 1.97 ms | 1.37 ± 0.05 ms | 1.36 ± 0.03 ms |
| `rasterware` | 6.68 ± 0.17 ms | 12.35 ± 0.37 ms | 21.64 ± 1.29 ms | 1.35 ± 0.05 ms | 1.36 ± 0.08 ms |
| `vexelray-logo` | 4.31 ± 0.25 ms | 11.94 ± 0.72 ms | 13.22 ± 0.92 ms | 867.3 ± 34.0 µs | 870.4 ± 17.3 µs |
| `mainframe` | 6.41 ± 0.29 ms | 12.31 ± 1.19 ms | 21.84 ± 2.11 ms | 1.40 ± 0.04 ms | 1.39 ± 0.16 ms |
| `background-icon` | 7.76 ± 0.64 ms | 13.20 ± 1.84 ms | 26.64 ± 2.62 ms | 1.35 ± 0.04 ms | 1.36 ± 0.04 ms |
| `file-icon` | 2.20 ± 0.04 ms | 11.94 ± 0.99 ms | 13.06 ± 1.10 ms | 985.1 ± 4.0 µs | 987.0 ± 18.6 µs |

### GPU draw time

One drawing at the size, by Vulkan timestamps (see *Method*).

| Art | SDG, GPU | PNG 1024, GPU |
| --- | ---: | ---: |
| `badge` | 8.2 µs | 2.9 µs |
| `rasterware` | 15.8 µs | 2.9 µs |
| `vexelray-logo` | 9.6 µs | 2.8 µs |
| `mainframe` | 14.4 µs | 2.8 µs |
| `background-icon` | 25.5 µs | 2.9 µs |
| `file-icon` | 10.8 µs | 2.9 µs |

### Error against the truth

Mean / worst / share of pixels past 8.

| Art | SDG, CPU | SVG, imagelib | SVG, Java | PNG 256, scaled | PNG 1024, scaled | SDG, GPU |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| `badge` | 0.29 / 59 / 1.3% | 0.23 / 62 / 0.9% | 0.06 / 28 / 0.3% | 0.23 / 62 / 0.9% | 0.63 / 72 / 2.9% | 0.27 / 59 / 0.5% |
| `rasterware` | 0.29 / 161 / 2.0% | 0.26 / 51 / 2.3% | 0.22 / 23 / 1.8% | 0.26 / 51 / 2.3% | 0.66 / 77 / 3.3% | 0.38 / 161 / 1.8% |
| `vexelray-logo` | 0.62 / 148 / 2.6% | 0.22 / 62 / 1.2% | 0.13 / 24 / 0.4% | 0.22 / 62 / 1.2% | 0.48 / 65 / 2.4% | 0.93 / 76 / 2.3% |
| `mainframe` | 0.24 / 98 / 1.6% | 0.24 / 51 / 2.4% | 0.19 / 28 / 1.8% | 0.24 / 51 / 2.4% | 0.55 / 64 / 3.2% | 0.34 / 98 / 1.6% |
| `background-icon` | 0.49 / 96 / 2.1% | 0.57 / 100 / 2.6% | 0.42 / 39 / 2.0% | 0.57 / 100 / 2.6% | 0.81 / 69 / 4.2% | 0.47 / 96 / 1.9% |
| `file-icon` | 0.11 / 19 / 0.2% | 0.05 / 28 / 0.3% | 0.04 / 28 / 0.2% | 0.05 / 28 / 0.3% | 0.15 / 55 / 0.7% | 0.43 / 13 / 0.0% |

## At 1024 pixels

### Draw time

| Art | SDG, CPU | SVG, imagelib | SVG, Java | PNG 256, scaled | PNG 1024, scaled |
| --- | ---: | ---: | ---: | ---: | ---: |
| `badge` | 79.89 ± 3.04 ms | 17.97 ± 3.69 ms | 327.68 ± 387.57 ms | 21.69 ± 4.29 ms | 21.56 ± 1.28 ms |
| `rasterware` | 106.15 ± 6.11 ms | 18.58 ± 1.10 ms | 364.70 ± 272.94 ms | 21.78 ± 1.03 ms | 21.57 ± 0.95 ms |
| `vexelray-logo` | 68.13 ± 5.54 ms | 16.53 ± 0.72 ms | 217.08 ± 92.57 ms | 14.00 ± 0.57 ms | 13.69 ± 0.61 ms |
| `mainframe` | 100.62 ± 2.95 ms | 18.69 ± 0.72 ms | 432.80 ± 438.67 ms | 22.42 ± 0.44 ms | 22.20 ± 0.19 ms |
| `background-icon` | 122.81 ± 16.22 ms | 27.79 ± 3.27 ms | 482.75 ± 1361.66 ms | 21.87 ± 0.99 ms | 21.58 ± 0.46 ms |
| `file-icon` | 34.97 ± 0.53 ms | 15.60 ± 1.89 ms | 219.83 ± 91.00 ms | 15.80 ± 0.63 ms | 15.67 ± 0.28 ms |

### GPU draw time

One drawing at the size, by Vulkan timestamps (see *Method*).

| Art | SDG, GPU | PNG 1024, GPU |
| --- | ---: | ---: |
| `badge` | 71.3 µs | 25.1 µs |
| `rasterware` | 154.6 µs | 27.7 µs |
| `vexelray-logo` | 95.7 µs | 24.8 µs |
| `mainframe` | 150.9 µs | 24.9 µs |
| `background-icon` | 218.9 µs | 25.2 µs |
| `file-icon` | 55.8 µs | 25.1 µs |

### Error against the truth

Mean / worst / share of pixels past 8.

| Art | SDG, CPU | SVG, imagelib | SVG, Java | PNG 256, scaled | PNG 1024, scaled | SDG, GPU |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| `badge` | 0.12 / 69 / 0.5% | 0.06 / 64 / 0.4% | 0.05 / 30 / 0.4% | 1.30 / 149 / 5.3% | 0.06 / 64 / 0.4% | 0.21 / 69 / 0.2% |
| `rasterware` | 0.18 / 225 / 0.8% | 0.06 / 47 / 0.5% | 0.06 / 31 / 0.5% | 1.98 / 164 / 7.8% | 0.06 / 47 / 0.5% | 0.27 / 225 / 0.8% |
| `vexelray-logo` | 0.65 / 218 / 1.6% | 0.05 / 32 / 0.2% | 0.03 / 71 / 0.1% | 1.31 / 169 / 5.1% | 0.05 / 32 / 0.2% | 0.99 / 125 / 1.4% |
| `mainframe` | 0.15 / 217 / 0.8% | 0.06 / 47 / 0.6% | 0.06 / 28 / 0.5% | 1.68 / 141 / 7.8% | 0.06 / 47 / 0.6% | 0.25 / 217 / 0.7% |
| `background-icon` | 0.39 / 139 / 1.4% | 0.14 / 107 / 0.7% | 0.11 / 59 / 0.6% | 2.42 / 204 / 8.9% | 0.14 / 107 / 0.7% | 0.40 / 135 / 1.3% |
| `file-icon` | 0.03 / 67 / 0.1% | 0.01 / 39 / 0.1% | 0.01 / 26 / 0.1% | 1.08 / 183 / 2.6% | 0.01 / 39 / 0.1% | 0.41 / 38 / 0.1% |
