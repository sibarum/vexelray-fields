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
| star, 32 texels | 81.9 ± 7.0 µs | 26.8 KiB |
| star, 64 texels | 335.8 ± 3.1 µs | 35.3 KiB |

## `Convert.shapeToMsdf`

Shape → MSDF: EXACT

| Input | Time | Allocated |
| --- | ---: | ---: |
| star, 32 texels | 92.2 ± 14.6 µs | 874.6 KiB |
| star, 64 texels | 238.4 ± 2.0 µs | 2.6 MiB |

## `Convert.shapeToMtsdf`

Shape → MTSDF: EXACT

| Input | Time | Allocated |
| --- | ---: | ---: |
| star, 32 texels | 88.8 ± 9.2 µs | 860.1 KiB |
| star, 64 texels | 250.5 ± 2.2 µs | 2.8 MiB |

## `Convert.svgToSdg`

SVG → SDG: EXACT

| Input | Time | Allocated |
| --- | ---: | ---: |
| badge, 32 texels across | 2.24 ± 0.28 ms | 2.4 MiB |
| badge, 64 texels across | 5.15 ± 0.07 ms | 7.0 MiB |

## `Convert.maskToSdf`

Mask → SDF: EXACT

| Input | Time | Allocated |
| --- | ---: | ---: |
| star, 32 texels × 8 | 3.03 ± 0.03 ms | 1.3 MiB |
| star, 64 texels × 8 | 14.23 ± 0.11 ms | 5.1 MiB |

## `Convert.maskToShape`

Mask → Shape: EXACT

| Input | Time | Allocated |
| --- | ---: | ---: |
| star, 32 texels × 8 | 146.8 ± 3.5 µs | 698.5 KiB |
| star, 64 texels × 8 | 559.0 ± 49.0 µs | 2.6 MiB |

## `Convert.widen`

SDF → MTSDF: LOSSY (corners: round at magnification)

| Input | Time | Allocated |
| --- | ---: | ---: |
| star SDF, 32 texels | 2.2 ± 0.2 µs | 16.0 KiB |
| star SDF, 64 texels | 8.2 ± 2.8 µs | 64.0 KiB |

## `Convert.sdfToCoverage`

SDF → Coverage: EXACT

| Input | Time | Allocated |
| --- | ---: | ---: |
| star, 32 texels to 256 px | 414.8 ± 6.7 µs | 1.8 MiB |
| star, 64 texels to 512 px | 1.60 ± 0.01 ms | 7.0 MiB |

## `Convert.msdfToCoverage`

MSDF → Coverage: EXACT

| Input | Time | Allocated |
| --- | ---: | ---: |
| star, 32 texels to 256 px | 970.2 ± 21.4 µs | 2.3 MiB |
| star, 64 texels to 512 px | 3.81 ± 0.03 ms | 9.0 MiB |

## `Convert.mtsdfToCoverage`

MTSDF → Coverage: EXACT

| Input | Time | Allocated |
| --- | ---: | ---: |
| star, 32 texels to 256 px | 970.7 ± 19.6 µs | 2.3 MiB |
| star, 64 texels to 512 px | 3.80 ± 0.03 ms | 9.0 MiB |

## `Convert.coverageToMask`

Coverage → Mask: EXACT

| Input | Time | Allocated |
| --- | ---: | ---: |
| star, 256 px | 9.2 ± 0.2 µs | 64.1 KiB |
| star, 512 px | 33.6 ± 1.3 µs | 256.1 KiB |

## `Convert.fieldToSdg`

SDF → SDG: SYNTHESISING (a paint)

| Input | Time | Allocated |
| --- | ---: | ---: |
| star SDF, 32 texels, a solid paint | 10.0 ± 0.1 µs | 4.2 KiB |
| star SDF, 32 texels, a texture | 10.0 ± 0.2 µs | 4.3 KiB |
| star SDF, 64 texels, a solid paint | 39.4 ± 0.4 µs | 16.2 KiB |
| star SDF, 64 texels, a texture | 39.5 ± 0.1 µs | 16.3 KiB |

## `Convert.sdgToMask`

SDG → Mask: LOSSY (paints)

| Input | Time | Allocated |
| --- | ---: | ---: |
| badge, 32 texels × 8 | 4.02 ± 0.08 ms | 1.3 MiB |
| badge, 64 texels × 8 | 15.48 ± 0.13 ms | 5.3 MiB |

## `Convert.sdfToProbability`

SDF → Probability: SYNTHESISING (a footprint's σ)

| Input | Time | Allocated |
| --- | ---: | ---: |
| star, 32 texels, σ 1.5 | 10.8 ± 0.6 µs | 8.1 KiB |
| star, 32 texels, σ per texel | 13.1 ± 0.4 µs | 8.1 KiB |
| star, 64 texels, σ 1.5 | 62.4 ± 29.6 µs | 32.1 KiB |
| star, 64 texels, σ per texel | 51.7 ± 0.4 µs | 32.1 KiB |

## `Convert.probabilityToSdf`

Probability → SDF: LOSSY (distances beyond the band: flat)

| Input | Time | Allocated |
| --- | ---: | ---: |
| star, 32 texels | 30.0 ± 1.0 µs | 4.0 KiB |
| star, 64 texels | 125.2 ± 0.5 µs | 16.0 KiB |

## `Convert.sdgLayerSdf`

SDG → SDF: EXACT

| Input | Time | Allocated |
| --- | ---: | ---: |
| star SDG, 32 texels | 1.0 ± 0.1 µs | 4.0 KiB |
| star SDG, 64 texels | 4.1 ± 0.0 µs | 16.0 KiB |

## `Convert.sdgLayerMsdf`

SDG → MSDF: EXACT

| Input | Time | Allocated |
| --- | ---: | ---: |
| star SDG, 32 texels | 3.1 ± 0.2 µs | 12.0 KiB |
| star SDG, 64 texels | 12.1 ± 0.2 µs | 48.0 KiB |

## `Convert.rasterToMask`

Raster → Mask: SYNTHESISING (a channel; a threshold)

| Input | Time | Allocated |
| --- | ---: | ---: |
| badge picture, 256 px | 42.5 ± 0.6 µs | 64.1 KiB |
| badge picture, 512 px | 167.9 ± 1.5 µs | 256.1 KiB |

## `Convert.svgToMtsdf`

SVG → Shape → MTSDF: LOSSY (paints)

| Input | Time | Allocated |
| --- | ---: | ---: |
| star, 32 texels | 102.4 ± 12.2 µs | 956.6 KiB |
| &nbsp;&nbsp;hop 1: SVG → Shape | 12.1 ± 2.3 µs | 91.2 KiB |
| &nbsp;&nbsp;hop 2: Shape → MTSDF | 92.4 ± 17.7 µs | 891.8 KiB |
| star, 64 texels | 265.6 ± 3.3 µs | 2.8 MiB |
| &nbsp;&nbsp;hop 1: SVG → Shape | 10.6 ± 0.2 µs | 91.2 KiB |
| &nbsp;&nbsp;hop 2: Shape → MTSDF | 256.6 ± 9.1 µs | 2.8 MiB |

## `Convert.sdgToMtsdf`

SDG → Mask → Shape → MTSDF: LOSSY (paints)

| Input | Time | Allocated |
| --- | ---: | ---: |
| badge, 32 texels × 8 | 4.47 ± 0.08 ms | 3.9 MiB |
| &nbsp;&nbsp;hop 1: SDG → Mask | 4.00 ± 0.11 ms | 1.3 MiB |
| &nbsp;&nbsp;hop 2: Mask → Shape | 175.7 ± 4.8 µs | 701.2 KiB |
| &nbsp;&nbsp;hop 3: Shape → MTSDF | 153.6 ± 19.1 µs | 2.0 MiB |
| badge, 64 texels × 8 | 17.25 ± 0.23 ms | 19.9 MiB |
| &nbsp;&nbsp;hop 1: SDG → Mask | 15.55 ± 0.11 ms | 5.3 MiB |
| &nbsp;&nbsp;hop 2: Mask → Shape | 667.1 ± 8.5 µs | 2.6 MiB |
| &nbsp;&nbsp;hop 3: Shape → MTSDF | 661.4 ± 6.2 µs | 12.1 MiB |

## `Convert.sdgToMtsdfFast`

SDG → Mask → SDF → MTSDF: LOSSY (paints; corners: round at magnification)

| Input | Time | Allocated |
| --- | ---: | ---: |
| badge, 32 texels × 8 | 7.35 ± 0.12 ms | 2.6 MiB |
| &nbsp;&nbsp;hop 1: SDG → Mask | 3.99 ± 0.12 ms | 1.3 MiB |
| &nbsp;&nbsp;hop 2: Mask → SDF | 3.37 ± 0.08 ms | 1.3 MiB |
| &nbsp;&nbsp;hop 3: SDF → MTSDF | 3.1 ± 0.2 µs | 25.0 KiB |
| badge, 64 texels × 8 | 29.26 ± 0.17 ms | 10.5 MiB |
| &nbsp;&nbsp;hop 1: SDG → Mask | 15.50 ± 0.05 ms | 5.3 MiB |
| &nbsp;&nbsp;hop 2: Mask → SDF | 13.72 ± 0.15 ms | 5.1 MiB |
| &nbsp;&nbsp;hop 3: SDF → MTSDF | 12.3 ± 0.2 µs | 100.0 KiB |

## `Convert.rasterToMsdf`

Raster → Mask → Shape → MSDF: SYNTHESISING (a channel; a threshold)

| Input | Time | Allocated |
| --- | ---: | ---: |
| badge picture, 256 px to 32 texels | 405.8 ± 15.1 µs | 2.7 MiB |
| &nbsp;&nbsp;hop 1: Raster → Mask | 42.4 ± 0.5 µs | 64.1 KiB |
| &nbsp;&nbsp;hop 2: Mask → Shape | 175.4 ± 8.8 µs | 701.2 KiB |
| &nbsp;&nbsp;hop 3: Shape → MSDF | 145.6 ± 10.9 µs | 1.9 MiB |
| badge picture, 512 px to 64 texels | 1.74 ± 0.13 ms | 17.3 MiB |
| &nbsp;&nbsp;hop 1: Raster → Mask | 167.4 ± 0.7 µs | 256.1 KiB |
| &nbsp;&nbsp;hop 2: Mask → Shape | 687.8 ± 109.7 µs | 2.6 MiB |
| &nbsp;&nbsp;hop 3: Shape → MSDF | 662.5 ± 9.8 µs | 12.0 MiB |

## `Convert.msdfToSdf`

MSDF → Coverage → Mask → SDF: EXACT

| Input | Time | Allocated |
| --- | ---: | ---: |
| star, 32 texels × 8 | 3.24 ± 0.02 ms | 3.1 MiB |
| &nbsp;&nbsp;hop 1: MSDF → Coverage | 944.9 ± 27.0 µs | 2.3 MiB |
| &nbsp;&nbsp;hop 2: Coverage → Mask | 8.3 ± 0.1 µs | 64.1 KiB |
| &nbsp;&nbsp;hop 3: Mask → SDF | 2.28 ± 0.02 ms | 846.3 KiB |
| star, 64 texels × 8 | 13.23 ± 0.05 ms | 12.5 MiB |
| &nbsp;&nbsp;hop 1: MSDF → Coverage | 3.80 ± 0.03 ms | 9.0 MiB |
| &nbsp;&nbsp;hop 2: Coverage → Mask | 33.5 ± 0.3 µs | 256.1 KiB |
| &nbsp;&nbsp;hop 3: Mask → SDF | 8.18 ± 0.08 ms | 3.3 MiB |

## `Convert.sdgToRaster`

SDG → Raster: EXACT

| Input | Time | Allocated |
| --- | ---: | ---: |
| badge, 256 px | 3.86 ± 0.03 ms | 1.3 MiB |
| badge, 1024 px | 62.29 ± 1.23 ms | 20.0 MiB |

## `Convert.rasterToSdg`

Raster → SDG: LOSSY (colours: clustered into regions)

| Input | Time | Allocated |
| --- | ---: | ---: |
| badge picture, 64 px | 95.58 ± 0.46 ms | 44.5 MiB |
| badge picture, 128 px | 444.95 ± 2.25 ms | 196.5 MiB |

## `Convert.sdgToRasterSizes`

SDG → Raster sizes: EXACT

| Input | Time | Allocated |
| --- | ---: | ---: |
| badge, the 10 Windows icon sizes | 6.09 ± 0.59 ms | 1.9 MiB |
| badge, the 10 Windows icon sizes, a small drawing to 24 | 6.07 ± 0.26 ms | 1.9 MiB |

## `Convert.icon`

Raster sizes → Icon: EXACT

| Input | Time | Allocated |
| --- | ---: | ---: |
| badge, the 10 Windows icon sizes | 0.0 ± 0.0 µs | 208 B |

## `Convert.sdgToIcon`

SDG → Raster sizes → Icon: EXACT

| Input | Time | Allocated |
| --- | ---: | ---: |
| badge, the 10 Windows icon sizes | 6.01 ± 0.23 ms | 1.9 MiB |
| &nbsp;&nbsp;hop 1: SDG → Raster sizes | 6.11 ± 0.50 ms | 1.9 MiB |
| &nbsp;&nbsp;hop 2: Raster sizes → Icon | 0.0 ± 0.0 µs | 208 B |

## `Convert.sdgToArtPair`

(SDG → Mask → Shape → MTSDF) beside (SDG → Raster sizes) → Art pair: LOSSY (paints)

| Input | Time | Allocated |
| --- | ---: | ---: |
| badge, field 48, colour at 48 and 96 | 12.76 ± 0.26 ms | 18.3 MiB |
| badge, field 104, colour at 104 and 208 | 51.21 ± 10.65 ms | 80.2 MiB |

## `Convert.sdgToArtPairFast`

(SDG → Mask → SDF → MTSDF) beside (SDG → Raster sizes) → Art pair: LOSSY (paints; corners: round at magnification)

| Input | Time | Allocated |
| --- | ---: | ---: |
| badge, field 48, colour at 48 and 96 | 20.46 ± 0.74 ms | 12.6 MiB |
| badge, field 104, colour at 104 and 208 | 88.54 ± 0.73 ms | 40.5 MiB |

## `Convert.svgToArtPair`

SVG → SDG → ((SDG → Mask → Shape → MTSDF) beside (SDG → Raster sizes) → Art pair): LOSSY (paints)

| Input | Time | Allocated |
| --- | ---: | ---: |
| badge, field 48, colour at 48 and 96 | 19.21 ± 0.30 ms | 26.0 MiB |
| badge, field 104, colour at 104 and 208 | 56.67 ± 1.18 ms | 88.1 MiB |

## `Convert.svgToShape`

SVG → Shape: LOSSY (paints)

| Input | Time | Allocated |
| --- | ---: | ---: |
| star | 10.5 ± 0.2 µs | 91.2 KiB |

