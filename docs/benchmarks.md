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
| star, 32 texels | 86.5 ± 43.7 µs | 26.8 KiB |
| star, 64 texels | 532.7 ± 212.7 µs | 35.3 KiB |

## `Convert.shapeToMsdf`

Shape → MSDF: EXACT

| Input | Time | Allocated |
| --- | ---: | ---: |
| star, 32 texels | 96.3 ± 4.4 µs | 880.6 KiB |
| star, 64 texels | 424.5 ± 219.7 µs | 2.6 MiB |

## `Convert.shapeToMtsdf`

Shape → MTSDF: EXACT

| Input | Time | Allocated |
| --- | ---: | ---: |
| star, 32 texels | 95.7 ± 9.6 µs | 878.7 KiB |
| star, 64 texels | 423.3 ± 73.1 µs | 2.6 MiB |

## `Convert.svgToSdg`

SVG → SDG: EXACT

| Input | Time | Allocated |
| --- | ---: | ---: |
| badge, 32 texels across | 2.22 ± 0.14 ms | 2.4 MiB |
| badge, 64 texels across | 7.62 ± 0.90 ms | 7.8 MiB |

## `Convert.maskToSdf`

Mask → SDF: EXACT

| Input | Time | Allocated |
| --- | ---: | ---: |
| star, 32 texels × 8 | 3.60 ± 0.63 ms | 1.3 MiB |
| star, 64 texels × 8 | 16.83 ± 0.63 ms | 5.1 MiB |

## `Convert.maskToShape`

Mask → Shape: EXACT

| Input | Time | Allocated |
| --- | ---: | ---: |
| star, 32 texels × 8 | 150.9 ± 3.7 µs | 741.7 KiB |
| star, 64 texels × 8 | 750.4 ± 198.9 µs | 2.7 MiB |

## `Convert.maskToCells`

Mask → Shape: EXACT

| Input | Time | Allocated |
| --- | ---: | ---: |
| star, 32 texels × 8 | 158.5 ± 49.7 µs | 752.9 KiB |
| star, 64 texels × 8 | 836.4 ± 153.5 µs | 2.7 MiB |

## `Convert.widen`

SDF → MTSDF: LOSSY (corners: round at magnification)

| Input | Time | Allocated |
| --- | ---: | ---: |
| star SDF, 32 texels | 2.1 ± 0.1 µs | 16.0 KiB |
| star SDF, 64 texels | 11.0 ± 2.0 µs | 64.0 KiB |

## `Convert.sdfToCoverage`

SDF → Coverage: EXACT

| Input | Time | Allocated |
| --- | ---: | ---: |
| star, 32 texels to 256 px | 432.8 ± 235.2 µs | 1.8 MiB |
| star, 64 texels to 512 px | 2.16 ± 0.37 ms | 7.0 MiB |

## `Convert.msdfToCoverage`

MSDF → Coverage: EXACT

| Input | Time | Allocated |
| --- | ---: | ---: |
| star, 32 texels to 256 px | 951.3 ± 30.4 µs | 2.3 MiB |
| star, 64 texels to 512 px | 4.87 ± 1.32 ms | 9.0 MiB |

## `Convert.mtsdfToCoverage`

MTSDF → Coverage: EXACT

| Input | Time | Allocated |
| --- | ---: | ---: |
| star, 32 texels to 256 px | 969.8 ± 35.4 µs | 2.3 MiB |
| star, 64 texels to 512 px | 5.24 ± 0.32 ms | 9.0 MiB |

## `Convert.coverageToMask`

Coverage → Mask: EXACT

| Input | Time | Allocated |
| --- | ---: | ---: |
| star, 256 px | 9.5 ± 0.6 µs | 64.1 KiB |
| star, 512 px | 54.7 ± 19.0 µs | 256.1 KiB |

## `Convert.fieldToSdg`

SDF → SDG: SYNTHESISING (a paint)

| Input | Time | Allocated |
| --- | ---: | ---: |
| star SDF, 32 texels, a solid paint | 10.5 ± 4.3 µs | 4.2 KiB |
| star SDF, 32 texels, a texture | 10.0 ± 0.2 µs | 4.3 KiB |
| star SDF, 64 texels, a solid paint | 47.5 ± 10.2 µs | 16.2 KiB |
| star SDF, 64 texels, a texture | 55.9 ± 8.4 µs | 16.3 KiB |

## `Convert.sdgToMask`

SDG → Mask: LOSSY (paints)

| Input | Time | Allocated |
| --- | ---: | ---: |
| badge, 32 texels × 8 | 4.00 ± 0.02 ms | 1.3 MiB |
| badge, 64 texels × 8 | 20.11 ± 1.64 ms | 5.3 MiB |

## `Convert.sdfToProbability`

SDF → Probability: SYNTHESISING (a footprint's σ)

| Input | Time | Allocated |
| --- | ---: | ---: |
| star, 32 texels, σ 1.5 | 10.7 ± 0.2 µs | 8.1 KiB |
| star, 32 texels, σ per texel | 13.5 ± 3.1 µs | 8.1 KiB |
| star, 64 texels, σ 1.5 | 80.5 ± 32.8 µs | 32.1 KiB |
| star, 64 texels, σ per texel | 79.4 ± 21.5 µs | 32.1 KiB |

## `Convert.probabilityToSdf`

Probability → SDF: LOSSY (distances beyond the band: flat)

| Input | Time | Allocated |
| --- | ---: | ---: |
| star, 32 texels | 30.4 ± 1.9 µs | 4.0 KiB |
| star, 64 texels | 165.1 ± 58.4 µs | 16.0 KiB |

## `Convert.sdgLayerSdf`

SDG → SDF: EXACT

| Input | Time | Allocated |
| --- | ---: | ---: |
| star SDG, 32 texels | 1.1 ± 0.0 µs | 4.0 KiB |
| star SDG, 64 texels | 5.2 ± 0.8 µs | 16.0 KiB |

## `Convert.sdgLayerMsdf`

SDG → MSDF: EXACT

| Input | Time | Allocated |
| --- | ---: | ---: |
| star SDG, 32 texels | 3.2 ± 0.1 µs | 12.0 KiB |
| star SDG, 64 texels | 16.7 ± 5.5 µs | 48.0 KiB |

## `Convert.rasterToMask`

Raster → Mask: SYNTHESISING (a channel; a threshold)

| Input | Time | Allocated |
| --- | ---: | ---: |
| badge picture, 256 px | 42.8 ± 0.6 µs | 64.1 KiB |
| badge picture, 512 px | 170.7 ± 7.5 µs | 256.1 KiB |

## `Convert.svgToMtsdf`

SVG → Shape → MTSDF: LOSSY (paints)

| Input | Time | Allocated |
| --- | ---: | ---: |
| star, 32 texels | 108.8 ± 25.1 µs | 945.3 KiB |
| &nbsp;&nbsp;hop 1: SVG → Shape | 12.4 ± 0.2 µs | 91.2 KiB |
| &nbsp;&nbsp;hop 2: Shape → MTSDF | 96.2 ± 66.0 µs | 807.8 KiB |
| star, 64 texels | 320.9 ± 57.3 µs | 3.0 MiB |
| &nbsp;&nbsp;hop 1: SVG → Shape | 10.8 ± 0.3 µs | 91.2 KiB |
| &nbsp;&nbsp;hop 2: Shape → MTSDF | 262.5 ± 10.3 µs | 2.9 MiB |

## `Convert.sdgToMtsdf`

SDG → Mask → Shape → MTSDF: LOSSY (paints)

| Input | Time | Allocated |
| --- | ---: | ---: |
| badge, 32 texels × 8 | 4.49 ± 0.07 ms | 4.0 MiB |
| &nbsp;&nbsp;hop 1: SDG → Mask | 4.01 ± 0.03 ms | 1.3 MiB |
| &nbsp;&nbsp;hop 2: Mask → Shape | 180.0 ± 6.9 µs | 745.4 KiB |
| &nbsp;&nbsp;hop 3: Shape → MTSDF | 165.6 ± 54.9 µs | 1.9 MiB |
| badge, 64 texels × 8 | 17.41 ± 0.26 ms | 20.0 MiB |
| &nbsp;&nbsp;hop 1: SDG → Mask | 15.60 ± 0.32 ms | 5.3 MiB |
| &nbsp;&nbsp;hop 2: Mask → Shape | 747.0 ± 113.0 µs | 2.7 MiB |
| &nbsp;&nbsp;hop 3: Shape → MTSDF | 784.4 ± 23.4 µs | 12.0 MiB |

## `Convert.sdgToMtsdfFast`

SDG → Mask → SDF → MTSDF: LOSSY (paints; corners: round at magnification)

| Input | Time | Allocated |
| --- | ---: | ---: |
| badge, 32 texels × 8 | 7.52 ± 0.86 ms | 2.6 MiB |
| &nbsp;&nbsp;hop 1: SDG → Mask | 4.03 ± 0.10 ms | 1.3 MiB |
| &nbsp;&nbsp;hop 2: Mask → SDF | 3.38 ± 0.04 ms | 1.3 MiB |
| &nbsp;&nbsp;hop 3: SDF → MTSDF | 3.2 ± 0.2 µs | 25.0 KiB |
| badge, 64 texels × 8 | 30.49 ± 0.64 ms | 10.5 MiB |
| &nbsp;&nbsp;hop 1: SDG → Mask | 16.17 ± 0.19 ms | 5.3 MiB |
| &nbsp;&nbsp;hop 2: Mask → SDF | 14.17 ± 0.45 ms | 5.1 MiB |
| &nbsp;&nbsp;hop 3: SDF → MTSDF | 13.6 ± 1.1 µs | 100.0 KiB |

## `Convert.rasterToMsdf`

Raster → Mask → Shape → MSDF: SYNTHESISING (a channel; a threshold)

| Input | Time | Allocated |
| --- | ---: | ---: |
| badge picture, 256 px to 32 texels | 453.9 ± 27.3 µs | 2.7 MiB |
| &nbsp;&nbsp;hop 1: Raster → Mask | 42.7 ± 0.8 µs | 64.1 KiB |
| &nbsp;&nbsp;hop 2: Mask → Shape | 180.9 ± 5.3 µs | 745.4 KiB |
| &nbsp;&nbsp;hop 3: Shape → MSDF | 163.0 ± 44.4 µs | 1.9 MiB |
| badge picture, 512 px to 64 texels | 1.81 ± 0.19 ms | 15.0 MiB |
| &nbsp;&nbsp;hop 1: Raster → Mask | 178.6 ± 2.1 µs | 256.1 KiB |
| &nbsp;&nbsp;hop 2: Mask → Shape | 706.9 ± 19.0 µs | 2.7 MiB |
| &nbsp;&nbsp;hop 3: Shape → MSDF | 660.5 ± 11.4 µs | 12.1 MiB |

## `Convert.msdfToSdf`

MSDF → Coverage → Mask → SDF: EXACT

| Input | Time | Allocated |
| --- | ---: | ---: |
| star, 32 texels × 8 | 3.35 ± 0.03 ms | 3.1 MiB |
| &nbsp;&nbsp;hop 1: MSDF → Coverage | 1.20 ± 0.09 ms | 2.3 MiB |
| &nbsp;&nbsp;hop 2: Coverage → Mask | 15.1 ± 4.8 µs | 64.1 KiB |
| &nbsp;&nbsp;hop 3: Mask → SDF | 2.43 ± 0.44 ms | 846.3 KiB |
| star, 64 texels × 8 | 14.20 ± 0.17 ms | 12.5 MiB |
| &nbsp;&nbsp;hop 1: MSDF → Coverage | 3.90 ± 0.07 ms | 9.0 MiB |
| &nbsp;&nbsp;hop 2: Coverage → Mask | 36.8 ± 2.4 µs | 256.1 KiB |
| &nbsp;&nbsp;hop 3: Mask → SDF | 9.99 ± 0.35 ms | 3.3 MiB |

## `Convert.sdgToRaster`

SDG → Raster: EXACT

| Input | Time | Allocated |
| --- | ---: | ---: |
| badge, 256 px | 3.87 ± 0.04 ms | 1.3 MiB |
| badge, 1024 px | 62.39 ± 0.59 ms | 20.0 MiB |

## `Convert.rasterToSdg`

Raster → SDG: LOSSY (colours: clustered into regions)

| Input | Time | Allocated |
| --- | ---: | ---: |
| badge picture, 64 px | 97.28 ± 0.65 ms | 44.5 MiB |
| badge picture, 128 px | 448.34 ± 7.48 ms | 196.5 MiB |

## `Convert.sdgToRasterSizes`

SDG → Raster sizes: EXACT

| Input | Time | Allocated |
| --- | ---: | ---: |
| badge, the 10 Windows icon sizes | 6.26 ± 0.18 ms | 1.9 MiB |
| badge, the 10 Windows icon sizes, a small drawing to 24 | 6.26 ± 0.05 ms | 1.9 MiB |

## `Convert.icon`

Raster sizes → Icon: EXACT

| Input | Time | Allocated |
| --- | ---: | ---: |
| badge, the 10 Windows icon sizes | 0.0 ± 0.0 µs | 208 B |

## `Convert.svgToRaster`

SVG → Raster: EXACT

| Input | Time | Allocated |
| --- | ---: | ---: |
| badge, 64 px | 851.1 ± 47.1 µs | 1.6 MiB |
| badge, 256 px | 14.95 ± 0.20 ms | 22.1 MiB |

## `Convert.svgToRasterSizes`

SVG → Raster sizes: EXACT

| Input | Time | Allocated |
| --- | ---: | ---: |
| badge, the 10 Windows icon sizes | 23.41 ± 0.53 ms | 35.6 MiB |

## `Convert.svgToIcon`

SVG → Raster sizes → Icon: EXACT

| Input | Time | Allocated |
| --- | ---: | ---: |
| badge, the 10 Windows icon sizes | 23.36 ± 0.39 ms | 35.6 MiB |
| &nbsp;&nbsp;hop 1: SVG → Raster sizes | 23.37 ± 0.29 ms | 35.5 MiB |
| &nbsp;&nbsp;hop 2: Raster sizes → Icon | 0.0 ± 0.0 µs | 208 B |

## `Convert.sdgToIcon`

SDG → Raster sizes → Icon: EXACT

| Input | Time | Allocated |
| --- | ---: | ---: |
| badge, the 10 Windows icon sizes | 6.22 ± 0.20 ms | 1.9 MiB |
| &nbsp;&nbsp;hop 1: SDG → Raster sizes | 6.12 ± 0.31 ms | 1.9 MiB |
| &nbsp;&nbsp;hop 2: Raster sizes → Icon | 0.0 ± 0.0 µs | 208 B |

## `Convert.sdgToArtPair`

(SDG → Mask → Shape → MTSDF) beside (SDG → Raster sizes) → Art pair: LOSSY (paints)

| Input | Time | Allocated |
| --- | ---: | ---: |
| badge, field 48, colour at 48 and 96 | 12.74 ± 0.69 ms | 19.8 MiB |
| badge, field 104, colour at 104 and 208 | 50.63 ± 1.28 ms | 80.4 MiB |

## `Convert.sdgToArtPairFast`

(SDG → Mask → SDF → MTSDF) beside (SDG → Raster sizes) → Art pair: LOSSY (paints; corners: round at magnification)

| Input | Time | Allocated |
| --- | ---: | ---: |
| badge, field 48, colour at 48 and 96 | 21.03 ± 0.26 ms | 12.6 MiB |
| badge, field 104, colour at 104 and 208 | 96.38 ± 0.42 ms | 40.5 MiB |

## `Convert.svgToArtPair`

SVG → SDG → ((SDG → Mask → Shape → MTSDF) beside (SDG → Raster sizes) → Art pair): LOSSY (paints)

| Input | Time | Allocated |
| --- | ---: | ---: |
| badge, field 48, colour at 48 and 96 | 19.14 ± 0.64 ms | 26.5 MiB |
| badge, field 104, colour at 104 and 208 | 55.50 ± 0.97 ms | 88.2 MiB |

## `Convert.svgToShape`

SVG → Shape: LOSSY (paints)

| Input | Time | Allocated |
| --- | ---: | ---: |
| star | 11.1 ± 2.6 µs | 90.9 KiB |

