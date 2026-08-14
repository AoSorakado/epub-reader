import { Color, DoubleSide, MeshBasicMaterial, MeshPhysicalMaterial, MeshStandardMaterial } from 'three'
import {
  PANEL_GLASS_ATTENUATION_COLOR,
  PANEL_GLASS_ATTENUATION_DISTANCE,
  PANEL_GLASS_COLOR,
  PANEL_GLASS_ENV_INTENSITY,
  PANEL_GLASS_IOR,
  PANEL_GLASS_OPACITY,
  PANEL_GLASS_ROUGHNESS,
  PANEL_GLASS_THICKNESS,
  PANEL_GLASS_TRANSMISSION,
  TILE_COLOR_INTENSITY,
  TILE_METALNESS,
  TILE_PASTEL_MIX,
  TILE_ROUGHNESS,
} from '../config'

export function createTileMaterial(color: number) {
  const pastel = new Color(color).lerp(new Color(0xffffff), TILE_PASTEL_MIX).multiplyScalar(TILE_COLOR_INTENSITY)
  return new MeshStandardMaterial({
    color: pastel,
    metalness: TILE_METALNESS,
    roughness: TILE_ROUGHNESS,
  })
}

export function createTitleMaterial() {
  return new MeshStandardMaterial({
    color: 0xffffff,
    emissive: 0xffffff,
    emissiveIntensity: 0.1,
    metalness: 0.02,
    roughness: 0.5,
  })
}

export function createLissajousMaterial() {
  return new MeshPhysicalMaterial({
    color: 0xffffff,
    attenuationColor: 0xffffff,
    attenuationDistance: 90,
    envMapIntensity: 1.05,
    ior: 1.36,
    metalness: 0,
    opacity: 0.72,
    roughness: 0.12,
    thickness: 14,
    transmission: 0.72,
    transparent: true,
  })
}

export function createPanelGlassMaterial() {
  return new MeshPhysicalMaterial({
    color: PANEL_GLASS_COLOR,
    attenuationColor: PANEL_GLASS_ATTENUATION_COLOR,
    attenuationDistance: PANEL_GLASS_ATTENUATION_DISTANCE,
    envMapIntensity: PANEL_GLASS_ENV_INTENSITY,
    ior: PANEL_GLASS_IOR,
    metalness: 0,
    opacity: PANEL_GLASS_OPACITY,
    roughness: PANEL_GLASS_ROUGHNESS,
    thickness: PANEL_GLASS_THICKNESS,
    transmission: PANEL_GLASS_TRANSMISSION,
    transparent: true,
  })
}

export function createHitProxyMaterial() {
  return new MeshBasicMaterial({
    color: 0xffffff,
    colorWrite: false,
    depthWrite: false,
    opacity: 0,
    side: DoubleSide,
    transparent: true,
  })
}
