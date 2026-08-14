---
created: 2026-08-14T14:38:05 (UTC +08:00)
tags: []
source: https://kyant.gitbook.io/backdrop/tutorials/glass-bottom-sheet
author: 
---

# Glass Bottom Sheet

> ## Excerpt
> Create a glass bottom sheet

---
## Goals[](https://kyant.gitbook.io/backdrop/tutorials/glass-bottom-sheet#goals)

-   Create a glass bottom sheet based on the code:
    

## What you will learn[](https://kyant.gitbook.io/backdrop/tutorials/glass-bottom-sheet#what-you-will-learn)

-   Handle the case of "glass on glass"
    
-   Make use of `exportedBackdrop` parameter of the `drawBackdrop` modifier
    

## Steps[](https://kyant.gitbook.io/backdrop/tutorials/glass-bottom-sheet#steps)

1

### Create a GlassBottomSheet[](https://kyant.gitbook.io/backdrop/tutorials/glass-bottom-sheet#create-a-glassbottomsheet)

The backdrop for the glass button is `backdrop`, but we want to include the bottom sheet.

2

### Use the bottom sheet as a backdrop for the glass button (WRONG code)[](https://kyant.gitbook.io/backdrop/tutorials/glass-bottom-sheet#use-the-bottom-sheet-as-a-backdrop-for-the-glass-button-wrong-code)

The WRONG idea is to set a new LayerBackdrop after `drawBackdrop` .

You will get a crash:

Fatal signal 11 (SIGSEGV), code 2 (SEGV\_ACCERR), fault addr 0x\_\_ in tid \_\_ (RenderThread), pid \_\_

Because the `layerBackdrop` modifier will draw the content to the `bottomSheetBackdrop`, and the content will draw the `bottomSheetBackdrop`, **it's a loop**!

3

### Use the bottom sheet as a backdrop for the glass button (CORRECT code)[](https://kyant.gitbook.io/backdrop/tutorials/glass-bottom-sheet#use-the-bottom-sheet-as-a-backdrop-for-the-glass-button-correct-code)

Use `exportedBackdrop` in `drawBackdrop` modifier, **it will skip drawing the content**.

## Final code[](https://kyant.gitbook.io/backdrop/tutorials/glass-bottom-sheet#final-code)

Final code[](https://kyant.gitbook.io/backdrop/tutorials/glass-bottom-sheet#final-code-1)
