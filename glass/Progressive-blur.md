---
created: 2026-08-14T14:39:01 (UTC +08:00)
tags: []
source: https://kyant.gitbook.io/backdrop/tutorials/progressive-blur
author: 
---

# Progressive blur

> ## Excerpt
> Create progressive blur effect

---
```
Modifier.drawPlainBackdrop(
    backdrop = backdrop,
    shape = { RectangleShape },
    effects = {
        blur(4f.dp.toPx())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            effect(
                RenderEffect.createRuntimeShaderEffect(
                    obtainRuntimeShader(
                        "AlphaMask",
                        """
uniform shader content;

uniform float2 size;
layout(color) uniform half4 tint;
uniform float tintIntensity;

half4 main(float2 coord) {
    float blurAlpha = smoothstep(size.y, size.y * 0.5, coord.y);
    float tintAlpha = smoothstep(size.y, size.y * 0.5, coord.y);
    return mix(content.eval(coord) * blurAlpha, tint * tintAlpha, tintIntensity);
}"""
                    ).apply {
                        setFloatUniform("size", size.width, size.height)
                        setColorUniform("tint", tintColor.toArgb())
                        setFloatUniform("tintIntensity", 0.8f)
                    },
                    "content"
                )
            )
        }
    }
)
```
