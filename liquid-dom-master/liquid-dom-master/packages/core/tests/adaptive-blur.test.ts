import { describe, expect, it } from 'vitest'
import { chooseAdaptiveBlurLevel } from '../src/renderer/adaptive-blur'

describe('adaptive blur level selection', () => {
  it('skips blur for zero radius', () => {
    expect(chooseAdaptiveBlurLevel(0, 4)).toEqual({
      skip: true,
      level: 0,
      scale: 1,
      effectiveRadius: 0,
    })
  })

  it('keeps a 6px radius at full resolution', () => {
    expect(chooseAdaptiveBlurLevel(6, 4)).toEqual({
      skip: false,
      level: 0,
      scale: 1,
      effectiveRadius: 6,
    })
  })

  it('moves a 12px radius to the first downsample level', () => {
    expect(chooseAdaptiveBlurLevel(12, 4)).toEqual({
      skip: false,
      level: 1,
      scale: 2,
      effectiveRadius: 6,
    })
  })

  it('clamps very large radii to the available chain', () => {
    expect(chooseAdaptiveBlurLevel(4096, 3)).toEqual({
      skip: false,
      level: 3,
      scale: 8,
      effectiveRadius: 512,
    })
  })

  it('treats non-finite radii as a skipped blur', () => {
    expect(chooseAdaptiveBlurLevel(Number.NaN, 4)).toEqual({
      skip: true,
      level: 0,
      scale: 1,
      effectiveRadius: 0,
    })
  })
})
