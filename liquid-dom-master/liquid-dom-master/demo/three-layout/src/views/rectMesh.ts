import { TILE_CORNER_RADIUS } from '../config'
import { createTileGeometry } from '../three/geometry'
import type { CornerRadiiInput, RectMeshView, RenderRect } from '../types'

export function applyRectMesh(
  view: RectMeshView,
  rect: RenderRect,
  cornerRadii: CornerRadiiInput = TILE_CORNER_RADIUS,
) {
  if (Math.abs(view.geometryWidth - rect.width) > 0.1 || Math.abs(view.geometryHeight - rect.height) > 0.1) {
    view.mesh.geometry.dispose()
    view.mesh.geometry = createTileGeometry(rect.width, rect.height, cornerRadii)
    view.geometryWidth = rect.width
    view.geometryHeight = rect.height
  }

  view.mesh.position.x = rect.x
  view.mesh.position.y = rect.y
  view.mesh.scale.set(1, 1, 1)
}
