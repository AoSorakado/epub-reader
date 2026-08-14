import { CatmullRomCurve3, ExtrudeGeometry, Shape, TubeGeometry, Vector3 } from 'three'
import { TextGeometry } from 'three/examples/jsm/geometries/TextGeometry.js'
import type { Font } from 'three/examples/jsm/loaders/FontLoader.js'
import {
  GRID_COLUMNS,
  GRID_ROWS,
  LISSAJOUS_HEIGHT_RATIO,
  LISSAJOUS_TUBE_RADIUS,
  LISSAJOUS_WIDTH_RATIO,
  TILE_BEVEL_SIZE,
  TILE_BEVEL_THICKNESS,
  TILE_CORNER_RADIUS,
  TILE_DEPTH,
  TILE_PANEL_CORNER_RADIUS,
  TITLE_DEPTH,
  TITLE_SIZE,
} from '../config'
import type { CornerRadii, CornerRadiiInput } from '../types'

export function tileCornerRadii(tileIndex: number): CornerRadii {
  const column = tileIndex % GRID_COLUMNS
  const row = Math.floor(tileIndex / GRID_COLUMNS)

  return {
    topLeft: row === 0 && column === 0 ? TILE_PANEL_CORNER_RADIUS : TILE_CORNER_RADIUS,
    topRight: row === 0 && column === GRID_COLUMNS - 1 ? TILE_PANEL_CORNER_RADIUS : TILE_CORNER_RADIUS,
    bottomRight: row === GRID_ROWS - 1 && column === GRID_COLUMNS - 1 ? TILE_PANEL_CORNER_RADIUS : TILE_CORNER_RADIUS,
    bottomLeft: row === GRID_ROWS - 1 && column === 0 ? TILE_PANEL_CORNER_RADIUS : TILE_CORNER_RADIUS,
  }
}

export function createTileGeometry(
  width: number,
  height: number,
  cornerRadii: CornerRadiiInput = TILE_CORNER_RADIUS,
) {
  const halfWidth = width * 0.5
  const halfHeight = height * 0.5
  const radii = normalizeCornerRadii(width, height, cornerRadii)
  const smallestRadius = Math.min(radii.topLeft, radii.topRight, radii.bottomRight, radii.bottomLeft)
  const depth = TILE_DEPTH
  const shape = new Shape()

  shape.moveTo(-halfWidth + radii.bottomLeft, -halfHeight)
  shape.lineTo(halfWidth - radii.bottomRight, -halfHeight)
  shape.quadraticCurveTo(halfWidth, -halfHeight, halfWidth, -halfHeight + radii.bottomRight)
  shape.lineTo(halfWidth, halfHeight - radii.topRight)
  shape.quadraticCurveTo(halfWidth, halfHeight, halfWidth - radii.topRight, halfHeight)
  shape.lineTo(-halfWidth + radii.topLeft, halfHeight)
  shape.quadraticCurveTo(-halfWidth, halfHeight, -halfWidth, halfHeight - radii.topLeft)
  shape.lineTo(-halfWidth, -halfHeight + radii.bottomLeft)
  shape.quadraticCurveTo(-halfWidth, -halfHeight, -halfWidth + radii.bottomLeft, -halfHeight)
  shape.closePath()

  const geometry = new ExtrudeGeometry(shape, {
    depth,
    bevelEnabled: true,
    bevelSegments: 14,
    bevelSize: Math.min(TILE_BEVEL_SIZE, smallestRadius * 0.35),
    bevelThickness: Math.min(TILE_BEVEL_THICKNESS, smallestRadius * 0.3),
    curveSegments: 20,
    steps: 1,
  })
  geometry.translate(0, 0, -depth * 0.5)
  geometry.computeVertexNormals()
  return geometry
}

export function createTitleGeometry(text: string, font: Font) {
  const geometry = new TextGeometry(text, {
    font,
    size: TITLE_SIZE,
    depth: TITLE_DEPTH,
    curveSegments: 10,
    bevelEnabled: true,
    bevelSegments: 3,
    bevelSize: 0.8,
    bevelThickness: 0.8,
  })

  geometry.computeBoundingBox()
  const box = geometry.boundingBox
  if (!box) {
    throw new Error('Unable to measure title geometry.')
  }

  const width = box.max.x - box.min.x
  const height = box.max.y - box.min.y
  geometry.translate(-box.min.x - width * 0.5, -box.min.y - height * 0.5, -TITLE_DEPTH * 0.5)
  geometry.computeVertexNormals()

  return { geometry, size: { width, height } }
}

export function createLissajousGeometry(size: number, phase: number) {
  const points: Vector3[] = []
  const pointCount = 160
  const radiusX = size * LISSAJOUS_WIDTH_RATIO * 0.5
  const radiusY = size * LISSAJOUS_HEIGHT_RATIO * 0.5

  for (let index = 0; index < pointCount; index += 1) {
    const t = (index / pointCount) * Math.PI * 2
    points.push(new Vector3(
      Math.sin(3 * t + phase) * radiusX,
      Math.sin(2 * t) * radiusY,
      0,
    ))
  }

  const curve = new CatmullRomCurve3(points, true, 'centripetal')
  const geometry = new TubeGeometry(curve, pointCount, LISSAJOUS_TUBE_RADIUS, 8, true)
  geometry.computeVertexNormals()
  return geometry
}

function normalizeCornerRadii(width: number, height: number, input: CornerRadiiInput): CornerRadii {
  const halfWidth = width * 0.5
  const halfHeight = height * 0.5
  const radii = typeof input === 'number'
    ? {
        topLeft: input,
        topRight: input,
        bottomRight: input,
        bottomLeft: input,
      }
    : input

  return {
    topLeft: Math.min(radii.topLeft, halfWidth, halfHeight),
    topRight: Math.min(radii.topRight, halfWidth, halfHeight),
    bottomRight: Math.min(radii.bottomRight, halfWidth, halfHeight),
    bottomLeft: Math.min(radii.bottomLeft, halfWidth, halfHeight),
  }
}
