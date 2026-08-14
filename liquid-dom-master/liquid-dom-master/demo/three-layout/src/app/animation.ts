import type { AnimationManager } from '@liquid-dom/layout/animation'
import {
  LAYOUT_SPRING,
  LISSAJOUS_ELEVATION_SPRING,
  LISSAJOUS_HOVER_SCALE,
  LISSAJOUS_HOVER_Z_LIFT,
  LISSAJOUS_HOVER_SPIN_SPEED,
  LISSAJOUS_ROTATION_RESET_SPRING,
  LISSAJOUS_Z,
  TILE_HOVER_Z_LIFT,
  TILE_LIFT_SPRING,
} from '../config'
import { layoutState } from '../layout/layoutState'
import type { AnimatedRectView, PanelView, RenderRect, TileView } from '../types'

const FULL_ROTATION = Math.PI * 2
const ROTATION_EPSILON = 0.001

export function setRectTarget(
  animationManager: AnimationManager,
  view: AnimatedRectView,
  targetRect: RenderRect,
  immediate: boolean,
  applyRect: (rect: RenderRect) => void,
) {
  view.targetRect = targetRect

  if (immediate || !view.currentRect) {
    animationManager.stop(view, ['currentRect'])
    view.currentRect = targetRect
    applyRect(targetRect)
  } else {
    animationManager.animate(view, { currentRect: targetRect }, LAYOUT_SPRING)
  }
}

export function setTileZTarget(animationManager: AnimationManager, tile: TileView, targetZ: number, immediate: boolean) {
  tile.targetZ = targetZ

  if (immediate || tile.currentZ === null) {
    animationManager.stop(tile, ['currentZ'])
    tile.currentZ = targetZ
    tile.mesh.position.z = targetZ
  } else {
    animationManager.animate(tile, { currentZ: targetZ }, TILE_LIFT_SPRING)
  }
}

export function setTileCurveTarget(animationManager: AnimationManager, tile: TileView, hovered: boolean, immediate: boolean) {
  const targetCurveZ = hovered ? LISSAJOUS_Z + LISSAJOUS_HOVER_Z_LIFT : LISSAJOUS_Z
  const targetCurveScale = hovered ? LISSAJOUS_HOVER_SCALE : 1
  tile.targetCurveZ = targetCurveZ
  tile.targetCurveScale = targetCurveScale

  if (immediate || tile.currentCurveZ === null || tile.currentCurveScale === null) {
    animationManager.stop(tile, ['currentCurveZ', 'currentCurveScale'])
    tile.currentCurveZ = targetCurveZ
    tile.currentCurveScale = targetCurveScale
    tile.curveMesh.position.z = targetCurveZ
    tile.curveMesh.scale.set(targetCurveScale, targetCurveScale, targetCurveScale)
  } else {
    animationManager.animate(
      tile,
      { currentCurveZ: targetCurveZ },
      LISSAJOUS_ELEVATION_SPRING,
    )
    animationManager.animate(
      tile,
      { currentCurveScale: targetCurveScale },
      TILE_LIFT_SPRING,
    )
  }

  setTileCurveRotationTarget(animationManager, tile, hovered, immediate)
}

export function isTileHovered(tile: TileView) {
  return layoutState.hoveredTile?.panelIndex === tile.panelIndex &&
    layoutState.hoveredTile.tileIndex === tile.tileIndex
}

export function tileTargetZ(tile: TileView) {
  return isTileHovered(tile) ? TILE_HOVER_Z_LIFT : 0
}

export function advanceTileCurveRotation(tile: TileView, deltaMilliseconds: number) {
  const currentRotation = tile.currentCurveRotationY ?? tile.curveMesh.rotation.y
  const nextRotation = wrapRotation(currentRotation + LISSAJOUS_HOVER_SPIN_SPEED * (deltaMilliseconds / 1000))
  tile.currentCurveRotationY = nextRotation
  tile.curveMesh.rotation.y = nextRotation
}

export function stopPanelAnimations(animationManager: AnimationManager, panels: PanelView[]) {
  for (const panel of panels) {
    animationManager.stop(panel.background, ['currentRect'])
    animationManager.stop(panel.title, ['currentRect'])
    for (const tile of panel.tiles) {
      animationManager.stop(tile, ['currentRect'])
      animationManager.stop(tile, ['currentZ'])
      animationManager.stop(tile, ['currentCurveZ'])
      animationManager.stop(tile, ['currentCurveScale'])
      animationManager.stop(tile, ['currentCurveRotationY'])
    }
  }
}

function setTileCurveRotationTarget(
  animationManager: AnimationManager,
  tile: TileView,
  hovered: boolean,
  immediate: boolean,
) {
  if (tile.currentCurveRotationY === null) {
    tile.currentCurveRotationY = 0
    tile.targetCurveRotationY = 0
    tile.curveMesh.rotation.y = 0
  }

  if (hovered) {
    animationManager.stop(tile, ['currentCurveRotationY'])
    tile.targetCurveRotationY = null
    return
  }

  tile.targetCurveRotationY = 0
  if (immediate) {
    animationManager.stop(tile, ['currentCurveRotationY'])
    tile.currentCurveRotationY = 0
    tile.curveMesh.rotation.y = 0
    return
  }

  const resetFromRotation = normalizeRotationForReset(tile.currentCurveRotationY)
  tile.currentCurveRotationY = resetFromRotation
  tile.curveMesh.rotation.y = resetFromRotation

  if (Math.abs(resetFromRotation) <= ROTATION_EPSILON) {
    animationManager.stop(tile, ['currentCurveRotationY'])
    tile.currentCurveRotationY = 0
    tile.curveMesh.rotation.y = 0
    return
  }

  animationManager.animate(
    tile,
    { currentCurveRotationY: 0 },
    LISSAJOUS_ROTATION_RESET_SPRING,
  )
}

function wrapRotation(rotation: number) {
  return ((rotation % FULL_ROTATION) + FULL_ROTATION) % FULL_ROTATION
}

function normalizeRotationForReset(rotation: number) {
  const wrappedRotation = wrapRotation(rotation)
  return wrappedRotation > Math.PI ? wrappedRotation - FULL_ROTATION : wrappedRotation
}
