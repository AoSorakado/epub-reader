import type { Size } from '@liquid-dom/layout'
import {
  COLUMN_GAP,
  GRID_COLUMNS,
  GRID_ROWS,
  HOVER_SCALE,
  NON_HOVER_SCALE,
  ROW_GAP,
  TILE_SIZE,
} from '../config'
import type { GridLayoutProps, HoverTarget } from '../types'

export const layoutState = {
  tileSize: TILE_SIZE,
  columnGap: COLUMN_GAP,
  rowGap: ROW_GAP,
  hoveredTile: null as HoverTarget | null,
}

export function tileSizeFor(panelIndex: number, tileIndex: number): Size {
  const hoveredTile = layoutState.hoveredTile?.panelIndex === panelIndex ? layoutState.hoveredTile.tileIndex : null
  const hoveredColumn = hoveredTile === null ? null : hoveredTile % GRID_COLUMNS
  const hoveredRow = hoveredTile === null ? null : Math.floor(hoveredTile / GRID_COLUMNS)
  const column = tileIndex % GRID_COLUMNS
  const row = Math.floor(tileIndex / GRID_COLUMNS)
  const widthScale = hoveredTile === null
    ? 1
    : hoveredColumn === column
      ? HOVER_SCALE
      : NON_HOVER_SCALE
  const heightScale = hoveredTile === null
    ? 1
    : hoveredRow === row
      ? HOVER_SCALE
      : NON_HOVER_SCALE

  return {
    width: layoutState.tileSize * widthScale,
    height: layoutState.tileSize * heightScale,
  }
}

export function tileMeasureKey(panelIndex: number, tileIndex: number) {
  const size = tileSizeFor(panelIndex, tileIndex)
  return `${size.width}:${size.height}`
}

export function gridProps(): GridLayoutProps {
  return {
    columns: GRID_COLUMNS,
    rows: GRID_ROWS,
    columnGap: layoutState.columnGap,
    rowGap: layoutState.rowGap,
  }
}

export function sameHoverTarget(a: HoverTarget | null, b: HoverTarget | null) {
  return a?.panelIndex === b?.panelIndex && a?.tileIndex === b?.tileIndex
}
