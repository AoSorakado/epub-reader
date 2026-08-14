import type { Alignment, Insets, InsetsInput, Length, ProposedSize, Rect, Size, StackAlignment } from './types'

export const ZERO_SIZE: Size = { width: 0, height: 0 }

export function isFiniteLength(value: number | undefined): value is number {
  return typeof value === 'number' && Number.isFinite(value)
}

export function normalizeLength(value: Length | undefined): number | undefined {
  if (value === undefined) return undefined
  return value === 'infinity' ? Number.POSITIVE_INFINITY : value
}

export function sanitizeSize(size: Size): Size {
  return {
    width: sanitizeNumber(size.width),
    height: sanitizeNumber(size.height),
  }
}

export function sanitizeRect(rect: Rect): Rect {
  return {
    x: sanitizeNumber(rect.x),
    y: sanitizeNumber(rect.y),
    width: Math.max(0, sanitizeNumber(rect.width)),
    height: Math.max(0, sanitizeNumber(rect.height)),
  }
}

export function sanitizeProposal(proposal: ProposedSize): ProposedSize {
  const next: ProposedSize = {}
  if (isFiniteLength(proposal.width)) next.width = Math.max(0, proposal.width)
  if (isFiniteLength(proposal.height)) next.height = Math.max(0, proposal.height)
  return next
}

export function proposalKey(proposal: ProposedSize): string {
  return `${proposal.width ?? 'u'}x${proposal.height ?? 'u'}`
}

export function sizeToProposal(size: Size): ProposedSize {
  return { width: size.width, height: size.height }
}

export function clamp(value: number, min?: number, max?: number): number {
  let next = value
  if (min !== undefined) next = Math.max(min, next)
  if (max !== undefined && Number.isFinite(max)) next = Math.min(max, next)
  return sanitizeNumber(next)
}

export function clampSize(
  size: Size,
  minWidth?: number,
  minHeight?: number,
  maxWidth?: number,
  maxHeight?: number,
): Size {
  return {
    width: clamp(size.width, minWidth, maxWidth),
    height: clamp(size.height, minHeight, maxHeight),
  }
}

export function normalizeInsets(input: InsetsInput | undefined): Insets {
  if (input === undefined) {
    return { top: 0, right: 0, bottom: 0, left: 0 }
  }

  if (typeof input === 'number') {
    return { top: input, right: input, bottom: input, left: input }
  }

  const record = input as Partial<Insets> & { horizontal?: number; vertical?: number }
  const horizontal = record.horizontal
  const vertical = record.vertical

  return {
    top: record.top ?? vertical ?? 0,
    right: record.right ?? horizontal ?? 0,
    bottom: record.bottom ?? vertical ?? 0,
    left: record.left ?? horizontal ?? 0,
  }
}

export function insetRect(rect: Rect, insets: Insets): Rect {
  return sanitizeRect({
    x: rect.x + insets.left,
    y: rect.y + insets.top,
    width: rect.width - insets.left - insets.right,
    height: rect.height - insets.top - insets.bottom,
  })
}

export function subtractInsets(proposal: ProposedSize, insets: Insets): ProposedSize {
  const next: ProposedSize = {}
  if (proposal.width !== undefined) next.width = Math.max(0, proposal.width - insets.left - insets.right)
  if (proposal.height !== undefined) next.height = Math.max(0, proposal.height - insets.top - insets.bottom)
  return next
}

export function addInsets(size: Size, insets: Insets): Size {
  return {
    width: size.width + insets.left + insets.right,
    height: size.height + insets.top + insets.bottom,
  }
}

export function alignRect(size: Size, bounds: Rect, alignment: Alignment | undefined): Rect {
  const resolved = resolveAlignment(alignment)
  return sanitizeRect({
    x: bounds.x + alignOffset(bounds.width, size.width, resolved.x),
    y: bounds.y + alignOffset(bounds.height, size.height, resolved.y),
    width: size.width,
    height: size.height,
  })
}

export function crossAxisOffset(
  available: number,
  child: number,
  alignment: StackAlignment | undefined,
): number {
  switch (alignment) {
    case 'center':
      return (available - child) / 2
    case 'end':
    case 'trailing':
    case 'bottom':
      return available - child
    case 'start':
    case 'leading':
    case 'top':
    case undefined:
      return 0
  }
}

export function stableSerialize(value: unknown): string {
  if (value === undefined) return 'undefined'
  if (value === null) return 'null'
  if (typeof value === 'number' || typeof value === 'boolean' || typeof value === 'string') {
    return JSON.stringify(value)
  }
  if (Array.isArray(value)) {
    return `[${value.map(stableSerialize).join(',')}]`
  }
  if (typeof value === 'object') {
    const record = value as Record<string, unknown>
    return `{${Object.keys(record)
      .sort()
      .map((key) => `${JSON.stringify(key)}:${stableSerialize(record[key])}`)
      .join(',')}}`
  }
  if (typeof value === 'function') return `[function:${value.name || 'anonymous'}]`
  return String(value)
}

export function escapeIdentityPart(value: unknown): string {
  return String(value).replaceAll('%', '%25').replaceAll('/', '%2F')
}

function resolveAlignment(alignment: Alignment | undefined): { x: 'start' | 'center' | 'end'; y: 'start' | 'center' | 'end' } {
  if (alignment && typeof alignment === 'object') {
    return { x: alignment.x ?? 'center', y: alignment.y ?? 'center' }
  }

  switch (alignment) {
    case 'top':
      return { x: 'center', y: 'start' }
    case 'bottom':
      return { x: 'center', y: 'end' }
    case 'leading':
      return { x: 'start', y: 'center' }
    case 'trailing':
      return { x: 'end', y: 'center' }
    case 'topLeading':
      return { x: 'start', y: 'start' }
    case 'topTrailing':
      return { x: 'end', y: 'start' }
    case 'bottomLeading':
      return { x: 'start', y: 'end' }
    case 'bottomTrailing':
      return { x: 'end', y: 'end' }
    case 'center':
    case undefined:
      return { x: 'center', y: 'center' }
  }
}

function alignOffset(available: number, child: number, alignment: 'start' | 'center' | 'end') {
  switch (alignment) {
    case 'start':
      return 0
    case 'center':
      return (available - child) / 2
    case 'end':
      return available - child
  }
}

function sanitizeNumber(value: number): number {
  return Number.isFinite(value) ? value : 0
}
