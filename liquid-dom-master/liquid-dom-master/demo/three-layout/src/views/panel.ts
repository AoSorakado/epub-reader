import { Mesh } from 'three'
import type { Font } from 'three/examples/jsm/loaders/FontLoader.js'
import type { Group, MeshBasicMaterial, PlaneGeometry } from 'three'
import { Padding, VStack } from '@liquid-dom/layout'
import {
  BACKGROUND_CORNER_RADIUS,
  BACKGROUND_PADDING,
  COLOR_PALETTE,
  PANEL_STACK_GAP,
  ROOT_PANEL_Z,
  TITLE_PANEL_GAP,
} from '../config'
import { Grid2x3 } from '../layout/Grid2x3'
import { gridProps } from '../layout/layoutState'
import { createTileGeometry } from '../three/geometry'
import { createPanelGlassMaterial } from '../three/materials'
import type { PanelView, RectMeshView, TileView } from '../types'
import { createTileView } from './tile'
import { createTitleView } from './title'

export function createPanel(
  panelIndex: number,
  font: Font,
  hitProxyGeometry: PlaneGeometry,
  hitProxyMaterial: MeshBasicMaterial,
): PanelView {
  const title = createTitleView(panelIndex, font)
  const tiles: TileView[] = COLOR_PALETTE.map((color, tileIndex) =>
    createTileView(panelIndex, tileIndex, color, hitProxyGeometry, hitProxyMaterial),
  )
  const grid = createGrid(tiles)
  const gridPadding = new Padding(BACKGROUND_PADDING).append(grid)

  return {
    node: new VStack({ alignment: 'center', spacing: TITLE_PANEL_GAP }).append(title.node, gridPadding),
    grid,
    gridPadding,
    title,
    background: createPanelBackground(),
    tiles,
  }
}

export function createRootStack(panels: PanelView[]) {
  return new VStack({ alignment: 'center', spacing: PANEL_STACK_GAP }).append(panels.map((panel) => panel.node))
}

export function addPanelToStage(stage: Group, panel: PanelView) {
  stage.add(panel.background.mesh)
  stage.add(panel.title.mesh)
  for (const tile of panel.tiles) {
    stage.add(tile.mesh)
    stage.add(tile.curveMesh)
    stage.add(tile.hitMesh)
  }
}

export function disposePanel(panel: PanelView) {
  panel.title.mesh.geometry.dispose()
  panel.title.mesh.material.dispose()
  panel.background.mesh.geometry.dispose()
  panel.background.mesh.material.dispose()
  for (const tile of panel.tiles) {
    tile.mesh.geometry.dispose()
    tile.mesh.material.dispose()
    tile.curveMesh.geometry.dispose()
    tile.curveMesh.material.dispose()
  }
}

function createGrid(tiles: TileView[]) {
  return new Grid2x3(gridProps()).append(tiles.map((tile) => tile.node))
}

function createPanelBackground(): RectMeshView {
  const background: RectMeshView = {
    mesh: new Mesh(createTileGeometry(1, 1, BACKGROUND_CORNER_RADIUS), createPanelGlassMaterial()),
    geometryWidth: 1,
    geometryHeight: 1,
    currentRect: null,
    targetRect: null,
  }
  background.mesh.position.z = ROOT_PANEL_Z
  return background
}
