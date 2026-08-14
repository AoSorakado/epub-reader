# Adaptive Blur Performance Characteristics

This note compares the 5, 9, 13, and 17 tap-equivalent adaptive blur kernels.
The numbers are static GPU work estimates, not measured timings.

## Cost Model

The adaptive blur pipeline does this for a blur radius:

1. Choose a downsample level with `ceil(log2(radiusPx / denseRadiusPx))`.
2. Downsample one level at a time with a 4-sample box pass.
3. Run horizontal and vertical separable blur at the selected level.
4. Upsample one level at a time with the linear sampler.

The tables use normalized full-resolution texture-read area units, written as
`A`. For example, `1.000 A` means roughly one texture read for every full-size
output pixel in the blur target area.

For selected level `L` and shader samples per blur pass `S`:

```txt
downsample(L) = 4/3 * (1 - 4^-L)
upsample(L)   = 4/3 * (1 - 4^-L)
blur(L)       = 2 * S * 4^-L
total(L)      = downsample(L) + blur(L) + upsample(L)
```

The visual discontinuity at a downsample boundary is not directly measured by
this model. The current working assumption is that wider kernels hide the
resolution-level transition better, even when their performance discontinuity is
larger.

## Variant Summary

| Equivalent taps | Gaussian sigma | Tap radius | Dense radius | Shader samples/pass | Blur reads at L0 | Level boundaries |
|---:|---:|---:|---:|---:|---:|---|
| 5 | 1 | 2px | 2px | 3 | 6.000 A | 2, 4, 8, 16, 32px |
| 9 | 2 | 4px | 4px | 5 | 10.000 A | 4, 8, 16, 32, 64px |
| 13 | 3 | 6px | 6px | 7 | 14.000 A | 6, 12, 24, 48, 96px |
| 17 | 4 | 8px | 8px | 9 | 18.000 A | 8, 16, 32, 64, 128px |

The boundaries column lists the upper edge of levels L0 through L4. For example,
the 13 tap equivalent uses L0 through 6px, L1 for `(6, 12]`, L2 for `(12, 24]`,
and so on.

## Normalized Total Cost By Selected Level

| Equivalent taps | L0 | L1 | L2 | L3 | L4 | L5 |
|---:|---:|---:|---:|---:|---:|---:|
| 5 | 6.000 A | 3.500 A | 2.875 A | 2.719 A | 2.680 A | 2.670 A |
| 9 | 10.000 A | 4.500 A | 3.125 A | 2.781 A | 2.695 A | 2.674 A |
| 13 | 14.000 A | 5.500 A | 3.375 A | 2.844 A | 2.711 A | 2.678 A |
| 17 | 18.000 A | 6.500 A | 3.625 A | 2.906 A | 2.727 A | 2.682 A |

At high levels, downsample and upsample work dominate, so all variants converge
toward about `2.667 A`.

## Work Drop At Level Boundaries

This table shows the total work ratio when crossing from one selected
downsample level to the next. It describes the performance discontinuity, not
the visual discontinuity.

| Equivalent taps | L0 -> L1 | L1 -> L2 | L2 -> L3 | L3 -> L4 | L4 -> L5 |
|---:|---:|---:|---:|---:|---:|
| 5 | 1.71x | 1.22x | 1.06x | 1.01x | 1.00x |
| 9 | 2.22x | 1.44x | 1.12x | 1.03x | 1.01x |
| 13 | 2.55x | 1.63x | 1.19x | 1.05x | 1.01x |
| 17 | 2.77x | 1.79x | 1.25x | 1.07x | 1.02x |

The larger kernels have bigger performance drops at the earliest level
boundaries because the blur pass is a larger share of total work before the
switch. In practice, the larger spatial support also makes those boundaries less
visible.

## Cost And Speedup At Example Radii

Speedup is relative to the 17 tap equivalent at the same blur radius.

| Radius | 5 tap | 9 tap | 13 tap | 17 tap baseline |
|---:|---|---|---|---|
| 2px | L0, 6.000 A, 3.00x | L0, 10.000 A, 1.80x | L0, 14.000 A, 1.29x | L0, 18.000 A, 1.00x |
| 4px | L1, 3.500 A, 5.14x | L0, 10.000 A, 1.80x | L0, 14.000 A, 1.29x | L0, 18.000 A, 1.00x |
| 6px | L2, 2.875 A, 6.26x | L1, 4.500 A, 4.00x | L0, 14.000 A, 1.29x | L0, 18.000 A, 1.00x |
| 8px | L2, 2.875 A, 6.26x | L1, 4.500 A, 4.00x | L1, 5.500 A, 3.27x | L0, 18.000 A, 1.00x |
| 12px | L3, 2.719 A, 2.39x | L2, 3.125 A, 2.08x | L1, 5.500 A, 1.18x | L1, 6.500 A, 1.00x |
| 16px | L3, 2.719 A, 2.39x | L2, 3.125 A, 2.08x | L2, 3.375 A, 1.93x | L1, 6.500 A, 1.00x |
| 20px | L4, 2.680 A, 1.35x | L3, 2.781 A, 1.30x | L2, 3.375 A, 1.07x | L2, 3.625 A, 1.00x |
| 24px | L4, 2.680 A, 1.35x | L3, 2.781 A, 1.30x | L2, 3.375 A, 1.07x | L2, 3.625 A, 1.00x |
| 32px | L4, 2.680 A, 1.35x | L3, 2.781 A, 1.30x | L3, 2.844 A, 1.27x | L2, 3.625 A, 1.00x |
| 48px | L5, 2.670 A, 1.09x | L4, 2.695 A, 1.08x | L3, 2.844 A, 1.02x | L3, 2.906 A, 1.00x |
| 64px | L5, 2.670 A, 1.09x | L4, 2.695 A, 1.08x | L4, 2.711 A, 1.07x | L3, 2.906 A, 1.00x |
| 128px | L6, 2.667 A, 1.02x | L5, 2.674 A, 1.02x | L5, 2.678 A, 1.02x | L4, 2.727 A, 1.00x |

## Practical Reading

| Equivalent taps | Performance profile | Boundary-continuity expectation |
|---:|---|---|
| 5 | Very cheap at small and medium radii because it downscales aggressively. | Highest risk. The narrow support exposes downsample-level changes. |
| 9 | Large savings at common radii with more support than 5 tap. | Medium risk. Worth checking around 4, 8, 16, and 32px thresholds. |
| 13 | Conservative savings with thresholds closer to the 17 tap baseline. | Lower risk. Likely closer to 17 tap at boundaries. |
| 17 | Most expensive at low levels, converges with others at high levels. | Baseline. Current observation is that boundaries are not noticeable. |
