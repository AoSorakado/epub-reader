import { describe, expect, it } from 'vitest'
import { Container } from '../src/scene'
import { resolveSpecularWidthPx } from '../src/renderer'

describe('specular width', () => {
  it('defaults containers to a 1px CSS specular width', () => {
    expect(new Container().specularWidth).toBe(1)
  })

  it('resolves hairline to one device pixel at any DPR', () => {
    expect(resolveSpecularWidthPx('hairline', 1)).toBe(1)
    expect(resolveSpecularWidthPx('hairline', 2)).toBe(1)
    expect(resolveSpecularWidthPx('hairline', 3)).toBe(1)
  })

  it('keeps numeric widths in CSS pixels before DPR scaling', () => {
    expect(resolveSpecularWidthPx(0.5, 1)).toBe(0.5)
    expect(resolveSpecularWidthPx(0.5, 2)).toBe(1)
    expect(resolveSpecularWidthPx(0.5, 3)).toBe(1.5)
  })
})
