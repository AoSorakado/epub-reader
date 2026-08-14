# @liquid-dom/three

## Description

`@liquid-dom/three` hosts the reusable liquid-glass WebGPU core inside Three's WebGPU renderer. It lets you render a Three scene into a backdrop texture, then composite liquid glass over that backdrop into the canvas or another output texture.

## Install

```sh
pnpm add @liquid-dom/three @liquid-dom/core three
```

## Quick Start

```ts
import * as THREE from 'three/webgpu'
import { Container, Glass, Scene } from '@liquid-dom/core'
import { ThreeGlassRenderer } from '@liquid-dom/three'

const canvas = document.querySelector('canvas')!
const renderer = new THREE.WebGPURenderer({ canvas })
await renderer.init()

const threeScene = new THREE.Scene()
const camera = new THREE.PerspectiveCamera(60, 1, 0.1, 100)
const backdrop = new THREE.RenderTarget(1, 1, {
  colorSpace: THREE.SRGBColorSpace,
})

const glassScene = new Scene()
const container = new Container({ blur: 12, spacing: 28 })
container.add(new Glass({
  x: 120,
  y: 120,
  width: 280,
  height: 160,
  cornerRadius: 44,
}))
glassScene.add(container)

const glassRenderer = new ThreeGlassRenderer({
  renderer,
  scene: glassScene,
})

const drawingSize = new THREE.Vector2()

function frame() {
  renderer.getDrawingBufferSize(drawingSize)
  backdrop.setSize(drawingSize.x, drawingSize.y)

  renderer.setRenderTarget(backdrop)
  renderer.render(threeScene, camera)
  renderer.setRenderTarget(null)

  glassRenderer.render({
    backdrop,
    width: drawingSize.x,
    height: drawingSize.y,
  })

  requestAnimationFrame(frame)
}
frame()
```

## API Overview

### `ThreeGlassRenderer`

```ts
const glass = new ThreeGlassRenderer({
  renderer,
  scene,
  format,
  contentSource,
})

glass.render({
  scene,
  backdrop,
  contentSource,
  outputTexture,
  width,
  height,
  dpr,
})

glass.destroy()
```

Constructor options:

- `renderer`: initialized Three WebGPU renderer.
- `scene`: optional `@liquid-dom/core` scene. Defaults to a new empty scene.
- `format`: optional output `GPUTextureFormat` override.
- `contentSource`: optional DOM or custom content source for glass HTML.

Instance API:

| API | Description |
| --- | --- |
| `scene` | The default `@liquid-dom/core` scene used by `render()`. |
| `device` | Three WebGPU backend device. |
| `format` | Output format override, Three backend preferred format, or `navigator.gpu.getPreferredCanvasFormat()`. |
| `getGpuTexture(backdrop)` | Resolves a `GPUTexture` from a `GPUTexture`, Three `RenderTarget`, or Three `Texture`. |
| `render(options)` | Composites liquid glass over a backdrop. |
| `destroy()` | Releases GPU resources owned by the adapter. |

Render options:

- `backdrop`: `GPUTexture`, Three `RenderTarget`, or Three `Texture` containing the already-rendered background.
- `scene`: optional scene override for this render call.
- `contentSource`: optional content source override.
- `outputTexture`: optional output `GPUTexture`. Defaults to the current canvas texture.
- `width`, `height`: output dimensions in device pixels. Defaults to Three's drawing buffer size.
- `dpr`: CSS-to-device pixel ratio. Defaults to Three's pixel ratio.

Public types:

| Type | Fields |
| --- | --- |
| `ThreeWebGpuBackend` | `isWebGPUBackend`, `device`, `utils.getPreferredCanvasFormat`, `get(object)` |
| `ThreeWebGpuBackendReady` | `ThreeWebGpuBackend` with `isWebGPUBackend: true` and `device` |
| `ThreeWebGpuRenderer` | `backend`, `getContext()`, `getDrawingBufferSize(target)`, `getPixelRatio()` |
| `ThreeWebGpuRenderTargetRenderer` | `ThreeWebGpuRenderer` plus `domElement`, `render(scene, camera)`, `setRenderTarget(target)` |
| `ThreeWebGpuRendererValidationOptions` | `owner?: string`, `help?: string` |
| `ThreeGlassRendererInit` | Constructor options listed above. |
| `ThreeGlassRenderOptions` | Render options listed above. |

### Validation Helpers

```ts
import {
  requireThreeWebGpuBackend,
  requireThreeWebGpuCanvasContext,
  requireThreeWebGpuRenderer,
  requireThreeWebGpuRenderTargetRenderer,
} from '@liquid-dom/three'
```

These helpers validate that a renderer is backed by Three's WebGPU backend and exposes the methods needed by each integration layer. They throw explicit errors with optional `owner` and `help` text.

| Helper | Returns |
| --- | --- |
| `requireThreeWebGpuBackend(renderer, options?)` | `ThreeWebGpuBackendReady` |
| `requireThreeWebGpuRenderer(renderer, options?)` | `ThreeWebGpuRenderer` |
| `requireThreeWebGpuCanvasContext(renderer, options?)` | `GPUCanvasContext` |
| `requireThreeWebGpuRenderTargetRenderer(renderer, options?)` | `ThreeWebGpuRenderTargetRenderer` |

### Backdrop And Output Flow

The adapter does not render your Three scene for you. Render the Three scene into a target, then pass that target as `backdrop`. The liquid-glass pass samples the backdrop and writes the composited result to `outputTexture` or the canvas current texture.

Call `destroy()` when the adapter is no longer needed so GPU resources owned by the liquid-glass core are released.

## Integration Notes

- This package only supports Three's WebGPU renderer.
- `WebGLRenderer` is not supported.
- A Three `Texture` or `RenderTarget` must have an initialized backend GPU texture before it can be used as `backdrop`; render to it first if needed.
- Use `WebGpuDomContentSource` from `@liquid-dom/core` when the liquid-glass scene includes DOM-backed `Html` content.
- DOM-backed `Html` content requires the experimental HTML-in-Canvas API, currently available only behind Chrome's Canvas Draw Element flag: `chrome://flags/#canvas-draw-element`.
- Reference: [WICG HTML-in-Canvas](https://wicg.github.io/html-in-canvas/).

## Local Development

```sh
pnpm --filter @liquid-dom/three build
pnpm --filter @liquid-dom/three watch
```
