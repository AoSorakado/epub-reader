import { LayoutEngine } from '@liquid-dom/layout'
import { AnimationManager } from '@liquid-dom/layout/animation'
import { PlaneGeometry, Raycaster, Vector2 } from 'three'
import type { Group, MeshBasicMaterial, PerspectiveCamera, Scene, WebGLRenderer } from 'three'
import type { Font } from 'three/examples/jsm/loaders/FontLoader.js'
import {
  BACKGROUND_CORNER_RADIUS,
  PANEL_COUNT,
  POINTER_ROTATION_X,
  POINTER_ROTATION_Y,
  STAGE_ROTATION_X,
  STAGE_ROTATION_Y,
} from '../config'
import { loadEnvironmentMap, loadTitleFont } from '../three/assets'
import { frameCameraToRect, resetCamera } from '../three/camera'
import { createHitProxyMaterial } from '../three/materials'
import { addLights, createCamera, createRenderer, createScene, createStage } from '../three/scene'
import { renderRectForLayoutRect } from '../layout/rects'
import {
  gridProps,
  layoutState,
  sameHoverTarget,
  tileMeasureKey,
} from '../layout/layoutState'
import { addPanelToStage, createPanel, createRootStack, disposePanel } from '../views/panel'
import { applyRectMesh } from '../views/rectMesh'
import { applyTileRect } from '../views/tile'
import { applyTitleRect } from '../views/title'
import {
  advanceTileCurveRotation,
  isTileHovered,
  setRectTarget,
  setTileCurveTarget,
  setTileZTarget,
  stopPanelAnimations,
  tileTargetZ,
} from './animation'
import type { DomRefs, EnvironmentMap, HoverTarget, PanelView, TileView } from '../types'
import type { LayoutNode } from '@liquid-dom/layout'

export class ThreeLayoutDemo {
  private readonly canvas: HTMLCanvasElement
  private readonly renderer: WebGLRenderer
  private readonly scene: Scene
  private readonly camera: PerspectiveCamera
  private readonly stage: Group
  private readonly environmentMap: EnvironmentMap
  private readonly hitProxyGeometry: PlaneGeometry
  private readonly hitProxyMaterial: MeshBasicMaterial
  private readonly panels: PanelView[]
  private readonly tiles: TileView[]
  private readonly rootStack: LayoutNode
  private readonly layoutEngine: LayoutEngine
  private readonly animationManager = new AnimationManager()
  private readonly raycaster = new Raycaster()
  private readonly pointer = new Vector2()
  private readonly eventCleanups: (() => void)[] = []
  private lastWidth = 0
  private lastHeight = 0
  private lastDpr = 0
  private animationFrameId: number | null = null
  private lastAnimationTime = 0
  private frameDeltaMilliseconds = 0
  private started = false
  private disposed = false

  static async create(options: DomRefs) {
    const renderer = createRenderer(options.canvas)
    const titleFont = await loadTitleFont()
    const environmentMap = await loadEnvironmentMap(renderer)
    return new ThreeLayoutDemo(options, renderer, environmentMap, titleFont)
  }

  private constructor(
    options: DomRefs,
    renderer: WebGLRenderer,
    environmentMap: EnvironmentMap,
    titleFont: Font,
  ) {
    this.canvas = options.canvas
    this.renderer = renderer
    this.environmentMap = environmentMap
    this.hitProxyGeometry = new PlaneGeometry(1, 1)
    this.hitProxyMaterial = createHitProxyMaterial()
    this.panels = Array.from({ length: PANEL_COUNT }, (_, index) =>
      createPanel(index, titleFont, this.hitProxyGeometry, this.hitProxyMaterial),
    )
    this.tiles = this.panels.flatMap((panel) => panel.tiles)
    this.rootStack = createRootStack(this.panels)
    this.layoutEngine = new LayoutEngine({
      root: this.rootStack,
    })
    this.scene = createScene(environmentMap)
    this.camera = createCamera()
    this.stage = createStage(this.scene)

    for (const panel of this.panels) {
      addPanelToStage(this.stage, panel)
    }
    addLights(this.scene)
  }

  start() {
    if (this.started) return
    this.started = true
    this.installEventListeners()
    this.layoutAndRender({ immediate: true })
  }

  dispose() {
    if (this.disposed) return
    this.disposed = true
    for (const cleanup of this.eventCleanups.splice(0)) cleanup()
    this.finishAnimation()
    this.layoutEngine.dispose()
    for (const panel of this.panels) {
      disposePanel(panel)
    }
    this.hitProxyGeometry.dispose()
    this.hitProxyMaterial.dispose()
    this.environmentMap.background.dispose()
    this.environmentMap.renderTarget.dispose()
    this.renderer.dispose()
  }

  private installEventListeners() {
    this.canvas.addEventListener('pointermove', this.handlePointerMove)
    this.eventCleanups.push(() => this.canvas.removeEventListener('pointermove', this.handlePointerMove))

    this.canvas.addEventListener('pointerleave', this.handlePointerLeave)
    this.eventCleanups.push(() => this.canvas.removeEventListener('pointerleave', this.handlePointerLeave))

    window.addEventListener('resize', this.handleResize)
    this.eventCleanups.push(() => window.removeEventListener('resize', this.handleResize))

    window.addEventListener('pagehide', this.handlePageHide)
    this.eventCleanups.push(() => window.removeEventListener('pagehide', this.handlePageHide))
  }

  private measureLayout() {
    for (const panel of this.panels) {
      panel.grid.props = gridProps()
    }

    for (const tile of this.tiles) {
      tile.node.measureKey = tileMeasureKey(tile.panelIndex, tile.tileIndex)
    }

    this.layoutEngine.layout({})
  }

  private updateLayoutTargets(width: number, height: number, immediate: boolean) {
    const rootRect = this.rootStack.layout?.absoluteRect
    if (!rootRect) return

    for (const panel of this.panels) {
      const titleRect = panel.title.node.layout?.absoluteRect
      if (titleRect) {
        setRectTarget(
          this.animationManager,
          panel.title,
          renderRectForLayoutRect(titleRect, rootRect),
          immediate,
          (rect) => applyTitleRect(panel.title, rect),
        )
      }

      const panelRect = panel.gridPadding.layout?.absoluteRect
      if (panelRect) {
        setRectTarget(
          this.animationManager,
          panel.background,
          renderRectForLayoutRect(panelRect, rootRect),
          immediate,
          (rect) => applyRectMesh(panel.background, rect, BACKGROUND_CORNER_RADIUS),
        )
      }

      for (const tile of panel.tiles) {
        const rect = tile.node.layout?.absoluteRect
        if (!rect) continue

        setRectTarget(
          this.animationManager,
          tile,
          renderRectForLayoutRect(rect, rootRect),
          immediate,
          (nextRect) => applyTileRect(tile, nextRect),
        )
        setTileZTarget(this.animationManager, tile, tileTargetZ(tile), immediate)
        setTileCurveTarget(this.animationManager, tile, isTileHovered(tile), immediate)
      }
    }

    if (immediate || layoutState.hoveredTile === null) {
      this.frameCameraToRootStack(width, height)
    }
  }

  private syncViewport() {
    const width = Math.max(1, this.canvas.clientWidth)
    const height = Math.max(1, this.canvas.clientHeight)
    const dpr = Math.min(window.devicePixelRatio || 1, 2)

    if (width !== this.lastWidth || height !== this.lastHeight || dpr !== this.lastDpr) {
      this.renderer.setPixelRatio(dpr)
      this.renderer.setSize(width, height, false)
      this.camera.aspect = width / Math.max(height, 1)
      this.camera.updateProjectionMatrix()

      this.lastWidth = width
      this.lastHeight = height
      this.lastDpr = dpr
    }

    return { width, height }
  }

  private frameCameraToRootStack(width: number, height: number) {
    const stackRect = this.rootStack.layout?.absoluteRect
    if (!stackRect) {
      resetCamera(this.camera)
      return
    }

    frameCameraToRect(
      this.camera,
      this.stage,
      renderRectForLayoutRect(stackRect, stackRect),
      width,
      height,
    )
  }

  private finishAnimation() {
    if (this.animationFrameId !== null) {
      cancelAnimationFrame(this.animationFrameId)
      this.animationFrameId = null
    }
    stopPanelAnimations(this.animationManager, this.panels)
  }

  private renderCurrentLayout() {
    for (const panel of this.panels) {
      if (panel.title.currentRect) {
        applyTitleRect(panel.title, panel.title.currentRect)
      }

      if (panel.background.currentRect) {
        applyRectMesh(panel.background, panel.background.currentRect, BACKGROUND_CORNER_RADIUS)
      }

      for (const tile of panel.tiles) {
        if (!tile.currentRect) continue
        applyTileRect(tile, tile.currentRect)
        if (isTileHovered(tile)) {
          advanceTileCurveRotation(tile, this.frameDeltaMilliseconds)
        }
      }
    }

    this.renderer.render(this.scene, this.camera)
  }

  private startLayoutAnimation() {
    if (this.animationFrameId === null) {
      this.lastAnimationTime = 0
      this.animationFrameId = requestAnimationFrame(this.animateLayout)
    }
  }

  private layoutAndRender({ immediate = false }: { immediate?: boolean } = {}) {
    const { width, height } = this.syncViewport()
    this.measureLayout()
    this.updateLayoutTargets(width, height, immediate)

    if (immediate) {
      this.finishAnimation()
      this.renderCurrentLayout()
    } else {
      this.startLayoutAnimation()
    }
  }

  private setHoveredTile(target: HoverTarget | null) {
    if (sameHoverTarget(layoutState.hoveredTile, target)) return false

    layoutState.hoveredTile = target
    this.canvas.style.cursor = 'default'
    this.layoutAndRender()
    return true
  }

  private updateStageRotationFromPointer() {
    this.stage.rotation.x = STAGE_ROTATION_X + this.pointer.y * POINTER_ROTATION_X
    this.stage.rotation.y = STAGE_ROTATION_Y + this.pointer.x * POINTER_ROTATION_Y
  }

  private readonly animateLayout = (now: number) => {
    const delta = this.lastAnimationTime === 0 ? 16.7 : now - this.lastAnimationTime
    this.lastAnimationTime = now
    this.frameDeltaMilliseconds = delta
    this.animationManager.tick(delta)
    this.renderCurrentLayout()
    this.frameDeltaMilliseconds = 0

    if (this.animationManager.active || layoutState.hoveredTile !== null) {
      this.animationFrameId = requestAnimationFrame(this.animateLayout)
      return
    }

    this.animationFrameId = null
    this.lastAnimationTime = 0
  }

  private readonly handlePointerMove = (event: PointerEvent) => {
    const bounds = this.canvas.getBoundingClientRect()
    this.pointer.x = ((event.clientX - bounds.left) / Math.max(bounds.width, 1)) * 2 - 1
    this.pointer.y = -(((event.clientY - bounds.top) / Math.max(bounds.height, 1)) * 2 - 1)
    this.updateStageRotationFromPointer()
    this.raycaster.setFromCamera(this.pointer, this.camera)

    const hit = this.raycaster.intersectObjects(this.tiles.map((tile) => tile.hitMesh), false)[0]
    const tile = hit ? this.tiles.find((candidate) => candidate.hitMesh === hit.object) : undefined
    const didChangeHover = this.setHoveredTile(tile ? { panelIndex: tile.panelIndex, tileIndex: tile.tileIndex } : null)
    if (!didChangeHover && this.animationFrameId === null) {
      this.renderCurrentLayout()
    }
  }

  private readonly handlePointerLeave = () => {
    this.pointer.set(0, 0)
    this.updateStageRotationFromPointer()
    const didChangeHover = this.setHoveredTile(null)
    if (!didChangeHover && this.animationFrameId === null) {
      this.renderCurrentLayout()
    }
  }

  private readonly handleResize = () => {
    this.layoutAndRender({ immediate: true })
  }

  private readonly handlePageHide = () => {
    this.dispose()
  }
}
