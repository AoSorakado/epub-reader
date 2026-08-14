import {
  AmbientLight,
  DirectionalLight,
  Group,
  PerspectiveCamera,
  PointLight,
  Scene,
  SRGBColorSpace,
  WebGLRenderer,
} from 'three'
import {
  CAMERA_DISTANCE,
  CAMERA_FAR,
  CAMERA_FOV,
  CAMERA_NEAR,
  ENVIRONMENT_BACKGROUND_BLUR,
  ENVIRONMENT_BACKGROUND_INTENSITY,
  ENVIRONMENT_LIGHTING_INTENSITY,
  STAGE_ROTATION_X,
  STAGE_ROTATION_Y,
} from '../config'
import type { EnvironmentMap } from '../types'

export function createRenderer(canvas: HTMLCanvasElement) {
  const renderer = new WebGLRenderer({
    canvas,
    antialias: true,
    alpha: false,
  })
  renderer.outputColorSpace = SRGBColorSpace
  return renderer
}

export function createScene(environmentMap: EnvironmentMap) {
  const scene = new Scene()
  scene.background = environmentMap.background
  scene.backgroundBlurriness = ENVIRONMENT_BACKGROUND_BLUR
  scene.backgroundIntensity = ENVIRONMENT_BACKGROUND_INTENSITY
  scene.environment = environmentMap.renderTarget.texture
  scene.environmentIntensity = ENVIRONMENT_LIGHTING_INTENSITY
  return scene
}

export function createCamera() {
  const camera = new PerspectiveCamera(CAMERA_FOV, 1, CAMERA_NEAR, CAMERA_FAR)
  camera.position.set(0, 0, CAMERA_DISTANCE)
  camera.lookAt(0, 0, 0)
  return camera
}

export function createStage(scene: Scene) {
  const stage = new Group()
  stage.rotation.x = STAGE_ROTATION_X
  stage.rotation.y = STAGE_ROTATION_Y
  scene.add(stage)
  return stage
}

export function addLights(scene: Scene) {
  scene.add(new AmbientLight(0xffffff, 1.8))

  const keyLight = new DirectionalLight(0xffffff, 3.2)
  keyLight.position.set(-260, -320, 560)
  scene.add(keyLight)

  const coralLight = new PointLight(0xff7a59, 44000, 1200)
  coralLight.position.set(-320, 210, 360)
  scene.add(coralLight)

  const tealLight = new PointLight(0x2dd4bf, 32000, 1000)
  tealLight.position.set(360, -180, 300)
  scene.add(tealLight)
}
