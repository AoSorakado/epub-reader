---
created: 2026-08-14T14:37:41 (UTC +08:00)
tags: []
source: https://kyant.gitbook.io/backdrop/tutorials/interactive-glass-bottom-bar
author: 
---

# Interactive Glass Bottom Bar

> ## Excerpt
> Add interactive feedbacks for the glass bottom bar

---
## Goals[](https://kyant.gitbook.io/backdrop/tutorials/interactive-glass-bottom-bar#goals)

-   Add "press to scale" animation to the glass bottom bar
    

## What you will learn[](https://kyant.gitbook.io/backdrop/tutorials/interactive-glass-bottom-bar#what-you-will-learn)

-   Handle the transformations (scale, rotation) correctly
    
-   Make use of `layerBlock` parameter of the `drawBackdrop` modifier
    

## Steps[](https://kyant.gitbook.io/backdrop/tutorials/interactive-glass-bottom-bar#steps)

1

### Press to scale[](https://kyant.gitbook.io/backdrop/tutorials/interactive-glass-bottom-bar#press-to-scale)

Update the code for the glass bottom bar.

Press the bottom bar.

Oops! The backdrop is misplaced, it will also scale with the bottom bar. **But the backdrop shouldn't scale**.

2

### Prevent backdrop from scaling[](https://kyant.gitbook.io/backdrop/tutorials/interactive-glass-bottom-bar#prevent-backdrop-from-scaling)

Move the code in `graphicsLayer` to `layerBlock` in the `drawBackdrop` modifier.

Press the bottom bar again.

It works correctly! The content will scale but the backdrop won't scale.

## Final code[](https://kyant.gitbook.io/backdrop/tutorials/interactive-glass-bottom-bar#final-code)

Final code[](https://kyant.gitbook.io/backdrop/tutorials/interactive-glass-bottom-bar#final-code-1)
