import { Mesh } from 'three'
import type { MeshBasicMaterial, PlaneGeometry } from 'three'
import { GRID_COLUMNS, GRID_ROWS, HIT_PROXY_Z, LISSAJOUS_Z } from '../config'
import { layoutState, tileMeasureKey, tileSizeFor } from '../layout/layoutState'
import { createLissajousGeometry, createTileGeometry, tileCornerRadii } from '../three/geometry'
import { createLissajousMaterial, createTileMaterial } from '../three/materials'
import type { RenderRect, TileView } from '../types'
import { MeasuredLeaf } from '../layout/MeasuredLeaf'
import { applyRectMesh } from './rectMesh'

export function createTileView(
  panelIndex: number,
  tileIndex: number,
  color: number,
  hitProxyGeometry: PlaneGeometry,
  hitProxyMaterial: MeshBasicMaterial,
): TileView {
  const node = new MeasuredLeaf(
    () => tileSizeFor(panelIndex, tileIndex),
    {
      measureKey: tileMeasureKey(panelIndex, tileIndex),
    },
  )
  const geometrySize = tileSizeFor(panelIndex, tileIndex)
  const mesh = new Mesh(
    createTileGeometry(geometrySize.width, geometrySize.height, tileCornerRadii(tileIndex)),
    createTileMaterial(color),
  )
  const hitMesh = new Mesh(hitProxyGeometry, hitProxyMaterial)
  hitMesh.position.z = HIT_PROXY_Z
  const curveGeometrySize = lissajousSizeForRect(geometrySize)
  const curveMesh = new Mesh(
    createLissajousGeometry(curveGeometrySize, lissajousPhase(tileIndex)),
    createLissajousMaterial(),
  )
  curveMesh.position.z = LISSAJOUS_Z

  return {
    node,
    mesh,
    hitMesh,
    curveMesh,
    panelIndex,
    tileIndex,
    geometryWidth: geometrySize.width,
    geometryHeight: geometrySize.height,
    curveGeometrySize,
    currentRect: null,
    targetRect: null,
    currentZ: null,
    targetZ: null,
    currentCurveZ: null,
    targetCurveZ: null,
    currentCurveScale: null,
    targetCurveScale: null,
    currentCurveRotationY: null,
    targetCurveRotationY: null,
  }
}

export function applyTileRect(tile: TileView, rect: RenderRect) {
  applyRectMesh(tile, rect, tileCornerRadii(tile.tileIndex))
  tile.mesh.position.z = tile.currentZ ?? 0
  applyLissajousRect(tile, rect)

  const column = tile.tileIndex % GRID_COLUMNS
  const row = Math.floor(tile.tileIndex / GRID_COLUMNS)
  const leftMargin = column === 0 ? 0 : layoutState.columnGap * 0.5
  const rightMargin = column === GRID_COLUMNS - 1 ? 0 : layoutState.columnGap * 0.5
  const topMargin = row === 0 ? 0 : layoutState.rowGap * 0.5
  const bottomMargin = row === GRID_ROWS - 1 ? 0 : layoutState.rowGap * 0.5
  const proxyLeft = rect.x - rect.width * 0.5 - leftMargin
  const proxyRight = rect.x + rect.width * 0.5 + rightMargin
  const proxyTop = rect.y + rect.height * 0.5 + topMargin
  const proxyBottom = rect.y - rect.height * 0.5 - bottomMargin

  tile.hitMesh.position.x = (proxyLeft + proxyRight) * 0.5
  tile.hitMesh.position.y = (proxyTop + proxyBottom) * 0.5
  tile.hitMesh.scale.set(proxyRight - proxyLeft, proxyTop - proxyBottom, 1)
}

function applyLissajousRect(tile: TileView, rect: RenderRect) {
  const curveGeometrySize = lissajousSizeForRect(rect)

  if (Math.abs(tile.curveGeometrySize - curveGeometrySize) > 0.1) {
    tile.curveMesh.geometry.dispose()
    tile.curveMesh.geometry = createLissajousGeometry(curveGeometrySize, lissajousPhase(tile.tileIndex))
    tile.curveGeometrySize = curveGeometrySize
  }

  const scale = tile.currentCurveScale ?? 1
  tile.curveMesh.position.x = rect.x
  tile.curveMesh.position.y = rect.y
  tile.curveMesh.position.z = tile.currentCurveZ ?? LISSAJOUS_Z
  tile.curveMesh.rotation.y = tile.currentCurveRotationY ?? 0
  tile.curveMesh.scale.set(scale, scale, scale)
}

function lissajousPhase(tileIndex: number) {
  return tileIndex * 0.43
}

function lissajousSizeForRect(rect: Pick<RenderRect, 'width' | 'height'>) {
  return Math.min(rect.width, rect.height)
}
