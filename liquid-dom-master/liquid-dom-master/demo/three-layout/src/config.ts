import { spring } from '@liquid-dom/layout/animation'

// Grid layout
export const GRID_COLUMNS = 3
export const GRID_ROWS = 2
export const PANEL_COUNT = 3
export const HOVER_SCALE = 2
export const NON_HOVER_SCALE = 0.85
export const COLUMN_GAP = 28
export const ROW_GAP = 30
export const PANEL_STACK_GAP = 120

// Tile sizing and geometry
export const TILE_SIZE = 140
export const TILE_CORNER_RADIUS = 24
export const TILE_PANEL_CORNER_RADIUS = 60
export const TILE_DEPTH = 30
export const TILE_BEVEL_SIZE = 6
export const TILE_BEVEL_THICKNESS = 5
export const TILE_HOVER_Z_LIFT = 0

// Lissajous curves
export const LISSAJOUS_WIDTH_RATIO = 0.5
export const LISSAJOUS_HEIGHT_RATIO = 0.43
export const LISSAJOUS_TUBE_RADIUS = 4.2
export const LISSAJOUS_Z = TILE_DEPTH * 1.15
export const LISSAJOUS_HOVER_Z_LIFT = 96
export const LISSAJOUS_HOVER_SCALE = 1.28
export const LISSAJOUS_HOVER_SPIN_SPEED = 0.55

// Panel geometry
export const BACKGROUND_PADDING = 35
export const BACKGROUND_CORNER_RADIUS = TILE_PANEL_CORNER_RADIUS + BACKGROUND_PADDING
export const HIT_PROXY_Z = TILE_DEPTH * 0.65
export const ROOT_PANEL_Z = -TILE_DEPTH - 34

// Stage interaction
export const STAGE_ROTATION_X = -0.34
export const STAGE_ROTATION_Y = 0.18
export const POINTER_ROTATION_X = 0.08
export const POINTER_ROTATION_Y = 0.12

// Title text
export const TITLE_Z = 10
export const TITLE_SIZE = 42
export const TITLE_DEPTH = 3
export const TITLE_PANEL_GAP = 36

// Camera
export const CAMERA_FOV = 32
export const CAMERA_DISTANCE = 1400
export const CAMERA_NEAR = 10
export const CAMERA_FAR = 8000
export const CAMERA_FIT_MARGIN = 1.1
export const CAMERA_VERTICAL_OFFSET = -90

// Animation
export const LAYOUT_SPRING = spring({ stiffness: 300, damping: 20 })
export const TILE_LIFT_SPRING = spring({ stiffness: 500, damping: 15 })
export const LISSAJOUS_ELEVATION_SPRING = spring({ stiffness: 500, damping: 48 })
export const LISSAJOUS_ROTATION_RESET_SPRING = spring({ stiffness: 240, damping: 30 })

// Environment map
export const ENVIRONMENT_MAP_URL =
  'https://raw.githubusercontent.com/pmndrs/drei-assets/456060a26bbeb8fdf79326f224b6d99b8bcce736/hdri/forest_slope_1k.hdr'
export const ENVIRONMENT_BACKGROUND_BLUR = 0.2
export const ENVIRONMENT_BACKGROUND_INTENSITY = 0.1
export const ENVIRONMENT_LIGHTING_INTENSITY = 0.72

// Panel glass material
export const PANEL_GLASS_COLOR = 0xffffff
export const PANEL_GLASS_ATTENUATION_COLOR = 0xf4fff8
export const PANEL_GLASS_ATTENUATION_DISTANCE = 180
export const PANEL_GLASS_ENV_INTENSITY = 0.85
export const PANEL_GLASS_IOR = 1.35
export const PANEL_GLASS_OPACITY = 0.4
export const PANEL_GLASS_ROUGHNESS = 0.2
export const PANEL_GLASS_THICKNESS = 42
export const PANEL_GLASS_TRANSMISSION = 0.68

// Tile material
export const TILE_PASTEL_MIX = 0
export const TILE_COLOR_INTENSITY = 0.82
export const TILE_METALNESS = 0.08
export const TILE_ROUGHNESS = 0.38

// Tile palette
export const COLOR_PALETTE = [
  0xe85d75,
  0x25a18e,
  0x725ac1,
  0xf3a712,
  0x2f80ed,
  0x7cb342,
]
