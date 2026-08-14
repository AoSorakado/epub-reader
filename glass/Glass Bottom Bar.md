---
created: 2026-08-14T14:37:05 (UTC +08:00)
tags: []
source: https://kyant.gitbook.io/backdrop/tutorials/glass-bottom-bar
author: 
---

# Glass Bottom Bar

> ## Excerpt
> Create a glass bottom bar

---
## Goals[](https://kyant.gitbook.io/backdrop/tutorials/glass-bottom-bar#goals)

-   Create a glass bottom bar over the `MainNavHost`.
    

Here is what `MainNavHost` looks like:

## What you will learn[](https://kyant.gitbook.io/backdrop/tutorials/glass-bottom-bar#what-you-will-learn)

-   Create and draw backdrops
    
-   Apply effects to the backdrops
    
-   Handle the background drawing correctly
    
-   Ensure the readability
    

## Steps[](https://kyant.gitbook.io/backdrop/tutorials/glass-bottom-bar#steps)

1

### Draw backdrop and add lens effect[](https://kyant.gitbook.io/backdrop/tutorials/glass-bottom-bar#draw-backdrop-and-add-lens-effect)

Noooo! The effect is wrong! There are transparent pixels in the bottom bar. Because we only draw the `MainNavHost` , **the background outside of** `MainNavHost` **should be drawn too**.

2

### Draw the background to the backdrop (optional)[](https://kyant.gitbook.io/backdrop/tutorials/glass-bottom-bar#draw-the-background-to-the-backdrop-optional)

Nice work!

Try to adjust the lens effect and observe what will happen.

3

### Add blur effect[](https://kyant.gitbook.io/backdrop/tutorials/glass-bottom-bar#add-blur-effect)

The use of `vibrancy()` enhances the saturation, giving us more visual impact.

4

### Add surface for readability[](https://kyant.gitbook.io/backdrop/tutorials/glass-bottom-bar#add-surface-for-readability)

The readability has increased. **You must balance between beauty and readability.**

5

### Final code[](https://kyant.gitbook.io/backdrop/tutorials/glass-bottom-bar#final-code)

## Exercise: Add a tinted glass icon button[](https://kyant.gitbook.io/backdrop/tutorials/glass-bottom-bar#exercise-add-a-tinted-glass-icon-button)

Final code[](https://kyant.gitbook.io/backdrop/tutorials/glass-bottom-bar#final-code-1)

It is recommended to use `BlendMode.Hue` , so that the hue of backdrop will adapt to the tint color.
