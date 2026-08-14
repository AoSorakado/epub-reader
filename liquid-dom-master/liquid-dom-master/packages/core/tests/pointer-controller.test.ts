import { describe, expect, it, vi } from 'vitest'
import { GlassPointerEvent } from '../src/events'
import { identityMatrix } from '../src/matrix'
import { Container, Glass } from '../src/scene'
import { PointerController } from '../src/renderer/pointer-controller'

function pointerEvent(
  type: string,
  init: {
    target?: EventTarget
    relatedTarget?: EventTarget | null
    composedPath?: EventTarget[]
    pointerId?: number
    clientX?: number
    clientY?: number
    button?: number
    buttons?: number
  } = {},
) {
  const event = new Event(type, { cancelable: true }) as PointerEvent
  Object.defineProperties(event, {
    pointerId: { value: init.pointerId ?? 1 },
    pointerType: { value: 'mouse' },
    isPrimary: { value: true },
    button: { value: init.button ?? 0 },
    buttons: { value: init.buttons ?? 1 },
    clientX: { value: init.clientX ?? 10 },
    clientY: { value: init.clientY ?? 10 },
    target: { value: init.target ?? null },
    relatedTarget: { value: init.relatedTarget ?? null },
    composedPath: {
      value: () => init.composedPath ?? [init.target].filter(Boolean),
    },
  })
  return event
}

describe('PointerController', () => {
  it('ignores descendant pointerleave events while a glass is captured', () => {
    const targetCanvas = document.createElement('canvas')
    const htmlHost = document.createElement('div')
    const nextHtmlChild = document.createElement('span')
    const pointerCancel = vi.fn()
    const pointerUp = vi.fn()
    targetCanvas.append(htmlHost, nextHtmlChild)
    targetCanvas.hasPointerCapture = () => false
    targetCanvas.releasePointerCapture = () => undefined
    targetCanvas.setPointerCapture = () => undefined
    targetCanvas.getBoundingClientRect = () => ({
      x: 0,
      y: 0,
      top: 0,
      right: 200,
      bottom: 100,
      left: 0,
      width: 200,
      height: 100,
      toJSON: () => ({}),
    })

    const container = new Container()
    const glass = container.add(new Glass({
      width: 100,
      height: 80,
      pointerEvents: true,
    }))
    glass.addEventListener('pointercancel', pointerCancel as EventListener)
    glass.addEventListener('pointerup', pointerUp as EventListener)

    const controller = new PointerController({
      targetCanvas,
      renderer: { canvas: targetCanvas } as never,
      isDestroyed: () => false,
      flushSceneContentSync: () => undefined,
      getSceneHtmlHosts: () => new Set(),
      getGlassContentHosts: () => new Set([htmlHost]),
    })

    controller.syncInteractions([{ container, transform: identityMatrix() }])
    controller.handlePointerDown(pointerEvent('pointerdown', {
      target: htmlHost,
      composedPath: [htmlHost, targetCanvas],
    }))
    controller.handlePointerLeave(pointerEvent('pointerleave', {
      target: htmlHost,
      relatedTarget: nextHtmlChild,
      buttons: 1,
    }))
    controller.handlePointerUp(pointerEvent('pointerup', {
      target: targetCanvas,
      buttons: 0,
    }))

    expect(pointerCancel).not.toHaveBeenCalled()
    expect(pointerUp).toHaveBeenCalledTimes(1)
    expect(pointerUp.mock.calls[0]?.[0]).toBeInstanceOf(GlassPointerEvent)
  })
})
