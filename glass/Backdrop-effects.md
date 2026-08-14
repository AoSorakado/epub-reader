---
created: 2026-08-14T14:39:43 (UTC +08:00)
tags: []
source: https://kyant.gitbook.io/backdrop/api/backdrop-effects
author: 
---

# Backdrop effects

> ## Excerpt
> Backdrop effects are RenderEffects radically. They only take effect with Android 12 and above. Some effects involving with RuntimeShader need Android 13 and above.

---
Backdrop effects are `RenderEffect`s radically. They only take effect with Android 12 and above. Some effects involving with `RuntimeShader` need Android 13 and above.

The order of effects matters. To create the right visual effects, you must apply them with the following order:

color filter ⇒ blur ⇒ lens

## Color filter[](https://kyant.gitbook.io/backdrop/api/backdrop-effects#color-filter)

### Custom ColorFilter[](https://kyant.gitbook.io/backdrop/api/backdrop-effects#custom-colorfilter)

```
colorFilter(colorFilter: ColorFilter)
```

### Opacity[](https://kyant.gitbook.io/backdrop/api/backdrop-effects#opacity)

```
opacity(alpha: Float)
```

### Color controls (brightness, contrast, saturation)[](https://kyant.gitbook.io/backdrop/api/backdrop-effects#color-controls-brightness-contrast-saturation)

```
colorControls(
    brightness: Float = 0f,
    contrast: Float = 1f,
    saturation: Float = 1f
)
```

![](backdrop-effects/image.png)

### Vibrancy[](https://kyant.gitbook.io/backdrop/api/backdrop-effects#vibrancy)

Multiply saturation with 1.5. It is equivalent to `colorControls(saturation = 1.5f)` .

```
vibrancy()
```

### Exposure adjustment[](https://kyant.gitbook.io/backdrop/api/backdrop-effects#exposure-adjustment)

```
exposureAdjustment(ev: Float)
```

### Gamma adjustment (Android 13+)[](https://kyant.gitbook.io/backdrop/api/backdrop-effects#gamma-adjustment-android-13)

```
gammaAdjustment(power: Float)
```

## Blur[](https://kyant.gitbook.io/backdrop/api/backdrop-effects#blur)

### Blur effect[](https://kyant.gitbook.io/backdrop/api/backdrop-effects#blur-effect)

```
blur(
    radius: Float,
    edgeTreatment: TileMode = TileMode.Clamp
)
```

## Lens (Android 13+)[](https://kyant.gitbook.io/backdrop/api/backdrop-effects#lens-android-13)

⚠️ To use the lens effect, your `shape` must be `CornerBasedShape` .

### Lens effect[](https://kyant.gitbook.io/backdrop/api/backdrop-effects#lens-effect)

```
lens(
    refractionHeight: Float,
    refractionAmount: Float = height,
    depthEffect: Boolean = false,
    chromaticAberration: Boolean = false
)
```

-   `height` must be in \[0, `shape.minCornerRadius` \]. If it exceeds, it will have discontinuities at some corners, but it's acceptable.
    
-   `amount` must be in \[0, `size.minDimension` \].
    

![](backdrop-effects/image.1.png)

![](backdrop-effects/image.2.png)

## RenderEffect[](https://kyant.gitbook.io/backdrop/api/backdrop-effects#rendereffect)

### Custom RenderRffect[](https://kyant.gitbook.io/backdrop/api/backdrop-effects#custom-renderrffect)

```
effect(effect: RenderEffect)
```

```
runtimeShaderEffect(
    key: String,
    shaderString: String,
    uniformShaderName: String,
    block: RuntimeShader.() -> Unit
)
```
