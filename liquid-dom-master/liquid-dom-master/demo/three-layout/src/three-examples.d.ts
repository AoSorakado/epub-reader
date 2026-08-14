declare module 'three/examples/jsm/loaders/FontLoader.js' {
  export class Font {
    data: unknown
  }

  export class FontLoader {
    parse(json: unknown): Font
  }
}

declare module 'three/examples/jsm/geometries/TextGeometry.js' {
  import { ExtrudeGeometry } from 'three'
  import type { Font } from 'three/examples/jsm/loaders/FontLoader.js'

  export type TextGeometryParameters = {
    font?: Font
    size?: number
    depth?: number
    curveSegments?: number
    bevelEnabled?: boolean
    bevelThickness?: number
    bevelSize?: number
    bevelSegments?: number
  }

  export class TextGeometry extends ExtrudeGeometry {
    constructor(text: string, parameters?: TextGeometryParameters)
  }
}

declare module 'three/examples/jsm/loaders/HDRLoader.js' {
  import type { DataTexture } from 'three'

  export class HDRLoader {
    loadAsync(url: string): Promise<DataTexture>
  }
}
