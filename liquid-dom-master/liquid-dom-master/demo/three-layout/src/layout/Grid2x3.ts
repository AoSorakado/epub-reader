import { Layout } from '@liquid-dom/layout'
import type { LayoutChild, LayoutMeasureInput, LayoutPlaceInput, Size } from '@liquid-dom/layout'
import type { GridLayoutProps } from '../types'

export class Grid2x3 extends Layout {
  private _props: GridLayoutProps

  constructor(props: GridLayoutProps) {
    super('grid-2x3')
    this._props = props
  }

  get props(): GridLayoutProps {
    return this._props
  }

  set props(value: GridLayoutProps) {
    if (Object.is(this._props, value)) return
    this._props = value
    this.markMeasureDirty('props')
  }

  override measureSelf({ children }: LayoutMeasureInput): Size {
    const { columnWidths, rowHeights } = measureGridTracks(children, this._props)
    return gridSize(columnWidths, rowHeights, this._props)
  }

  override placeChildren({ bounds, children }: LayoutPlaceInput): void {
    const { sizes, columnWidths, rowHeights } = measureGridTracks(children, this._props)
    let y = bounds.y

    for (let row = 0; row < this._props.rows; row += 1) {
      let x = bounds.x
      const rowHeight = rowHeights[row] ?? 0

      for (let column = 0; column < this._props.columns; column += 1) {
        const index = row * this._props.columns + column
        const child = children[index]
        const size = sizes[index]
        const columnWidth = columnWidths[column] ?? 0

        if (child && size) {
          child.place({
            x: x + (columnWidth - size.width) * 0.5,
            y: y + (rowHeight - size.height) * 0.5,
            width: size.width,
            height: size.height,
          }, size)
        }

        x += columnWidth + this._props.columnGap
      }

      y += rowHeight + this._props.rowGap
    }
  }
}

function measureGridTracks(children: LayoutChild[], props: GridLayoutProps) {
  const sizes = children.map((child) => child.measure({}))
  const columnWidths = Array.from({ length: props.columns }, () => 0)
  const rowHeights = Array.from({ length: props.rows }, () => 0)

  for (const [index, size] of sizes.entries()) {
    const column = index % props.columns
    const row = Math.floor(index / props.columns)
    if (row >= props.rows) continue

    columnWidths[column] = Math.max(columnWidths[column] ?? 0, size.width)
    rowHeights[row] = Math.max(rowHeights[row] ?? 0, size.height)
  }

  return { sizes, columnWidths, rowHeights }
}

function sum(values: number[]) {
  return values.reduce((total, value) => total + value, 0)
}

function gridSize(columnWidths: number[], rowHeights: number[], props: GridLayoutProps): Size {
  return {
    width: sum(columnWidths) + props.columnGap * Math.max(0, props.columns - 1),
    height: sum(rowHeights) + props.rowGap * Math.max(0, props.rows - 1),
  }
}
