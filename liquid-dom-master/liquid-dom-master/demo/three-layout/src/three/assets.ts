import { EquirectangularReflectionMapping, PMREMGenerator, WebGLRenderer } from 'three'
import { FontLoader } from 'three/examples/jsm/loaders/FontLoader.js'
import { HDRLoader } from 'three/examples/jsm/loaders/HDRLoader.js'
import titleFontUrl from 'three/examples/fonts/droid/droid_sans_regular.typeface.json?url'
import { ENVIRONMENT_MAP_URL } from '../config'
import type { EnvironmentMap } from '../types'

export async function loadTitleFont() {
  const response = await fetch(titleFontUrl)
  if (!response.ok) {
    throw new Error(`Unable to load title font: ${response.status}`)
  }

  return new FontLoader().parse(await response.json())
}

export async function loadEnvironmentMap(renderer: WebGLRenderer): Promise<EnvironmentMap> {
  const background = await new HDRLoader().loadAsync(ENVIRONMENT_MAP_URL)
  background.mapping = EquirectangularReflectionMapping

  const pmremGenerator = new PMREMGenerator(renderer)
  pmremGenerator.compileEquirectangularShader()
  const renderTarget = pmremGenerator.fromEquirectangular(background)
  pmremGenerator.dispose()

  return { background, renderTarget }
}
