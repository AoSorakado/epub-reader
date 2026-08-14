import { Vector3 } from 'three'
import type { Group, PerspectiveCamera } from 'three'
import { CAMERA_DISTANCE, CAMERA_FIT_MARGIN, CAMERA_VERTICAL_OFFSET } from '../config'
import type { RenderRect } from '../types'

const cameraLookTarget = new Vector3()
const cameraWorldTarget = new Vector3()

export function frameCameraToRect(
  camera: PerspectiveCamera,
  stage: Group,
  rect: RenderRect,
  viewportWidth: number,
  viewportHeight: number,
) {
  stage.updateWorldMatrix(true, false)
  cameraLookTarget.set(rect.x, rect.y + CAMERA_VERTICAL_OFFSET, 0)
  cameraWorldTarget.copy(cameraLookTarget)
  stage.localToWorld(cameraWorldTarget)
  camera.position.set(
    cameraWorldTarget.x,
    cameraWorldTarget.y,
    cameraWorldTarget.z + distanceToFitRect(camera, rect, viewportWidth, viewportHeight),
  )
  camera.lookAt(cameraWorldTarget)
}

export function resetCamera(camera: PerspectiveCamera) {
  camera.position.set(0, 0, CAMERA_DISTANCE)
  camera.lookAt(0, 0, 0)
}

function distanceToFitRect(camera: PerspectiveCamera, rect: RenderRect, viewportWidth: number, viewportHeight: number) {
  const verticalFov = (camera.fov * Math.PI) / 180
  const horizontalFov = 2 * Math.atan(Math.tan(verticalFov * 0.5) * (viewportWidth / Math.max(viewportHeight, 1)))
  const distanceForHeight = rect.height / (2 * Math.tan(verticalFov * 0.5))
  const distanceForWidth = rect.width / (2 * Math.tan(horizontalFov * 0.5))
  return Math.max(distanceForHeight, distanceForWidth, CAMERA_DISTANCE) * CAMERA_FIT_MARGIN
}
