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
  * **SDG, GPU**: the SDG drawn by a node in a headless window (`GuiApp.capture`), the stack's own
    renderer; measured for error only so far, its time waiting on GPU timestamp queries.

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
| `badge` | 54.4 ± 23.9 µs | 712.0 ± 1230.6 µs | 873.4 ± 386.0 µs | 145.1 ± 12.1 µs | 3.86 ± 0.67 ms |
| `rasterware` | 57.3 ± 19.9 µs | 1.34 ± 0.04 ms | 1.67 ± 0.46 ms | 131.6 ± 12.3 µs | 3.56 ± 0.79 ms |
| `vexelray-logo` | 58.7 ± 9.6 µs | 889.7 ± 125.4 µs | 1.13 ± 0.35 ms | 107.6 ± 55.7 µs | 3.49 ± 0.55 ms |
| `mainframe` | 54.2 ± 4.4 µs | 1.09 ± 0.06 ms | 1.32 ± 0.31 ms | 126.6 ± 32.2 µs | 3.52 ± 0.12 ms |
| `background-icon` | 56.0 ± 1.0 µs | 1.58 ± 0.15 ms | 1.88 ± 0.08 ms | 127.2 ± 11.0 µs | 3.58 ± 0.25 ms |
| `file-icon` | 48.8 ± 1.2 µs | 272.4 ± 22.1 µs | 402.7 ± 247.3 µs | 71.1 ± 18.1 µs | 2.98 ± 0.64 ms |

## At 64 pixels

### Draw time

| Art | SDG, CPU | SVG, imagelib | SVG, Java | PNG 256, scaled | PNG 1024, scaled |
| --- | ---: | ---: | ---: | ---: | ---: |
| `badge` | 332.6 ± 2.5 µs | 12.63 ± 0.64 ms | 1.40 ± 0.05 ms | 87.0 ± 24.1 µs | 84.6 ± 8.3 µs |
| `rasterware` | 440.1 ± 14.6 µs | 13.86 ± 12.53 ms | 1.49 ± 0.36 ms | 89.4 ± 6.0 µs | 89.2 ± 5.8 µs |
| `vexelray-logo` | 300.1 ± 9.3 µs | 13.16 ± 2.05 ms | 944.0 ± 99.3 µs | 58.8 ± 0.9 µs | 57.7 ± 21.5 µs |
| `mainframe` | 417.5 ± 6.0 µs | 12.08 ± 1.53 ms | 1.41 ± 0.24 ms | 87.4 ± 4.1 µs | 88.0 ± 1.5 µs |
| `background-icon` | 504.1 ± 41.4 µs | 11.64 ± 2.16 ms | 1.72 ± 0.05 ms | 87.6 ± 0.9 µs | 88.4 ± 1.8 µs |
| `file-icon` | 141.4 ± 5.5 µs | 11.78 ± 0.67 ms | 778.7 ± 229.1 µs | 64.4 ± 0.8 µs | 65.2 ± 1.0 µs |

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
| `badge` | 5.22 ± 1.27 ms | 12.51 ± 1.67 ms | 21.16 ± 0.53 ms | 1.35 ± 0.17 ms | 1.38 ± 0.14 ms |
| `rasterware` | 6.91 ± 2.93 ms | 13.51 ± 1.57 ms | 25.82 ± 5.13 ms | 1.39 ± 0.41 ms | 1.39 ± 1.10 ms |
| `vexelray-logo` | 4.46 ± 0.27 ms | 11.31 ± 1.59 ms | 14.20 ± 3.42 ms | 896.6 ± 116.9 µs | 913.2 ± 287.1 µs |
| `mainframe` | 6.42 ± 0.06 ms | 12.47 ± 0.53 ms | 21.92 ± 1.92 ms | 1.37 ± 0.13 ms | 1.36 ± 0.04 ms |
| `background-icon` | 7.75 ± 0.30 ms | 13.24 ± 0.96 ms | 27.02 ± 0.68 ms | 1.37 ± 0.03 ms | 1.37 ± 0.05 ms |
| `file-icon` | 2.21 ± 0.10 ms | 11.86 ± 1.68 ms | 13.75 ± 1.40 ms | 1.01 ± 0.02 ms | 1.01 ± 0.04 ms |

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
| `badge` | 82.56 ± 1.28 ms | 18.23 ± 0.97 ms | 341.48 ± 448.67 ms | 21.99 ± 5.76 ms | 21.61 ± 1.61 ms |
| `rasterware` | 109.22 ± 3.53 ms | 21.06 ± 27.42 ms | 391.61 ± 534.33 ms | 22.37 ± 2.40 ms | 21.85 ± 5.83 ms |
| `vexelray-logo` | 70.89 ± 5.63 ms | 17.24 ± 17.47 ms | 249.92 ± 464.88 ms | 14.51 ± 1.15 ms | 13.89 ± 2.58 ms |
| `mainframe` | 101.98 ± 2.29 ms | 18.04 ± 0.78 ms | 427.65 ± 709.55 ms | 21.90 ± 0.96 ms | 21.76 ± 0.65 ms |
| `background-icon` | 124.09 ± 18.45 ms | 27.97 ± 2.22 ms | 516.81 ± 717.98 ms | 22.02 ± 1.01 ms | 21.88 ± 0.89 ms |
| `file-icon` | 35.33 ± 1.68 ms | 17.17 ± 2.45 ms | 229.89 ± 316.58 ms | 16.18 ± 0.63 ms | 16.03 ± 0.81 ms |

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
