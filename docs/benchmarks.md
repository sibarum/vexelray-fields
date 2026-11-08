# Benchmarks

**Written by `vexelray-fields-bench`'s `Publish`, not by hand.** Every conversion in
[`conversions.md`](conversions.md), timed and its allocation measured by JMH on fixed inputs (the
module's `Inputs`). Run it again with:

```
mvn -pl vexelray-fields-bench -am install -DskipTests
mvn -pl vexelray-fields-bench exec:exec
```

## The run

| | |
| --- | --- |
| Date | 2026-11-08 |
| CPU | Intel(R) Core(TM) Ultra 7 255HX |
| Cores the JVM sees | 20 |
| Memory | 15.4 GiB |
| OS | Windows 11 10.0 amd64 |
| JDK | Java HotSpot(TM) 64-Bit Server VM 25.0.3+9-LTS-jvmci-b01 |
| JVM flags | `-XX:ThreadPriorityPolicy=1 -XX:+UnlockExperimentalVMOptions -XX:+EnableJVMCIProduct -XX:+EnableJVMCI -XX:-UnlockExperimentalVMOptions` |
| JMH | average time per operation, 1 fork, 3 warm-up and 5 measured iterations of 1 s each, one thread; allocation from the `gc` profiler |

Time is the mean per conversion and its 99.9% confidence half-width. Allocation is bytes per
conversion, normalised by the `gc` profiler. A route's hops are each timed on exactly what the hop
before it made inside the route, so where its cost lies is plain; a hop's time and the route's
whole can differ by what the JIT does with the whole.

## `Convert.shapeToSdf`

Shape → SDF: EXACT

| Input | Time | Allocated |
| --- | ---: | ---: |
| star, 32 texels | 93.0 ± 28.0 µs | 14.6 KiB |
| star, 64 texels | 367.0 ± 109.3 µs | 35.3 KiB |

## `Convert.shapeToMsdf`

Shape → MSDF: EXACT

| Input | Time | Allocated |
| --- | ---: | ---: |
| star, 32 texels | 113.1 ± 106.5 µs | 816.9 KiB |
| star, 64 texels | 294.3 ± 65.4 µs | 2.8 MiB |

## `Convert.shapeToMtsdf`

Shape → MTSDF: EXACT

| Input | Time | Allocated |
| --- | ---: | ---: |
| star, 32 texels | 92.7 ± 9.2 µs | 882.6 KiB |
| star, 64 texels | 293.9 ± 41.5 µs | 2.8 MiB |

## `Convert.svgToSdg`

SVG → SDG: EXACT

| Input | Time | Allocated |
| --- | ---: | ---: |
| badge, 32 texels across | 2.08 ± 0.19 ms | 2.4 MiB |
| badge, 64 texels across | 5.80 ± 0.35 ms | 7.1 MiB |

## `Convert.maskToSdf`

Mask → SDF: EXACT

| Input | Time | Allocated |
| --- | ---: | ---: |
| star, 32 texels × 8 | 3.57 ± 0.10 ms | 1.3 MiB |
| star, 64 texels × 8 | 12.69 ± 0.07 ms | 5.1 MiB |

## `Convert.maskToShape`

Mask → Shape: EXACT

| Input | Time | Allocated |
| --- | ---: | ---: |
| star, 32 texels × 8 | 154.9 ± 5.0 µs | 741.7 KiB |
| star, 64 texels × 8 | 616.9 ± 31.6 µs | 2.7 MiB |

## `Convert.maskToCells`

Mask → Shape: EXACT

| Input | Time | Allocated |
| --- | ---: | ---: |
| star, 32 texels × 8 | 153.5 ± 3.5 µs | 752.9 KiB |
| star, 64 texels × 8 | 622.4 ± 107.2 µs | 2.7 MiB |

## `Convert.widen`

SDF → MTSDF: LOSSY (corners: round at magnification)

| Input | Time | Allocated |
| --- | ---: | ---: |
| star SDF, 32 texels | 2.2 ± 0.1 µs | 16.0 KiB |
| star SDF, 64 texels | 9.6 ± 0.7 µs | 64.0 KiB |

## `Convert.sdfToCoverage`

SDF → Coverage: EXACT

| Input | Time | Allocated |
| --- | ---: | ---: |
| star, 32 texels to 256 px | 420.4 ± 25.1 µs | 1.8 MiB |
| star, 64 texels to 512 px | 1.66 ± 0.04 ms | 7.0 MiB |

## `Convert.msdfToCoverage`

MSDF → Coverage: EXACT

| Input | Time | Allocated |
| --- | ---: | ---: |
| star, 32 texels to 256 px | 989.1 ± 31.1 µs | 2.3 MiB |
| star, 64 texels to 512 px | 3.93 ± 0.23 ms | 9.0 MiB |

## `Convert.mtsdfToCoverage`

MTSDF → Coverage: EXACT

| Input | Time | Allocated |
| --- | ---: | ---: |
| star, 32 texels to 256 px | 994.0 ± 125.9 µs | 2.3 MiB |
| star, 64 texels to 512 px | 3.92 ± 0.04 ms | 9.0 MiB |

## `Convert.coverageToMask`

Coverage → Mask: EXACT

| Input | Time | Allocated |
| --- | ---: | ---: |
| star, 256 px | 9.2 ± 0.6 µs | 64.1 KiB |
| star, 512 px | 39.5 ± 4.2 µs | 256.1 KiB |

## `Convert.fieldToSdg`

SDF → SDG: SYNTHESISING (a paint)

| Input | Time | Allocated |
| --- | ---: | ---: |
| star SDF, 32 texels, a solid paint | 10.1 ± 0.2 µs | 4.2 KiB |
| star SDF, 32 texels, a texture | 10.0 ± 0.1 µs | 4.3 KiB |
| star SDF, 64 texels, a solid paint | 40.9 ± 0.5 µs | 16.2 KiB |
| star SDF, 64 texels, a texture | 41.5 ± 3.6 µs | 16.3 KiB |

## `Convert.sdgToMask`

SDG → Mask: LOSSY (paints)

| Input | Time | Allocated |
| --- | ---: | ---: |
| badge, 32 texels × 8 | 4.02 ± 0.12 ms | 1.3 MiB |
| badge, 64 texels × 8 | 16.80 ± 2.79 ms | 5.3 MiB |

## `Convert.sdfToProbability`

SDF → Probability: SYNTHESISING (a footprint's σ)

| Input | Time | Allocated |
| --- | ---: | ---: |
| star, 32 texels, σ 1.5 | 11.5 ± 4.5 µs | 8.1 KiB |
| star, 32 texels, σ per texel | 13.4 ± 1.0 µs | 8.1 KiB |
| star, 64 texels, σ 1.5 | 62.3 ± 11.2 µs | 32.1 KiB |
| star, 64 texels, σ per texel | 59.1 ± 9.5 µs | 32.1 KiB |

## `Convert.probabilityToSdf`

Probability → SDF: LOSSY (distances beyond the band: flat)

| Input | Time | Allocated |
| --- | ---: | ---: |
| star, 32 texels | 30.4 ± 0.5 µs | 4.0 KiB |
| star, 64 texels | 141.5 ± 32.6 µs | 16.0 KiB |

## `Convert.sdgLayerSdf`

SDG → SDF: EXACT

| Input | Time | Allocated |
| --- | ---: | ---: |
| star SDG, 32 texels | 1.0 ± 0.1 µs | 4.0 KiB |
| star SDG, 64 texels | 4.6 ± 1.0 µs | 16.0 KiB |

## `Convert.sdgLayerMsdf`

SDG → MSDF: EXACT

| Input | Time | Allocated |
| --- | ---: | ---: |
| star SDG, 32 texels | 3.2 ± 0.0 µs | 12.0 KiB |
| star SDG, 64 texels | 12.8 ± 0.6 µs | 48.0 KiB |

## `Convert.rasterToMask`

Raster → Mask: SYNTHESISING (a channel; a threshold)

| Input | Time | Allocated |
| --- | ---: | ---: |
| badge picture, 256 px | 43.8 ± 2.7 µs | 64.1 KiB |
| badge picture, 512 px | 185.6 ± 46.0 µs | 256.1 KiB |

## `Convert.svgToMtsdf`

SVG → Shape → MTSDF: LOSSY (paints)

| Input | Time | Allocated |
| --- | ---: | ---: |
| star, 32 texels | 120.0 ± 29.5 µs | 927.5 KiB |
| &nbsp;&nbsp;hop 1: SVG → Shape | 13.2 ± 4.1 µs | 91.2 KiB |
| &nbsp;&nbsp;hop 2: Shape → MTSDF | 97.0 ± 17.7 µs | 865.4 KiB |
| star, 64 texels | 399.2 ± 132.6 µs | 2.8 MiB |
| &nbsp;&nbsp;hop 1: SVG → Shape | 15.9 ± 1.5 µs | 90.9 KiB |
| &nbsp;&nbsp;hop 2: Shape → MTSDF | 348.6 ± 95.5 µs | 2.8 MiB |

## `Convert.sdgToMtsdf`

SDG → Mask → Shape → MTSDF: LOSSY (paints)

| Input | Time | Allocated |
| --- | ---: | ---: |
| badge, 32 texels × 8 | 4.61 ± 0.17 ms | 4.0 MiB |
| &nbsp;&nbsp;hop 1: SDG → Mask | 4.01 ± 0.07 ms | 1.3 MiB |
| &nbsp;&nbsp;hop 2: Mask → Shape | 190.5 ± 34.0 µs | 745.4 KiB |
| &nbsp;&nbsp;hop 3: Shape → MTSDF | 155.7 ± 4.3 µs | 1.9 MiB |
| badge, 64 texels × 8 | 18.13 ± 0.47 ms | 20.0 MiB |
| &nbsp;&nbsp;hop 1: SDG → Mask | 16.23 ± 0.86 ms | 5.3 MiB |
| &nbsp;&nbsp;hop 2: Mask → Shape | 986.8 ± 342.8 µs | 2.7 MiB |
| &nbsp;&nbsp;hop 3: Shape → MTSDF | 855.8 ± 135.8 µs | 12.1 MiB |

## `Convert.sdgToMtsdfFast`

SDG → Mask → SDF → MTSDF: LOSSY (paints; corners: round at magnification)

| Input | Time | Allocated |
| --- | ---: | ---: |
| badge, 32 texels × 8 | 7.51 ± 0.20 ms | 2.6 MiB |
| &nbsp;&nbsp;hop 1: SDG → Mask | 4.10 ± 0.27 ms | 1.3 MiB |
| &nbsp;&nbsp;hop 2: Mask → SDF | 3.47 ± 0.48 ms | 1.3 MiB |
| &nbsp;&nbsp;hop 3: SDF → MTSDF | 4.2 ± 0.3 µs | 25.0 KiB |
| badge, 64 texels × 8 | 40.71 ± 4.53 ms | 10.5 MiB |
| &nbsp;&nbsp;hop 1: SDG → Mask | 22.31 ± 7.22 ms | 5.3 MiB |
| &nbsp;&nbsp;hop 2: Mask → SDF | 15.31 ± 4.53 ms | 5.1 MiB |
| &nbsp;&nbsp;hop 3: SDF → MTSDF | 13.7 ± 0.3 µs | 100.0 KiB |

## `Convert.rasterToMsdf`

Raster → Mask → Shape → MSDF: SYNTHESISING (a channel; a threshold)

| Input | Time | Allocated |
| --- | ---: | ---: |
| badge picture, 256 px to 32 texels | 823.7 ± 74.0 µs | 2.7 MiB |
| &nbsp;&nbsp;hop 1: Raster → Mask | 64.5 ± 34.3 µs | 64.1 KiB |
| &nbsp;&nbsp;hop 2: Mask → Shape | 237.9 ± 35.1 µs | 745.4 KiB |
| &nbsp;&nbsp;hop 3: Shape → MSDF | 177.9 ± 16.8 µs | 1.9 MiB |
| badge picture, 512 px to 64 texels | 1.91 ± 0.77 ms | 15.0 MiB |
| &nbsp;&nbsp;hop 1: Raster → Mask | 175.5 ± 4.4 µs | 256.1 KiB |
| &nbsp;&nbsp;hop 2: Mask → Shape | 748.5 ± 34.0 µs | 2.7 MiB |
| &nbsp;&nbsp;hop 3: Shape → MSDF | 861.6 ± 85.0 µs | 12.0 MiB |

## `Convert.msdfToSdf`

MSDF → Coverage → Mask → SDF: EXACT

| Input | Time | Allocated |
| --- | ---: | ---: |
| star, 32 texels × 8 | 4.13 ± 0.79 ms | 3.1 MiB |
| &nbsp;&nbsp;hop 1: MSDF → Coverage | 1.27 ± 0.32 ms | 2.3 MiB |
| &nbsp;&nbsp;hop 2: Coverage → Mask | 13.6 ± 5.3 µs | 64.1 KiB |
| &nbsp;&nbsp;hop 3: Mask → SDF | 2.09 ± 0.32 ms | 846.3 KiB |
| star, 64 texels × 8 | 12.71 ± 1.19 ms | 12.5 MiB |
| &nbsp;&nbsp;hop 1: MSDF → Coverage | 3.95 ± 0.05 ms | 9.0 MiB |
| &nbsp;&nbsp;hop 2: Coverage → Mask | 39.6 ± 3.8 µs | 256.1 KiB |
| &nbsp;&nbsp;hop 3: Mask → SDF | 9.81 ± 0.62 ms | 3.3 MiB |

## `Convert.sdgToRaster`

SDG → Raster: EXACT

| Input | Time | Allocated |
| --- | ---: | ---: |
| badge, 256 px | 3.97 ± 0.05 ms | 1.3 MiB |
| badge, 1024 px | 68.02 ± 37.77 ms | 20.0 MiB |

## `Convert.rasterToSdg`

Raster → SDG: LOSSY (colours: clustered into regions)

| Input | Time | Allocated |
| --- | ---: | ---: |
| badge picture, 64 px | 100.67 ± 3.83 ms | 44.5 MiB |
| badge picture, 128 px | 464.09 ± 13.44 ms | 196.5 MiB |

## `Convert.sdgToRasterSizes`

SDG → Raster sizes: EXACT

| Input | Time | Allocated |
| --- | ---: | ---: |
| badge, the 10 Windows icon sizes | 6.23 ± 0.07 ms | 1.9 MiB |
| badge, the 10 Windows icon sizes, a small drawing to 24 | 6.20 ± 0.09 ms | 1.9 MiB |

## `Convert.icon`

Raster sizes → Icon: EXACT

| Input | Time | Allocated |
| --- | ---: | ---: |
| badge, the 10 Windows icon sizes | 0.0 ± 0.0 µs | 208 B |

## `Convert.svgToRaster`

SVG → Raster: EXACT

| Input | Time | Allocated |
| --- | ---: | ---: |
| badge, 64 px | 986.0 ± 391.6 µs | 1.6 MiB |
| badge, 256 px | 15.21 ± 1.04 ms | 22.2 MiB |

## `Convert.svgToRasterSizes`

SVG → Raster sizes: EXACT

| Input | Time | Allocated |
| --- | ---: | ---: |
| badge, the 10 Windows icon sizes | 23.74 ± 1.11 ms | 35.6 MiB |

## `Convert.svgToIcon`

SVG → Raster sizes → Icon: EXACT

| Input | Time | Allocated |
| --- | ---: | ---: |
| badge, the 10 Windows icon sizes | 23.47 ± 0.51 ms | 35.5 MiB |
| &nbsp;&nbsp;hop 1: SVG → Raster sizes | 23.86 ± 0.96 ms | 35.6 MiB |
| &nbsp;&nbsp;hop 2: Raster sizes → Icon | 0.0 ± 0.0 µs | 208 B |

## `Convert.sdgToIcon`

SDG → Raster sizes → Icon: EXACT

| Input | Time | Allocated |
| --- | ---: | ---: |
| badge, the 10 Windows icon sizes | 6.22 ± 0.13 ms | 1.9 MiB |
| &nbsp;&nbsp;hop 1: SDG → Raster sizes | 7.15 ± 2.76 ms | 1.9 MiB |
| &nbsp;&nbsp;hop 2: Raster sizes → Icon | 0.0 ± 0.0 µs | 208 B |

## `Convert.sdgToArtPair`

(SDG → Mask → Shape → MTSDF) beside (SDG → Raster sizes) → Art pair: LOSSY (paints)

| Input | Time | Allocated |
| --- | ---: | ---: |
| badge, field 48, colour at 48 and 96 | 15.92 ± 3.37 ms | 18.4 MiB |
| badge, field 104, colour at 104 and 208 | 58.02 ± 26.69 ms | 80.5 MiB |

## `Convert.sdgToArtPairFast`

(SDG → Mask → SDF → MTSDF) beside (SDG → Raster sizes) → Art pair: LOSSY (paints; corners: round at magnification)

| Input | Time | Allocated |
| --- | ---: | ---: |
| badge, field 48, colour at 48 and 96 | 24.05 ± 2.74 ms | 12.6 MiB |
| badge, field 104, colour at 104 and 208 | 94.19 ± 5.16 ms | 40.5 MiB |

## `Convert.svgToArtPair`

SVG → SDG → ((SDG → Mask → Shape → MTSDF) beside (SDG → Raster sizes) → Art pair): LOSSY (paints)

| Input | Time | Allocated |
| --- | ---: | ---: |
| badge, field 48, colour at 48 and 96 | 21.35 ± 4.33 ms | 26.6 MiB |
| badge, field 104, colour at 104 and 208 | 58.71 ± 6.51 ms | 88.3 MiB |

## `Convert.svgToShape`

SVG → Shape: LOSSY (paints)

| Input | Time | Allocated |
| --- | ---: | ---: |
| star | 13.2 ± 2.4 µs | 91.2 KiB |

