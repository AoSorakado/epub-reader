import { Mesh } from 'three'
import type { Font } from 'three/examples/jsm/loaders/FontLoader.js'
import { TITLE_Z } from '../config'
import { MeasuredLeaf } from '../layout/MeasuredLeaf'
import { createTitleGeometry } from '../three/geometry'
import { createTitleMaterial } from '../three/materials'
import type { RenderRect, TitleView } from '../types'

export function createTitleView(panelIndex: number, font: Font): TitleView {
  const titleGeometry = createTitleGeometry(`Section ${panelIndex + 1}`, font)
  const title: TitleView = {
    node: new MeasuredLeaf(
      () => titleGeometry.size,
      {
        measureKey: `${titleGeometry.size.width}:${titleGeometry.size.height}`,
      },
    ),
    mesh: new Mesh(titleGeometry.geometry.clone(), createTitleMaterial()),
    currentRect: null,
    targetRect: null,
  }
  title.mesh.position.z = TITLE_Z
  return title
}

export function applyTitleRect(title: TitleView, rect: RenderRect) {
  title.mesh.position.x = rect.x
  title.mesh.position.y = rect.y
}
