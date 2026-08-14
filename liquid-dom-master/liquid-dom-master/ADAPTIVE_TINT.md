# Adaptive Tint with the Metrics API

This document describes one practical way to implement adaptive glass tint on top of the `@liquid-dom/core` metrics API.

The goal is straightforward:

- when the backdrop under a glass container is dark, use a darker tint
- when the backdrop is bright, use a brighter tint
- avoid reacting to every tiny metric fluctuation
- delay updates until the backdrop has settled
- animate tint changes smoothly instead of snapping

The renderer already computes backdrop metrics for a tracked container. Your application can use those metrics to drive tint however it wants.

## Metrics API

The relevant renderer methods are:

```ts
renderer.setBackdropMetricsTracking(container, true)
const metrics = renderer.getBackdropMetrics(container)
```

`getBackdropMetrics(container)` returns either `null` or an object with these fields:

```ts
type BackdropMetrics = {
  averageLinearColor: {
    r: number
    g: number
    b: number
  }
  averageLuminance: number
  luminanceP10: number
  luminanceP50: number
  luminanceP90: number
}
```

These are linear-light measurements of the backdrop covered by the container.

## Recommended approach

For adaptive tint, a good default is:

1. Use `luminanceP50` as the main brightness input.
2. Map that luminance to a tint brightness target.
3. Only restart the debounce timer when that target meaningfully changes.
4. After the delay expires, ease the current tint toward the target.
5. Write the current tint back to `container.tint` every frame.

This gives stable behavior without pushing too much policy into the renderer.

## Why use `luminanceP50`

`luminanceP50` is the median sampled luminance.

That is usually a better adaptive-tint signal than `averageLuminance` because it is less sensitive to small bright or dark outliers. For example:

- one bright highlight will skew the average upward
- one small dark icon will skew the average downward
- the median usually tracks the dominant local tone more reliably

You may still choose a different metric depending on the look you want:

- `averageLuminance` for smoother, more global behavior
- `luminanceP10` if you want tint to respect dark pockets more strongly
- `luminanceP90` if you want bright regions to dominate sooner

## Basic setup

Enable tracking for any container whose backdrop metrics you want to read:

```ts
import { Container, Renderer } from '@liquid-dom/core'

const renderer = new Renderer()
const container = new Container()

renderer.setBackdropMetricsTracking(container, true)
```

When you are done with the container, disable tracking:

```ts
renderer.setBackdropMetricsTracking(container, false)
```

## A simple adaptive tint loop

This example assumes your app already has its own frame loop or update loop.

```ts
import type { BackdropMetrics, Container, RgbaColor, Renderer } from '@liquid-dom/core'

type AdaptiveTintState = {
  currentBrightness: number
  targetBrightness: number
  pendingBrightness: number | null
  observedBrightness: number | null
  settleAtMs: number
  alpha: number
}

function clamp(value: number, min: number, max: number) {
  return Math.min(Math.max(value, min), max)
}

function smoothstep(edge0: number, edge1: number, value: number) {
  const t = clamp((value - edge0) / Math.max(edge1 - edge0, Number.EPSILON), 0, 1)
  return t * t * (3 - 2 * t)
}

function targetTintBrightness(metrics: BackdropMetrics) {
  const luminance = metrics.luminanceP50
  const normalized = smoothstep(0.08, 0.92, luminance)
  const mappedBrightness = 0.1 + normalized * 0.75

  // Optional floor: if the backdrop is already bright, do not allow the tint
  // to become darker than the sampled brightness.
  return luminance >= 0.5 ? Math.max(mappedBrightness, luminance) : mappedBrightness
}

function shouldUpdateTarget(current: number | null, next: number, epsilon = 0.01) {
  return current === null || Math.abs(current - next) > epsilon
}

function grayscaleTint(brightness: number, alpha: number): RgbaColor {
  return {
    r: brightness,
    g: brightness,
    b: brightness,
    a: alpha,
  }
}

const adaptiveTintState: AdaptiveTintState = {
  currentBrightness: 0.2,
  targetBrightness: 0.2,
  pendingBrightness: null,
  observedBrightness: null,
  settleAtMs: 0,
  alpha: 0.7,
}

const easingDurationMs = 500
const easingDelayMs = 300

function updateAdaptiveTint(
  renderer: Renderer,
  container: Container,
  nowMs: number,
  deltaMs: number,
) {
  const metrics = renderer.getBackdropMetrics(container)

  if (metrics) {
    const nextObservedBrightness = targetTintBrightness(metrics)

    if (shouldUpdateTarget(adaptiveTintState.observedBrightness, nextObservedBrightness)) {
      adaptiveTintState.pendingBrightness = nextObservedBrightness
      adaptiveTintState.observedBrightness = nextObservedBrightness
      adaptiveTintState.settleAtMs = nowMs + easingDelayMs
    }
  }

  if (
    adaptiveTintState.pendingBrightness !== null &&
    nowMs >= adaptiveTintState.settleAtMs
  ) {
    adaptiveTintState.targetBrightness = adaptiveTintState.pendingBrightness
    adaptiveTintState.pendingBrightness = null
  }

  const blend = 1 - Math.exp(-deltaMs / Math.max(easingDurationMs, 1))

  adaptiveTintState.currentBrightness +=
    (adaptiveTintState.targetBrightness - adaptiveTintState.currentBrightness) * blend

  container.tint = grayscaleTint(
    adaptiveTintState.currentBrightness,
    adaptiveTintState.alpha,
  )
}
```

## How to call it from a frame loop

Here is a minimal pattern:

```ts
let lastTimeMs: number | null = null

function frame(nowMs: number) {
  const deltaMs =
    lastTimeMs === null ? 16.667 : Math.max(nowMs - lastTimeMs, 0)
  lastTimeMs = nowMs

  updateAdaptiveTint(renderer, container, nowMs, deltaMs)
  renderer.render()

  requestAnimationFrame(frame)
}

requestAnimationFrame(frame)
```

## Choosing the mapping

The mapping from luminance to tint brightness is the main artistic control.

The example above uses:

```ts
const normalized = smoothstep(0.08, 0.92, luminance)
const mappedBrightness = 0.1 + normalized * 0.75
```

This does three useful things:

- ignores the very darkest and brightest extremes
- gives a soft S-curve instead of a hard linear response
- constrains the output range so tint stays stylized rather than becoming pure black or pure white

You should tune these values for your own look.

For example:

- lower the minimum from `0.1` if you want darker glass on dark backdrops
- increase the output range if you want more aggressive adaptation
- narrow the `smoothstep` domain if you want tint to react sooner

## Debounce strategy

The debounce step matters because backdrop metrics often move continuously:

- scroll updates
- selection changes
- animated HTML content
- asynchronous image loading
- minor subpixel sampling differences

If you immediately switch the target on every new sample, tint may feel noisy. A better pattern is:

1. observe the newly derived target brightness
2. only treat it as meaningful if it differs by more than some epsilon
3. wait for a short delay before promoting it to the active target

That is what `pendingBrightness`, `observedBrightness`, and `settleAtMs` are doing in the example.

## Easing strategy

The example uses exponential smoothing:

```ts
const blend = 1 - Math.exp(-deltaMs / easingDurationMs)
current += (target - current) * blend
```

This works well because it is frame-rate independent and stable under varying `deltaMs`.

Interpretation:

- smaller `easingDurationMs` means faster response
- larger `easingDurationMs` means slower response

Typical starting values:

- `easingDurationMs = 500`
- `easingDelayMs = 300`

## Tint alpha

The example only adapts tint brightness. Alpha stays fixed:

```ts
container.tint = {
  r: brightness,
  g: brightness,
  b: brightness,
  a: fixedAlpha,
}
```

That is the safest place to start.

If you want alpha adaptation as well, you can derive it from the same metrics. For example:

- increase alpha when `luminanceP90 - luminanceP10` is small, meaning the backdrop is low contrast
- decrease alpha when backdrop contrast is high, so the content reads through more clearly

That policy is application-specific, so it is better left outside the renderer.

## Multiple containers

For multiple tracked containers, keep one adaptive state object per container:

```ts
const adaptiveTintStates = new WeakMap<Container, AdaptiveTintState>()
```

Then in your update loop:

```ts
for (const container of trackedContainers) {
  updateAdaptiveTint(renderer, container, nowMs, deltaMs)
}
```

Each container should debounce and ease independently because their backdrops may change differently.

## Important notes

- `getBackdropMetrics(container)` may return `null` if tracking is disabled, the container is not currently in scene, or no completed metrics sample is available yet.
- The metrics are cached renderer outputs, not synchronous measurements on demand.
- The renderer should stay policy-light. Treat the metrics as raw inputs for your own tint logic rather than expecting the renderer to decide the final look.

## Minimal example

If you want the smallest useful version, this is enough:

```ts
renderer.setBackdropMetricsTracking(container, true)

let current = 0.2
let target = 0.2

function frame(nowMs: number) {
  const metrics = renderer.getBackdropMetrics(container)

  if (metrics) {
    target = targetTintBrightness(metrics)
  }

  current += (target - current) * 0.1

  container.tint = {
    r: current,
    g: current,
    b: current,
    a: 0.7,
  }

  renderer.render()
  requestAnimationFrame(frame)
}

requestAnimationFrame(frame)
```

That version skips debounce and uses a fixed blend factor, but it is enough to prove out the idea before refining it.
