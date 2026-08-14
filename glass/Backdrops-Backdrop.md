---
created: 2026-08-14T14:39:17 (UTC +08:00)
tags: []
source: https://kyant.gitbook.io/backdrop/api/backdrops
author: 
---

# Backdrops | Backdrop

> ## Excerpt
> rememberBackdrop can draw a backdrop with custom commands.

---
## Backdrop[](https://kyant.gitbook.io/backdrop/api/backdrops#backdrop)

`rememberBackdrop` can draw a backdrop with custom commands.

## Layer backdrop[](https://kyant.gitbook.io/backdrop/api/backdrops#layer-backdrop)

`rememberLayerBackdrop` must be used with `Modifier.layerBackdrop` to draw the Composable's content, or it's derived by using `exportedBackdrop` parameter of the `drawBackdrop` modifier. It is coordinates-dependent.

## Combined backdrop[](https://kyant.gitbook.io/backdrop/api/backdrops#combined-backdrop)

`rememberCombinedBackdrop` can merge multiple backdrops into one backdrop. It is useful to create components such as tabs and sliders.

## Canvas backdrop[](https://kyant.gitbook.io/backdrop/api/backdrops#canvas-backdrop)

`rememberCanvasBackdrop` can draw custom content to a empty backdrop. It is coordinates-independent.

## Empty backdrop[](https://kyant.gitbook.io/backdrop/api/backdrops#empty-backdrop)

`emptyBackdrop` draws nothing.
