import type { Rect } from '@liquid-dom/layout'
import type { RenderRect } from '../types'

export function renderRectForLayoutRect(rect: Rect, rootRect: Rect): RenderRect {
  const rootCenterX = rootRect.x + rootRect.width * 0.5
  const rootCenterY = rootRect.y + rootRect.height * 0.5

  return {
    x: rect.x + rect.width * 0.5 - rootCenterX,
    y: rootCenterY - (rect.y + rect.height * 0.5),
    width: rect.width,
    height: rect.height,
  }
}
